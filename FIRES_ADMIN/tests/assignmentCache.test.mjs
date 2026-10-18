// W13.10: after a saved assignment, every screen sees it WITHOUT another read. Runs the REAL data.js
// against a fake Firestore.   node --test tests/assignmentCache.test.mjs
//
// Screens do not hold the assignments themselves. The live listener rebuilds every incident object
// from the snapshot plus a cache of the assignments already read, so "rebuilt from the cache" is the
// moment that matters. Each test saves an assignment and then rebuilds the list (loadIncidentsOnce,
// which fills I the same way the listener does) and looks at what the screens would now get.
import test, { beforeEach } from "node:test";
import assert from "node:assert/strict";
import { register } from "node:module";

register("./helpers/loader.mjs", import.meta.url);
const fake = await import("./helpers/firebase-stub.mjs");
const data = await import("../js/data.js");
const { saveAssignment, loadIncidentsOnce, loadActiveAssignments, loadDetail, loadRange, I } = data;
const { mapAssignment } = await import("../js/mapping.js");

// The cache lives for the whole module, so every test uses its own incident id.
let n = 0;
let INC, PATH;
const AID = "asg1";
const payload = (o = {}) => ({ responderId: "u1", responderName: "Ana Cruz", unit: "BFP", assignedResources: ["Fire truck"], ...o });
const seed = (extra = {}) => {
  INC = `inc${++n}`; PATH = `incidents/${INC}/assignments/${AID}`;
  fake.store[`incidents/${INC}`] = { status: "verified", severity: "high", submittedAt: new Date(500), updatedAt: new Date(1000), ...extra };
};
const mine = () => I.find(i => i.id === INC);
const assignmentReads = () => fake.reads.filter(r => r.path === `incidents/${INC}/assignments`);
// What the incident screens would get from a fresh read of the assignments collection.
const fromServer = async () => (await fake.getDocs(fake.collection(null, "incidents", INC, "assignments"))).docs.map(d => mapAssignment(d.data()));
const loadedIncident = async () => { await loadIncidentsOnce(); await loadActiveAssignments(); return mine(); };

beforeEach(() => { fake.reset(); I.length = 0; });

test("a saved assignment is still there after the list is rebuilt, and nothing is read again", async () => {
  seed();
  await loadedIncident();
  assert.deepEqual(await saveAssignment(INC, AID, payload()), { ok: true });
  fake.reads.length = 0;

  await loadIncidentsOnce();                       // what the live listener does on every change
  const rebuilt = mine();
  assert.equal(rebuilt.assignLoaded, true);
  assert.deepEqual(rebuilt.res.map(r => r[0]), ["u1"]);
  assert.equal(await loadActiveAssignments(), false);          // nothing left to load
  assert.equal(assignmentReads().length, 0);                   // and no read of the assignments
});

test("the cached row is exactly what re-reading the document would give", async () => {
  seed();
  await loadedIncident();
  await saveAssignment(INC, AID, payload({ responderName: "O'Brien & Sons", assignedResources: ["Fire truck", "Water tanker"] }));
  await loadIncidentsOnce();
  assert.deepEqual(mine().res, await fromServer());
  assert.equal(mine().res[0][2], "O&#39;Brien &amp; Sons");     // escaped once, for the screens
  assert.equal(fake.store[PATH].responderName, "O'Brien & Sons");  // stored as typed
});

test("the cache is updated even when the screen's copy is NOT the one that was cached", async () => {
  // The listener rebuilt the list while the drawer was still loading. The drawer's object (first)
  // is the one whose assignments end up cached. The save then patches the NEWER object in I (second).
  seed();
  await loadIncidentsOnce(); const first = mine();
  await loadIncidentsOnce(); const second = mine();
  assert.notEqual(first, second);
  await loadDetail(first);                          // caches first.res
  assert.equal(second.assignLoaded, false);

  assert.deepEqual(await saveAssignment(INC, AID, payload()), { ok: true });   // default local: second
  fake.reads.length = 0;

  await loadIncidentsOnce();
  assert.equal(mine().assignLoaded, true);
  assert.deepEqual(mine().res.map(r => r[0]), ["u1"], "the new assignee must not be missing from the rebuilt list");
  assert.equal(await loadActiveAssignments(), false);
  assert.equal(assignmentReads().length, 0);
});

test("the cache is updated when the incident is not in the list at the moment of saving", async () => {
  seed();
  await loadedIncident();
  I.length = 0;                                     // for example pushed out of the newest-200 list
  assert.deepEqual(await saveAssignment(INC, AID, payload()), { ok: true });
  await loadIncidentsOnce();
  assert.deepEqual(mine().res.map(r => r[0]), ["u1"]);
});

test("a list that was never loaded is NOT turned into a loaded one: the next load reads it all", async () => {
  seed();
  await loadIncidentsOnce();                        // no assignments read yet
  assert.equal(mine().assignLoaded, false);
  await saveAssignment(INC, AID, payload());
  await loadIncidentsOnce();
  assert.equal(mine().assignLoaded, false);         // one known row is not "everyone assigned"
  assert.deepEqual(mine().res, []);

  assert.equal(await loadActiveAssignments(), true);
  assert.equal(assignmentReads().length, 1);
  assert.deepEqual(mine().res.map(r => r[0]), ["u1"]);
});

test("a retry that finds its own earlier save lists the responder once", async () => {
  seed();
  await loadedIncident();
  fake.store[PATH] = { ...payload(), assignedAt: { seconds: 1 } };     // the first try got through, the answer was lost
  assert.deepEqual(await saveAssignment(INC, AID, payload()), { ok: true, already: true });
  await loadIncidentsOnce();
  assert.deepEqual(mine().res.map(r => r[0]), ["u1"]);
});

test("saving the same assignment twice never lists the responder twice", async () => {
  seed();
  await loadedIncident();
  await saveAssignment(INC, AID, payload());
  await saveAssignment(INC, AID, payload());
  await loadIncidentsOnce();
  assert.equal(mine().res.length, 1);
});

test("a second responder is added after the first", async () => {
  seed();
  await loadedIncident();
  await saveAssignment(INC, AID, payload());
  await saveAssignment(INC, "asg2", payload({ responderId: "u2", responderName: "Ben", unit: "BDRRMO", assignedResources: [] }));
  await loadIncidentsOnce();
  assert.deepEqual(mine().res.map(r => r[0]), ["u1", "u2"]);
});

test("a failed save changes neither the screen nor the cache", async () => {
  seed();
  await loadedIncident();
  fake.failNextSet(Object.assign(new Error("offline"), { code: "unavailable" }));
  await assert.rejects(() => saveAssignment(INC, AID, payload()));
  assert.deepEqual(mine().res, []);
  await loadIncidentsOnce();
  assert.deepEqual(mine().res, []);
});

test("a DIFFERENT assignment under the id (stale) changes neither the screen nor the cache", async () => {
  seed();
  await loadedIncident();
  fake.store[PATH] = { ...payload({ responderId: "u9" }), assignedAt: { seconds: 1 } };
  assert.deepEqual(await saveAssignment(INC, AID, payload()), { stale: true });
  await loadIncidentsOnce();
  assert.deepEqual(mine().res, []);
});

test("an invalid request (blocked) changes neither the screen nor the cache", async () => {
  seed();
  await loadedIncident();
  assert.deepEqual(await saveAssignment(INC, AID, payload({ unit: "Somebody else" })), { blocked: true });
  await loadIncidentsOnce();
  assert.deepEqual(mine().res, []);
});

test("the Records screen's date-range load sees it too", async () => {
  seed();
  await loadedIncident();
  await saveAssignment(INC, AID, payload());
  const { list } = await loadRange(null, null);
  const copy = list.find(i => i.id === INC);
  assert.equal(copy.assignLoaded, true);
  assert.deepEqual(copy.res.map(r => r[0]), ["u1"]);
});

test("if the incident itself changed in between, the cached list is not trusted (unchanged behaviour)", async () => {
  seed();
  await loadedIncident();
  await saveAssignment(INC, AID, payload());
  fake.store[`incidents/${INC}`].updatedAt = new Date(2000);          // someone verified it from the app
  await loadIncidentsOnce();
  assert.equal(mine().assignLoaded, false);                           // read again from the server
  await loadActiveAssignments();
  assert.deepEqual(mine().res.map(r => r[0]), ["u1"]);
});
