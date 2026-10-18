// Runs the REAL saveAssignment / newAssignmentId from js/data.js against a fake Firestore.
//   node --test tests/saveAssignment.test.mjs
import test, { beforeEach } from "node:test";
import assert from "node:assert/strict";
import { register } from "node:module";

register("./helpers/loader.mjs", import.meta.url);
const fake = await import("./helpers/firebase-stub.mjs");
const { saveAssignment, newAssignmentId, I } = await import("../js/data.js");
const { mapIncident, mapAssignment } = await import("../js/mapping.js");

const INC = "inc1", AID = "asg1", PATH = `incidents/${INC}/assignments/${AID}`;
const payload = (o = {}) => ({ responderId: "u1", responderName: "Ana Cruz", unit: "BFP", assignedResources: ["Fire truck", "Water tanker"], ...o });
const stamp = { __serverTimestamp: true };
const local = () => { const i = mapIncident(INC, { status: "verified" }); i.assignLoaded = true; return i; };

beforeEach(() => { fake.reset(); I.length = 0; });

test("newAssignmentId: a fresh id each time, empty without an incident", () => {
  const a = newAssignmentId(INC), b = newAssignmentId(INC);
  assert.ok(a && b && a !== b);
  assert.equal(newAssignmentId(""), "");
  assert.equal(newAssignmentId(undefined), "");
});

test("saves with setDoc under the id it was given: the four fields plus assignedAt = serverTimestamp()", async () => {
  assert.deepEqual(await saveAssignment(INC, AID, payload()), { ok: true });
  assert.equal(fake.calls.length, 1);
  const [call] = fake.calls;
  assert.equal(call.op, "set");
  assert.equal(call.path, PATH);
  assert.deepEqual(call.data, { ...payload(), assignedAt: stamp });
  assert.deepEqual(Object.keys(call.data).sort(), ["assignedAt", "assignedResources", "responderId", "responderName", "unit"]);
});

test("NOTHING ELSE is written: extra fields on the payload are dropped", async () => {
  await saveAssignment(INC, AID, payload({ role: "admin", status: "resolved", assignedAt: "2020-01-01", severity: "low" }));
  assert.deepEqual(Object.keys(fake.calls[0].data).sort(), ["assignedAt", "assignedResources", "responderId", "responderName", "unit"]);
  assert.deepEqual(fake.calls[0].data.assignedAt, stamp);       // the caller cannot choose the time
});

test("it only touches the assignments subcollection of that incident", async () => {
  await saveAssignment(INC, AID, payload());
  assert.deepEqual(fake.calls.map(c => c.path), [PATH]);
});

test("Try again after a failed save writes the SAME document, so there is only ever one", async () => {
  fake.failNextSet(Object.assign(new Error("offline"), { code: "unavailable" }));
  await assert.rejects(() => saveAssignment(INC, AID, payload()), e => e.code === "unavailable");
  assert.equal(fake.calls.length, 0);
  assert.deepEqual(await saveAssignment(INC, AID, payload()), { ok: true });
  assert.deepEqual(fake.calls.map(c => c.path), [PATH]);
  assert.deepEqual(Object.keys(fake.store).filter(k => k.includes("/assignments/")), [PATH]);
});

test("Try again after a save that DID reach the server: found, counted as saved, nothing written", async () => {
  fake.store[PATH] = { ...payload(), assignedAt: { seconds: 1 } };      // the earlier try went through
  const mine = local();
  assert.deepEqual(await saveAssignment(INC, AID, payload(), mine), { ok: true, already: true });
  assert.equal(fake.calls.length, 0);                                    // rules would refuse an overwrite
  assert.equal(mine.res.length, 1);                                     // and the screen shows it
});

test("the same assignment counts as the same even if the resources were stored in another order", async () => {
  fake.store[PATH] = { ...payload({ assignedResources: ["Water tanker", "Fire truck"] }) };
  assert.deepEqual(await saveAssignment(INC, AID, payload()), { ok: true, already: true });
});

test("a DIFFERENT assignment under this id: stale, nothing written, the stored one is untouched", async () => {
  for (const other of [{ responderId: "u2" }, { unit: "BDRRMO" }, { assignedResources: [] }]) {
    fake.reset();
    fake.store[PATH] = { ...payload(other), assignedAt: { seconds: 1 } };
    const before = structuredClone(fake.store[PATH]);
    const mine = local();
    assert.deepEqual(await saveAssignment(INC, AID, payload(), mine), { stale: true }, JSON.stringify(other));
    assert.equal(fake.calls.length, 0);
    assert.deepEqual(fake.store[PATH], before);
    assert.equal(mine.res.length, 0);
  }
});

test("an invalid request is blocked before anything is read or written", async () => {
  const bad = [
    [INC, AID, undefined], [INC, AID, null], [INC, AID, "x"], [INC, AID, {}],
    ["", AID, payload()], [undefined, AID, payload()], [INC, "", payload()], [INC, undefined, payload()],
    [INC, AID, payload({ responderId: "" })], [INC, AID, payload({ responderId: 5 })],
    [INC, AID, payload({ responderName: "" })], [INC, AID, payload({ responderName: "x".repeat(101) })],
    [INC, AID, payload({ unit: "Somebody else" })], [INC, AID, payload({ unit: "" })],
    [INC, AID, payload({ assignedResources: undefined })], [INC, AID, payload({ assignedResources: "Fire truck" })],
    [INC, AID, payload({ assignedResources: ["Helicopter"] })], [INC, AID, payload({ assignedResources: ["Fire truck", "Fire truck"] })]
  ];
  for (const [i, a, p] of bad) assert.deepEqual(await saveAssignment(i, a, p), { blocked: true }, JSON.stringify([i, a, p]));
  assert.equal(fake.calls.length, 0);
});

test("resources are optional: an empty list saves", async () => {
  assert.deepEqual(await saveAssignment(INC, AID, payload({ assignedResources: [] })), { ok: true });
  assert.deepEqual(fake.calls[0].data.assignedResources, []);
});

test("after a save, the screen's copy of the incident lists the responder, in the shape mapAssignment makes", async () => {
  const mine = local(); I.push(mine);                                    // found in I by default
  await saveAssignment(INC, AID, payload());
  assert.deepEqual(mine.res, [mapAssignment(payload())]);
});

test("an incident outside I can be patched through the fourth argument", async () => {
  const fromRange = local();
  await saveAssignment(INC, AID, payload(), fromRange);
  assert.equal(fromRange.res.length, 1);
});

test("a failed save leaves the screen's copy untouched", async () => {
  const mine = local();
  fake.failNextSet(new Error("denied"));
  await assert.rejects(() => saveAssignment(INC, AID, payload(), mine));
  assert.equal(mine.res.length, 0);
});

test("another assignment already listed stays, and the new one is added after it", async () => {
  const mine = local(); mine.res.push(mapAssignment({ responderId: "u9", unit: "BDRRMO", responderName: "Ben" }));
  await saveAssignment(INC, AID, payload(), mine);
  assert.deepEqual(mine.res.map(r => r[0]), ["u9", "u1"]);
});

test("with no local copy at all, the write still succeeds", async () => {
  assert.deepEqual(await saveAssignment(INC, AID, payload()), { ok: true });
  assert.equal(fake.calls.length, 1);
});
