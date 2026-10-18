// W13.19: after a saved assignment the drawer, the Overview workload card and the "Assigned" column on
// User accounts show it straight away.   node --test tests/afterAssign.test.mjs
//
// Runs the REAL data.js against a fake Firestore. The screens read the same lists these tests read:
// assignedCount(I, uid) is the number behind both the workload card and the "Assigned" column, and
// availableResponders() is what the drawer's Assign dialog offers next.
import test, { beforeEach } from "node:test";
import assert from "node:assert/strict";
import { register } from "node:module";

register("./helpers/loader.mjs", import.meta.url);
const fake = await import("./helpers/firebase-stub.mjs");
const data = await import("../js/data.js");
const { saveAssignment, loadIncidentsOnce, loadActiveAssignments, I } = data;
const { assignedCount, redrawAfterAssign, availableResponders, assignButtonHtml, canAssign } = await import("../js/assignRules.js");
const { drawerAfterReview } = await import("../js/verifyRules.js");

let n = 0, INC;
const payload = (o = {}) => ({ responderId: "u1", responderName: "Ana Cruz", unit: "BFP", assignedResources: ["Fire truck"], ...o });
const users = [
  { id: "u1", role: "responder", fullName: "Ana Cruz", name: "Ana Cruz", address: "BFP" },
  { id: "u2", role: "responder", fullName: "Ben Reyes", name: "Ben Reyes", address: "BDRRMO" }
];
const seed = () => {
  INC = `inc${++n}`;
  fake.store[`incidents/${INC}`] = { status: "verified", severity: "high", submittedAt: new Date(500), updatedAt: new Date(1000) };
};
const mine = () => I.find(i => i.id === INC);
const load = async () => { await loadIncidentsOnce(); await loadActiveAssignments(); return mine(); };

beforeEach(() => { fake.reset(); I.length = 0; });

// ---------- the numbers on Overview and User accounts ----------

test("assignedCount counts the incidents a responder is on, and ignores bad input", () => {
  const list = [{ res: [["u1", "BFP", "Ana", []]] }, { res: [["u1", "BFP", "Ana", []], ["u2", "BDRRMO", "Ben", []]] }, { res: [] }];
  assert.equal(assignedCount(list, "u1"), 2);
  assert.equal(assignedCount(list, "u2"), 1);
  assert.equal(assignedCount(list, "nobody"), 0);
  assert.equal(assignedCount(null, "u1"), 0);
  assert.equal(assignedCount([null, {}, { res: "x" }], "u1"), 0);
});

test("after Save the responder's count goes from 0 to 1 with no extra read", async () => {
  seed(); await load();
  assert.equal(assignedCount(I, "u1"), 0);
  assert.deepEqual(await saveAssignment(INC, "a1", payload()), { ok: true });
  assert.equal(assignedCount(I, "u1"), 1);            // the workload card and the "Assigned" column
  assert.equal(assignedCount(I, "u2"), 0);            // nobody else changes
});

test("the count survives the live listener rebuilding the list", async () => {
  seed(); await load();
  await saveAssignment(INC, "a1", payload());
  await loadIncidentsOnce();                          // what every snapshot does
  assert.equal(assignedCount(I, "u1"), 1);
});

test("a second responder on the same report adds one to that responder only", async () => {
  seed(); await load();
  await saveAssignment(INC, "a1", payload());
  await saveAssignment(INC, "a2", payload({ responderId: "u2", responderName: "Ben Reyes", unit: "BDRRMO", assignedResources: [] }));
  assert.equal(assignedCount(I, "u1"), 1);
  assert.equal(assignedCount(I, "u2"), 1);
});

// ---------- the drawer ----------

test("the drawer list gets the new responder, and the dialog no longer offers them", async () => {
  seed(); const i = await load();
  assert.deepEqual(availableResponders(i, users).map(r => r.id), ["u1", "u2"]);
  await saveAssignment(INC, "a1", payload());
  const again = mine();                               // found again by id, as afterAssign does
  assert.deepEqual(again.res.map(r => [r[0], r[1], r[2], r[3]]), [["u1", "BFP", "Ana Cruz", ["Fire truck"]]]);
  assert.deepEqual(availableResponders(again, users).map(r => r.id), ["u2"]);
});

test("when everyone is assigned the Assign button is still drawn only for an open report (it then explains)", async () => {
  seed(); const i = await load();
  await saveAssignment(INC, "a1", payload());
  await saveAssignment(INC, "a2", payload({ responderId: "u2", responderName: "Ben Reyes", unit: "BDRRMO" }));
  assert.equal(canAssign(mine()), true);
  assert.notEqual(assignButtonHtml(mine()), "");
  assert.deepEqual(availableResponders(mine(), users), []);
});

test("details already loaded: the drawer is redrawn from them; not loaded: they are loaded; gone: closed", () => {
  assert.equal(drawerAfterReview({ detailState: "ready" }), "redraw");
  assert.equal(drawerAfterReview({ detailState: "idle" }), "reload");
  assert.equal(drawerAfterReview(null), "close");
});

// ---------- the tab behind the drawer ----------

test("Overview and Live map are drawn again; User accounts only re-runs its filters", () => {
  assert.equal(redrawAfterAssign("over"), "screen");
  assert.equal(redrawAfterAssign("map"), "screen");
  assert.equal(redrawAfterAssign("resp"), "filters");
});

test("Incident records, Reports and anything unknown are left alone", () => {
  for (const t of ["rec", "rep", "", undefined, null, "nonsense"]) assert.equal(redrawAfterAssign(t), "none", String(t));
});
