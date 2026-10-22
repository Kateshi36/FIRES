// W13.20: what the website must NOT do, checked on the source itself, so a later edit cannot add it
// by accident.   node --test tests/scope.test.mjs
//
// The website only reviews reports (verify / false / duplicate) and assigns responders. Dispatch,
// On scene, Resolve, severity override and chat belong to the responder in the app.
import test, { beforeEach } from "node:test";
import assert from "node:assert/strict";
import { readFileSync, readdirSync } from "node:fs";
import { register } from "node:module";

register("./helpers/loader.mjs", import.meta.url);
const fake = await import("./helpers/firebase-stub.mjs");
const { verifyIncident, I } = await import("../js/data.js");
const { buildVerifyUpdate, VERIFY_BUTTONS } = await import("../js/verifyRules.js");
const { mapIncident } = await import("../js/mapping.js");

const root = new URL("../", import.meta.url);
const read = p => readFileSync(new URL(p, root), "utf8");
const strip = s => s.replace(/\/\*[\s\S]*?\*\//g, "").replace(/(^|[^:"'`\\])\/\/.*$/gm, "$1");   // comments out
const files = ["index.html", "login.html", ...readdirSync(new URL("js/", root)).map(f => "js/" + f)];
const code = Object.fromEntries(files.map(f => [f, strip(read(f))]));
const all = Object.values(code).join("\n");

beforeEach(() => { fake.reset(); I.length = 0; });

test("no button or text for Dispatch, On scene, Resolve (as an action) or severity override", () => {
  for (const re of [/dispatch(?!ed)/i, /on[ _-]scene(?!")/i, />\s*(Mark as )?Resolve\s*</i, /severity\s*override|set\s*severity|change\s*severity/i, /severitySource/]) {
    assert.equal(re.test(all), false, String(re));
  }
});

test("the only status strings the website can write are verified and dismissed", () => {
  for (const kind of ["verify", "false", "duplicate"]) {
    const u = buildVerifyUpdate(kind, 1);
    assert.ok(["verified", "dismissed"].includes(u.status), kind);
  }
  assert.deepEqual(VERIFY_BUTTONS.map(b => b.kind), ["verify", "false", "duplicate"]);
});

test("no chat: the website never writes or even reads the messages collection for sending", () => {
  assert.equal(/<textarea|type=["']?text["']?[^>]*(message|chat)/i.test(all), false);
  assert.equal(/addDoc|writeBatch/.test(all.replace(/import[^;]*;/g, "")), false);   // no other kind of write exists
  const writes = [...code["js/data.js"].matchAll(/\b(setDoc|updateDoc)\(\s*(?:ref|doc\(db,\s*("[^"]+"))/g)].map(m => m[1]);
  assert.equal(writes.length, 3);                                   // assignment, new user profile, role change (the activity log line is checked below)
  assert.equal(/"messages"[^)]*\)\s*,\s*\{/.test(code["js/data.js"].replace(/getDocs\([^)]*\)/g, "")), false);
});

test("every write call is one of the six known ones", () => {
  const calls = [...all.matchAll(/\b(tx\.update|tx\.set|tx\.delete|setDoc|updateDoc)\(/g)].map(m => m[1]).sort();
  assert.deepEqual(calls, ["setDoc", "setDoc", "setDoc", "setDoc", "tx.update", "tx.update", "updateDoc"]);   // assignment, profile, activity log line, verify + AI photo answer (both tx.update), role
});

test("the activity log is the only new collection written, and only through logActivity", () => {
  const d = code["js/data.js"];
  assert.equal((d.match(/doc\(collection\(db, "activity"\)\)/g) || []).length, 1);
  assert.equal(/setDoc\(doc\(collection\(db, "(?!activity|users", responderId, "notifications)/.test(d), false);   // the only other new write: the "assigned" notification
});

test("the verify write has exactly three fields for each of the three actions, through the real code", async () => {
  for (const [kind, v, s] of [["verify", "verified", "verified"], ["false", "false", "dismissed"], ["duplicate", "duplicate", "dismissed"]]) {
    fake.reset(); I.length = 0;
    fake.store["incidents/x1"] = { status: "reported", verification: "pending", severity: "high" };
    I.push(mapIncident("x1", fake.store["incidents/x1"]));
    assert.deepEqual(await verifyIncident("x1", kind), { ok: true });
    assert.equal(fake.calls.length, 1, kind);
    assert.deepEqual(Object.keys(fake.calls[0].data).sort(), ["status", "updatedAt", "verification"], kind);
    assert.deepEqual(fake.calls[0].data, { verification: v, status: s, updatedAt: { __serverTimestamp: true } }, kind);
    assert.equal("severity" in fake.calls[0].data, false, kind);
  }
});

test("the only delete on the website is removeAssignment, under incidents/*/assignments", () => {
  const d = code["js/data.js"];
  assert.equal((d.match(/\bdeleteDoc\(/g) || []).length, 1);
  assert.match(d, /deleteDoc\(doc\(db, "incidents", incidentId, "assignments", d\.id\)\)/);
});
