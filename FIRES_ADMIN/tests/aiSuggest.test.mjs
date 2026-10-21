import test from "node:test";
import assert from "node:assert/strict";
import { metersBetween, nearbyCandidates, buildPayload, requestSuggestion, aiPanelHtml, failText, AI_NOTE } from "../js/aiSuggest.js";
import { mapIncident, esc } from "../js/mapping.js";

const base = { latitude: 14.5995, longitude: 120.9842, status: "reported", fireType: "structural", description: "House on fire <b>x</b>", addressText: "Purok 3", fireSize: "large", peopleAtRisk: 3, trapped: true, vulnerablePersons: ["children"], hazards: ["lpg_tank"], severity: "high", reporterName: "Juan Dela Cruz", reporterContact: "09171234567" };
const ts = ms => ({ toMillis: () => ms });
const mk = (id, over = {}, ms = 1_700_000_000_000) => mapIncident(id, { ...base, submittedAt: ts(ms), ...over });

test("mapIncident keeps plain, unescaped AI fields and no reporter details", () => {
  const i = mk("abc123xyz");
  assert.equal(i.plain.description, "House on fire <b>x</b>");
  assert.equal(i.description, "House on fire &lt;b&gt;x&lt;/b&gt;");
  assert.ok(!JSON.stringify(i.plain).includes("Juan") && !JSON.stringify(i.plain).includes("0917"));
});

test("metersBetween is about right", () => {
  const m = metersBetween({ lat: 14.5995, lng: 120.9842 }, { lat: 14.5995, lng: 120.9852 });
  assert.ok(m > 100 && m < 120);
});

test("nearbyCandidates keeps close, recent, open reports only, nearest first, max 5", () => {
  const me = mk("me00000");
  const near = mk("n1", { latitude: 14.5996 }, 1_700_000_000_000 + 5 * 60000);
  const near2 = mk("n2", { latitude: 14.5998 });
  const far = mk("far", { latitude: 14.7 });
  const old = mk("old", {}, 1_700_000_000_000 - 3 * 3600000);
  const closed = mk("cl", { status: "resolved" });
  const noGps = mk("ng", { latitude: 0, longitude: 0 });
  const got = nearbyCandidates(me, [me, near2, near, far, old, closed, noGps]);
  assert.deepEqual(got.map(x => x.id), ["n1", "n2"]);
  assert.equal(got[0].minutesApart, 5);
  assert.equal(nearbyCandidates(noGps, [me]).length, 0);
  const many = Array.from({ length: 9 }, (_, k) => mk("m" + k, { latitude: 14.5996 + k * 1e-5 }));
  assert.equal(nearbyCandidates(me, [me, ...many]).length, 5);
});

test("buildPayload sends text fields and the form severity only", () => {
  const p = buildPayload(mk("me00000"), []);
  assert.equal(p.report.formSeverity, "high");
  assert.equal(p.report.trapped, true);
  assert.deepEqual(Object.keys(p.report).sort(), ["address", "description", "fireSize", "fireType", "formSeverity", "hazards", "peopleAtRisk", "trapped", "vulnerable"]);
});

const okRes = a => ({ ok: true, status: 200, json: async () => a });
const answer = { severity: "critical", confidence: 0.86, reasons: ["People trapped"], duplicateOf: null, duplicateReason: "" };

test("requestSuggestion sends the token and returns the answer", async () => {
  let seen;
  const r = await requestSuggestion("https://w/suggest", "TOKEN", { a: 1 }, async (u, o) => { seen = { u, o }; return okRes(answer); });
  assert.equal(r.ok, true);
  assert.equal(seen.o.headers.Authorization, "Bearer TOKEN");
  assert.equal(JSON.parse(seen.o.body).a, 1);
});

test("requestSuggestion turns every failure into a plain sentence and never throws", async () => {
  assert.equal((await requestSuggestion("u", "t", {}, async () => ({ ok: false, status: 429 }))).text, failText(429));
  assert.equal((await requestSuggestion("u", "t", {}, async () => ({ ok: false, status: 403 }))).text, failText(403));
  assert.equal((await requestSuggestion("u", "t", {}, async () => { throw new Error("offline"); })).ok, false);
  assert.equal((await requestSuggestion("u", "t", {}, async () => okRes({ nonsense: 1 }))).ok, false);
  const t = Object.assign(new Error("t"), { name: "TimeoutError" });
  assert.equal((await requestSuggestion("u", "t", {}, async () => { throw t; })).text, failText("timeout"));
});

test("panel: off without an endpoint, hidden for closed reports, button when idle", () => {
  const i = mk("me00000");
  assert.match(aiPanelHtml(i, {}, ""), /Not set up yet/);
  assert.equal(aiPanelHtml({ ...i, active: false }, {}, "https://w"), "");
  const idle = aiPanelHtml(i, {}, "https://w");
  assert.match(idle, /id="aib"/); assert.ok(idle.includes(esc(AI_NOTE)));
});

test("panel: shows severity, mismatch hint, escaped reasons and a link to the duplicate", () => {
  const i = mk("me00000"), other = mk("dup0001");
  const html = aiPanelHtml(i, { status: "done", answer: { ...answer, reasons: ["<img src=x onerror=1>"], duplicateOf: "dup0001", duplicateReason: "Same street" } }, "https://w", [i, other]);
  assert.match(html, /Critical/); assert.match(html, /form score says <b>High<\/b>/);
  assert.ok(!html.includes("<img")); assert.match(html, /data-aid="dup0001"/);
  const same = aiPanelHtml(mk("x", { severity: "critical" }), { status: "done", answer }, "https://w", []);
  assert.match(same, /Matches the form score/);
});

test("panel: loading and error states", () => {
  const i = mk("me00000");
  assert.match(aiPanelHtml(i, { status: "loading" }, "https://w"), /Reading the report/);
  const e = aiPanelHtml(i, { status: "error", text: "Nope <b>" }, "https://w");
  assert.match(e, /Try again/); assert.ok(!e.includes("<b>"));
});
