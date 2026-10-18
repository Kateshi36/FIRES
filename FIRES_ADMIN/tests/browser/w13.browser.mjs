// W13.21: the real website in a real (headless) browser against the fake Firestore in fake-firebase.js.
//   npm i --no-save playwright      (once; Chromium must be installed: npx playwright install chromium)
//   node tests/browser/w13.browser.mjs
// Nothing leaves the machine: the Firebase files are answered by the fake, fonts and Leaflet by empty files.
import http from "node:http";
import { readFileSync, existsSync } from "node:fs";
import { extname, join, normalize } from "node:path";
import { fileURLToPath } from "node:url";
import { createRequire } from "node:module";
import assert from "node:assert/strict";

const ROOT = fileURLToPath(new URL("../../", import.meta.url));
const FAKE = readFileSync(new URL("./fake-firebase.js", import.meta.url), "utf8");
const require = createRequire(import.meta.url);
let pw;
try { pw = require("playwright"); } catch { pw = require("/opt/npm-tools/node_modules/playwright"); }

const TYPES = { ".html": "text/html", ".js": "text/javascript", ".css": "text/css" };
const server = http.createServer((req, res) => {
  const p = normalize(join(ROOT, decodeURIComponent(req.url.split("?")[0])));
  if (!p.startsWith(ROOT) || !existsSync(p)) { res.writeHead(404); res.end(); return; }
  res.writeHead(200, { "content-type": TYPES[extname(p)] || "application/octet-stream" });
  res.end(readFileSync(p));
});
await new Promise(r => server.listen(0, "127.0.0.1", r));
const BASE = `http://127.0.0.1:${server.address().port}`;

const T0 = Date.UTC(2026, 9, 1, 2, 0);          // all dates are fixed, so nothing depends on today
const ts = n => ({ __ts: T0 + n * 60000 });
const inc = (extra = {}) => ({ status: "reported", verification: "pending", severity: "high", addressText: "12 Rizal St.",
  description: "Smoke", reporterName: "Citizen", submittedAt: ts(0), updatedAt: ts(0), ...extra });
const user = (name, role, address = "BFP") => ({ fullName: name, email: name.split(/\W+/)[0].toLowerCase() + "@x.test", contactNo: "0917", address, role });
const SEED = () => ({
  "users/admin1": user("Admin One", "admin", "BDRRMO"),
  "users/u1": user("Ana Cruz", "responder", "BFP"),
  "users/u2": user("O'Brien & Sons", "responder", "BDRRMO"),
  "users/c1": user("Citizen Cee", "citizen", "Purok 1"),
  "incidents/rep00001": inc({ submittedAt: ts(10) }),                          // reported
  "incidents/rep00002": inc({ submittedAt: ts(9) }),
  "incidents/rep00003": inc({ submittedAt: ts(8) }),
  "incidents/rep00004": inc({ submittedAt: ts(7) }),
  "incidents/ver00001": inc({ status: "verified", verification: "verified", submittedAt: ts(6) }),
  "incidents/dis00001": inc({ status: "dismissed", verification: "false", submittedAt: ts(5) }),
  "incidents/res00001": inc({ status: "resolved", verification: "verified", submittedAt: ts(4) }),
});

const browser = await pw.chromium.launch();
let failed = 0;
async function fresh() {
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await ctx.newPage();
  const errors = [];
  page.on("pageerror", e => errors.push(String(e)));
  await page.addInitScript(seed => { window.__seed = seed; }, SEED());
  await page.route("https://www.gstatic.com/firebasejs/**", r => r.fulfill({ contentType: "text/javascript", body: FAKE }));
  await page.route(/fonts\.googleapis|cdnjs\.cloudflare/, r => r.fulfill({ contentType: "text/plain", body: "" }));
  await page.goto(BASE + "/index.html");
  await page.waitForSelector("#main h1");
  return { page, errors, ctx };
}
const calls = page => page.evaluate(() => window.__fake.calls);
const openFromRecords = async (page, id) => {
  await page.click('aside button[data-t="rec"]');
  await page.waitForSelector("tbody tr[data-id]");
  await page.click(`tbody tr[data-id="${id}"]`);
  await page.waitForSelector("#drawer.open");
  await page.waitForFunction(() => !document.querySelector("#drawer").textContent.includes("Loading..."));
};
const toastText = page => page.locator("#toast").textContent();

async function test(name, fn) {
  const { page, errors, ctx } = await fresh();
  try { await fn(page); assert.deepEqual(errors, [], "page errors"); console.log("ok   " + name); }
  catch (e) { failed++; console.log("FAIL " + name + "\n     " + String(e.message).split("\n").join("\n     ")); }
  await ctx.close();
}

await test("buttons: Verify / False report / Duplicate only on a reported incident", async page => {
  for (const [id, want] of [["rep00001", 3], ["ver00001", 0], ["dis00001", 0], ["res00001", 0]]) {
    await openFromRecords(page, id);
    assert.equal(await page.locator("[data-rv]").count(), want, id);
    if (want) assert.deepEqual(await page.locator("[data-rv]").allTextContents(), ["Verify", "False report", "Duplicate"]);
    await page.click("#cx");
  }
});

await test("Assign responder only on open reports, never on resolved or dismissed ones", async page => {
  for (const [id, want] of [["rep00001", 1], ["ver00001", 1], ["dis00001", 0], ["res00001", 0]]) {
    await openFromRecords(page, id);
    assert.equal(await page.locator("#asg").count(), want, id);
    await page.click("#cx");
  }
});

await test("Verify writes at once, with no confirmation, and the drawer shows the new status", async page => {
  await openFromRecords(page, "rep00001");
  await page.click('[data-rv="verify"]');
  await page.waitForFunction(() => !document.querySelector("[data-rv]"));
  assert.equal(await page.locator("#dlg.open").count(), 0);
  assert.deepEqual(await calls(page), [{ op: "update", path: "incidents/rep00001", data: { verification: "verified", status: "verified", updatedAt: { __serverTimestamp: true } } }]);
  assert.match(await page.locator("#drawer .pill.Verified").first().textContent(), /Verified/);
  assert.equal(await toastText(page), "Report verified.");
});

for (const [kind, title, verification] of [["false", "False report", "false"], ["duplicate", "Duplicate", "duplicate"]]) {
  await test(`${title}: asks first; Cancel writes nothing; Confirm writes exactly three fields`, async page => {
    await openFromRecords(page, "rep00002");
    await page.click(`[data-rv="${kind}"]`);
    await page.waitForSelector("#dlg.open #dyes");
    assert.ok((await page.locator("#dlg-t").textContent()).length > 0);
    assert.equal(await page.evaluate(() => document.activeElement.id), "dno", "Cancel is focused, so Enter cannot confirm by accident");
    await page.click("#dno");
    assert.equal(await page.locator("#dlg.open").count(), 0);
    assert.deepEqual(await calls(page), []);                                      // cancel = nothing written
    assert.equal(await page.locator("[data-rv]").count(), 3);                     // still reviewable
    await page.click(`[data-rv="${kind}"]`);
    await page.waitForSelector("#dlg.open #dyes");
    await page.click("#dyes");
    await page.waitForFunction(() => !document.querySelector("[data-rv]"));
    const c = await calls(page);
    assert.equal(c.length, 1);
    assert.deepEqual(Object.keys(c[0].data).sort(), ["status", "updatedAt", "verification"]);
    assert.deepEqual(c[0].data, { verification, status: "dismissed", updatedAt: { __serverTimestamp: true } });
    assert.equal(c[0].path, "incidents/rep00002");
  });
}

await test("stale: someone else reviewed it first, so nothing is written and the drawer closes", async page => {
  await openFromRecords(page, "rep00003");
  await page.evaluate(() => { window.__fake.store["incidents/rep00003"].status = "verified"; });   // the app / another admin, no listener fired
  await page.click('[data-rv="verify"]');
  await page.waitForFunction(() => !document.querySelector("#drawer.open"));
  assert.deepEqual(await calls(page), []);
  assert.match(await toastText(page), /Someone already reviewed this report/);
});

await test("stale through the confirmation dialog: False report is refused, nothing is written", async page => {
  await openFromRecords(page, "rep00004");
  await page.click('[data-rv="false"]');
  await page.waitForSelector("#dlg.open #dyes");
  await page.evaluate(() => { window.__fake.store["incidents/rep00004"].status = "dismissed"; });
  await page.click("#dyes");
  await page.waitForFunction(() => !document.querySelector("#drawer.open"));
  assert.deepEqual(await calls(page), []);
  assert.match(await toastText(page), /Someone already reviewed this report/);
});

await test("assign: the responder shows in the drawer at once, the payload is exact, and they cannot be picked twice", async page => {
  await openFromRecords(page, "ver00001");
  await page.click("#asg");
  await page.waitForSelector("#dlg.open #ar");
  assert.deepEqual(await page.locator("#ar option").allTextContents(), ["Choose a responder", "Ana Cruz", "O'Brien & Sons"]);
  await page.selectOption("#ar", "u1");
  assert.equal(await page.inputValue("#au"), "BFP");                 // preselected from the responder's unit
  await page.check('#ares input[value="Fire truck"]');
  await page.click("#asv");
  await page.waitForFunction(() => !document.querySelector("#dlg.open"));
  const c = await calls(page);
  assert.equal(c.length, 1);
  assert.equal(c[0].op, "set");
  assert.match(c[0].path, /^incidents\/ver00001\/assignments\/auto\d+$/);
  assert.deepEqual(Object.keys(c[0].data).sort(), ["assignedAt", "assignedResources", "responderId", "responderName", "unit"]);
  assert.deepEqual(c[0].data, { responderId: "u1", responderName: "Ana Cruz", unit: "BFP", assignedResources: ["Fire truck"], assignedAt: { __serverTimestamp: true } });
  assert.match(await page.locator("#drawer").textContent(), /Ana Cruz - BFP \(Fire truck\)/);   // shown straight away
  await page.click("#asg");                                          // second try on the same report
  await page.waitForSelector("#dlg.open #ar");
  assert.deepEqual(await page.locator("#ar option").allTextContents(), ["Choose a responder", "O'Brien & Sons"]);   // Ana is gone
  assert.equal(await page.locator('#ar option[value="u1"]').count(), 0);
  await page.click("#dno");
  assert.equal((await calls(page)).length, 1);
});

await test("assign: when everyone is assigned the dialog says so and has nothing to save", async page => {
  await openFromRecords(page, "ver00001");
  for (const uid of ["u1", "u2"]) {
    await page.click("#asg");
    await page.waitForSelector("#dlg.open #ar");
    await page.selectOption("#ar", uid);
    await page.click("#asv");
    await page.waitForFunction(() => !document.querySelector("#dlg.open"));
  }
  await page.click("#asg");
  await page.waitForSelector("#ar-none");
  assert.match(await page.locator("#ar-none").textContent(), /already assigned/);
  assert.equal(await page.locator("#asv").count(), 0);
  assert.equal((await calls(page)).length, 2);
});

await test("names: \"O'Brien & Sons\" is stored as typed and shown once-escaped, never as &amp;", async page => {
  await openFromRecords(page, "ver00001");
  await page.click("#asg");
  await page.waitForSelector("#dlg.open #ar");
  await page.selectOption("#ar", "u2");
  await page.click("#asv");
  await page.waitForFunction(() => !document.querySelector("#dlg.open"));
  const c = await calls(page);
  assert.equal(c[0].data.responderName, "O'Brien & Sons");            // raw in Firestore (the app reads this)
  const drawer = await page.locator("#drawer").textContent();
  assert.match(drawer, /O'Brien & Sons - BDRRMO/);
  assert.doesNotMatch(drawer, /&amp;|&#39;|&amp;amp;/);
  await page.click("#cx");
  await page.click('aside button[data-t="over"]');                    // Overview workload card
  await page.waitForSelector(".card h3");
  const wl = await page.locator(".card", { hasText: "Responder workload" }).textContent();
  assert.match(wl, /O'Brien/);
  assert.doesNotMatch(wl, /&amp;|&#39;/);
});

await test("Overview workload and User accounts 'Assigned' update after Save, without reloading", async page => {
  await page.click('aside button[data-t="resp"]');
  await page.waitForSelector("#tb tr");
  const row = name => page.locator("#tb tr", { hasText: name });
  assert.equal((await row("Ana Cruz").locator("td").nth(4).textContent()).trim(), "0");
  await page.click('aside button[data-t="rec"]');
  await page.waitForSelector("tbody tr[data-id]");
  await page.click('tbody tr[data-id="ver00001"]');
  await page.waitForFunction(() => document.querySelector("#asg") && !document.querySelector("#asg").disabled);
  await page.click("#asg");
  await page.selectOption("#ar", "u1");
  await page.click("#asv");
  await page.waitForFunction(() => !document.querySelector("#dlg.open"));
  await page.click("#cx");
  await page.click('aside button[data-t="resp"]');
  await page.waitForSelector("#tb tr");
  assert.equal((await row("Ana Cruz").locator("td").nth(4).textContent()).trim(), "1");
});

await browser.close(); server.close();
console.log(failed ? `\n${failed} FAILED` : "\nall browser checks passed");
process.exit(failed ? 1 : 0);
