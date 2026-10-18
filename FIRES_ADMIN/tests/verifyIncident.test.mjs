// Runs the REAL verifyIncident from js/data.js against a fake Firestore (tests/helpers).
//   node --test tests/verifyIncident.test.mjs
import test, { beforeEach } from "node:test";
import assert from "node:assert/strict";
import { register } from "node:module";

register("./helpers/loader.mjs", import.meta.url);
const fake = await import("./helpers/firebase-stub.mjs");
const { verifyIncident, I } = await import("../js/data.js");
const { mapIncident } = await import("../js/mapping.js");

const ID = "abc123xyz";
const serverDoc = (o = {}) => ({ status: "reported", verification: "pending", severity: "high", ...o });
const local = (o = {}) => mapIncident(ID, serverDoc(o));
const THREE = ["status", "updatedAt", "verification"];

beforeEach(() => { fake.reset(); I.length = 0; });

test("a reported incident is verified: exactly the three fields, then the local copy is patched", async () => {
  fake.store[`incidents/${ID}`] = serverDoc();
  const mine = local(); I.push(mine);
  const res = await verifyIncident(ID, "verify");
  assert.deepEqual(res, { ok: true });
  assert.equal(fake.calls.length, 1);
  const [call] = fake.calls;
  assert.equal(call.op, "update");
  assert.equal(call.path, `incidents/${ID}`);
  assert.deepEqual(Object.keys(call.data).sort(), THREE);
  assert.deepEqual(call.data, { verification: "verified", status: "verified", updatedAt: { __serverTimestamp: true } });
  assert.equal(mine.rawStatus, "verified");
  assert.equal(mine.verification, "verified");
  assert.equal(mine.status, "Verified");
  assert.equal(mine.active, true);
});

test("false report and duplicate close it: dismissed, not active, with the right label", async () => {
  for (const [kind, verification, label] of [["false", "false", "False"], ["duplicate", "duplicate", "Duplicate"]]) {
    fake.reset(); I.length = 0;
    fake.store[`incidents/${ID}`] = serverDoc();
    const mine = local(); I.push(mine);
    assert.deepEqual(await verifyIncident(ID, kind), { ok: true });
    assert.deepEqual(fake.calls[0].data, { verification, status: "dismissed", updatedAt: { __serverTimestamp: true } });
    assert.equal(mine.rawStatus, "dismissed");
    assert.equal(mine.status, label);
    assert.equal(mine.active, false);
  }
});

test("already reviewed by someone else: stale, nothing written, local copy untouched", async () => {
  for (const status of ["verified", "dispatched", "on_scene", "resolved", "dismissed"]) {
    fake.reset(); I.length = 0;
    fake.store[`incidents/${ID}`] = serverDoc({ status });
    const mine = local(); I.push(mine);          // this screen still thinks it is "reported"
    assert.deepEqual(await verifyIncident(ID, "verify"), { stale: true }, status);
    assert.equal(fake.calls.length, 0, status);
    assert.equal(mine.rawStatus, "reported", status);
    assert.equal(mine.status, "Reported", status);
  }
});

test("the incident no longer exists: stale, nothing written", async () => {
  I.push(local());
  assert.deepEqual(await verifyIncident(ID, "false"), { stale: true });
  assert.equal(fake.calls.length, 0);
});

test("a stored incident with no status counts as reported, as everywhere else", async () => {
  fake.store[`incidents/${ID}`] = {};
  assert.deepEqual(await verifyIncident(ID, "verify"), { ok: true });
  assert.equal(fake.calls.length, 1);
});

test("an invalid request is blocked before anything is read or written", async () => {
  fake.store[`incidents/${ID}`] = serverDoc();
  for (const [id, kind] of [[ID, "dispatch"], [ID, "resolve"], [ID, ""], [ID, "toString"], [ID, undefined], ["", "verify"], [undefined, "verify"]]) {
    assert.deepEqual(await verifyIncident(id, kind), { blocked: true }, `${id} ${kind}`);
  }
  assert.equal(fake.calls.length, 0);
});

test("it never writes to anything but the incident document", async () => {
  fake.store[`incidents/${ID}`] = serverDoc();
  await verifyIncident(ID, "verify");
  assert.deepEqual(fake.calls.map(c => c.path), [`incidents/${ID}`]);
});

test("an incident that is not in I can be patched through the third argument", async () => {
  fake.store[`incidents/${ID}`] = serverDoc();
  const fromRange = local();                       // e.g. loaded by the Records date range
  assert.deepEqual(await verifyIncident(ID, "false", fromRange), { ok: true });
  assert.equal(fromRange.status, "False");
  assert.equal(fromRange.active, false);
});

test("with no local copy at all, the write still succeeds", async () => {
  fake.store[`incidents/${ID}`] = serverDoc();
  assert.deepEqual(await verifyIncident(ID, "verify"), { ok: true });
  assert.equal(fake.calls.length, 1);
});

test("a failed write is thrown to the caller and leaves the local copy untouched", async () => {
  fake.store[`incidents/${ID}`] = serverDoc();
  const mine = local(); I.push(mine);
  const denied = Object.assign(new Error("denied"), { code: "permission-denied" });
  fake.failNextUpdate(denied);
  await assert.rejects(() => verifyIncident(ID, "verify"), e => e.code === "permission-denied");
  assert.equal(mine.rawStatus, "reported");
  assert.equal(mine.status, "Reported");
  assert.equal(mine.active, true);
  assert.equal(fake.calls.length, 0);
});

test("after a failure the same report can be reviewed again", async () => {
  fake.store[`incidents/${ID}`] = serverDoc();
  fake.failNextUpdate(new Error("offline"));
  await assert.rejects(() => verifyIncident(ID, "verify"));
  assert.deepEqual(await verifyIncident(ID, "verify"), { ok: true });
  assert.equal(fake.calls.length, 1);
});

// ---------- W13.13: when the save does not go through ----------

test("offline when the report is read: it throws 'unavailable', nothing is written, the screen's copy is untouched", async () => {
  fake.store[`incidents/${ID}`] = serverDoc();
  const mine = local(); I.push(mine);
  fake.failNextRead(Object.assign(new Error("client is offline"), { code: "unavailable" }));
  await assert.rejects(() => verifyIncident(ID, "verify"), e => e.code === "unavailable");
  assert.equal(fake.calls.length, 0);
  assert.equal(mine.rawStatus, "reported");
  assert.equal(mine.active, true);
});

test("the check and the write are one transaction: a stale report writes nothing, an open one writes once", async () => {
  fake.store[`incidents/${ID}`] = serverDoc({ status: "verified" });
  assert.deepEqual(await verifyIncident(ID, "false"), { stale: true });
  assert.equal(fake.calls.length, 0);
  fake.reset(); I.length = 0;
  fake.store[`incidents/${ID}`] = serverDoc();
  assert.deepEqual(await verifyIncident(ID, "false"), { ok: true });
  assert.equal(fake.calls.length, 1);
});

test("a refused commit (permission-denied) is thrown, nothing is recorded, and a later try works", async () => {
  fake.store[`incidents/${ID}`] = serverDoc();
  const mine = local(); I.push(mine);
  fake.failNextUpdate(Object.assign(new Error("denied"), { code: "permission-denied" }));
  await assert.rejects(() => verifyIncident(ID, "duplicate"), e => e.code === "permission-denied");
  assert.equal(fake.calls.length, 0);
  assert.equal(mine.rawStatus, "reported");
  assert.deepEqual(await verifyIncident(ID, "duplicate"), { ok: true });
  assert.equal(mine.status, "Duplicate");
});
