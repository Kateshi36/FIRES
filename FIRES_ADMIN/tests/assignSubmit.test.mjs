// W13.18: Save, the error under each field, Try again, and the one assignment id kept for retries.
// The logic is in assignRules.js (submitAssignment, resultView, createAssignSession) and is tested on
// its own with a fake `save`. The last tests run it through the REAL saveAssignment / refreshAssignments
// from js/data.js against a fake Firestore, to prove that no retry can add a second assignment.
//   node --test tests/assignSubmit.test.mjs
import test, { beforeEach } from "node:test";
import assert from "node:assert/strict";
import { register } from "node:module";

register("./helpers/loader.mjs", import.meta.url);
const fake = await import("./helpers/firebase-stub.mjs");
const rules = await import("../js/assignRules.js");
const { saveAssignment, refreshAssignments, newAssignmentId, loadIncidentsOnce, I } = await import("../js/data.js");
const { mapIncident } = await import("../js/mapping.js");
const { SAVE_TEXT } = await import("../js/saveMessages.js");
const {
  MSG, SAVE_LABEL, ERROR_IDS, createAssignSession, submitAssignment, resultView, assignDialogHtml, responderOptions
} = rules;

const INC = "inc1";
const user = (id, name, unit = "BFP") => ({ id, role: "responder", name, fullName: name, email: `${id}@x.ph`, address: unit });
const USERS = [user("u1", "Ana Cruz"), user("u2", "Ben Reyes", "BDRRMO")];
const RESPONDERS = responderOptions(USERS);
const incident = (res = []) => {
  const i = mapIncident(INC, { status: "verified" });
  i.assignLoaded = true; i.res = res;
  return i;
};
const form = (o = {}) => ({ responderId: "u1", unit: "BFP", resources: ["Water tanker", "Fire truck"], ...o });
const counter = () => { let n = 0; return () => `id${++n}`; };

beforeEach(() => { fake.reset(); I.length = 0; });

// ---------- the id ----------

test("the session makes its id once and keeps it", () => {
  const s = createAssignSession(counter());
  assert.equal(s.id, "id1");
  assert.equal(s.id, "id1");
});

test("renew() gives a new id, and only then", () => {
  const s = createAssignSession(counter());
  assert.equal(s.renew(), "id2");
  assert.equal(s.id, "id2");
});

// ---------- a field is wrong: nothing is sent, the error goes under that field ----------

test("nothing chosen: nothing is sent and each field gets its own message", async () => {
  let sent = 0;
  const r = await submitAssignment({
    incident: incident(), form: form({ responderId: "", unit: "" }), responders: RESPONDERS,
    session: createAssignSession(counter()), save: async () => { sent++; return { ok: true }; }, refresh: async () => {}
  });
  assert.equal(sent, 0);
  assert.equal(r.kind, "invalid");
  const v = resultView(r);
  assert.equal(v.errors.responder, MSG.responderRequired);
  assert.equal(v.errors.unit, MSG.unitRequired);
  assert.equal(v.errors.general, "");
  assert.equal(v.focus, "responder");
  assert.equal(v.button, SAVE_LABEL.save);
});

test("only the unit missing: the focus goes to the unit", async () => {
  const r = await submitAssignment({
    incident: incident(), form: form({ unit: "" }), responders: RESPONDERS,
    session: createAssignSession(counter()), save: async () => ({ ok: true }), refresh: async () => {}
  });
  const v = resultView(r);
  assert.equal(v.errors.responder, "");
  assert.equal(v.errors.unit, MSG.unitRequired);
  assert.equal(v.focus, "unit");
});

test("a responder who is already assigned is refused under the responder field", async () => {
  const r = await submitAssignment({
    incident: incident([["u1", "BFP", "Ana Cruz", []]]), form: form(), responders: RESPONDERS,
    session: createAssignSession(counter()), save: async () => ({ ok: true }), refresh: async () => {}
  });
  assert.equal(resultView(r).errors.responder, MSG.alreadyAssigned);
});

test("a closed report puts the message under the buttons, not under a field", async () => {
  const closed = incident(); closed.rawStatus = "resolved";
  const r = await submitAssignment({
    incident: closed, form: form(), responders: RESPONDERS,
    session: createAssignSession(counter()), save: async () => ({ ok: true }), refresh: async () => {}
  });
  const v = resultView(r);
  assert.equal(v.errors.general, MSG.closed);
  assert.equal(v.errors.responder, "");
  assert.equal(v.errors.unit, "");
});

test("resultView always answers all three error slots, so an old message never stays behind", () => {
  for (const r of [
    { kind: "invalid", errors: { responder: "x", unit: null, general: null } },
    { kind: "saved" }, { kind: "failed", text: "t" }, { kind: "blocked", text: "t" }, { kind: "stale", text: "t", lock: false }
  ]) assert.deepEqual(Object.keys(resultView(r).errors).sort(), Object.keys(ERROR_IDS).sort());
});

// ---------- saving ----------

test("a valid form is sent once: the exact payload and the session's id", async () => {
  const calls = [];
  const r = await submitAssignment({
    incident: incident(), form: form(), responders: RESPONDERS, session: createAssignSession(counter()),
    save: async (payload, id) => { calls.push({ payload, id }); return { ok: true }; }, refresh: async () => {}
  });
  assert.deepEqual(r, { kind: "saved", already: false });
  assert.equal(calls.length, 1);
  assert.equal(calls[0].id, "id1");
  assert.deepEqual(calls[0].payload, { responderId: "u1", responderName: "Ana Cruz", unit: "BFP", assignedResources: ["Fire truck", "Water tanker"] });
});

test("saved: the dialog closes with the 'Responder assigned.' toast and no error", () => {
  const v = resultView({ kind: "saved", already: false });
  assert.equal(v.close, true);
  assert.equal(v.toast, MSG.done);
  assert.deepEqual(v.errors, { responder: "", unit: "", general: "" });
});

test("an earlier try that had already landed counts as saved (already: true)", async () => {
  const r = await submitAssignment({
    incident: incident(), form: form(), responders: RESPONDERS, session: createAssignSession(counter()),
    save: async () => ({ ok: true, already: true }), refresh: async () => {}
  });
  assert.deepEqual(r, { kind: "saved", already: true });
  assert.equal(resultView(r).close, true);
});

// ---------- failing, and Try again ----------

const failing = code => async () => { throw Object.assign(new Error("x"), { code }); };

test("each way a save can fail gets its message under the buttons, and the button says Try again", async () => {
  const cases = [["unavailable", SAVE_TEXT.offline], ["permission-denied", SAVE_TEXT.denied],
    ["unauthenticated", SAVE_TEXT.expired], ["something-new", SAVE_TEXT.other]];
  for (const [code, text] of cases) {
    const r = await submitAssignment({
      incident: incident(), form: form(), responders: RESPONDERS, session: createAssignSession(counter()),
      save: failing(code), refresh: async () => {}
    });
    assert.equal(r.kind, "failed");
    const v = resultView(r);
    assert.equal(v.errors.general, text, code);
    assert.equal(v.button, SAVE_LABEL.retry, code);
    assert.ok(!v.close && !v.lock, code);
  }
});

test("Try again reuses the SAME assignment id", async () => {
  const session = createAssignSession(counter());
  const ids = [];
  const tries = [failing("unavailable"), failing("deadline-exceeded"), async () => ({ ok: true })];
  for (const t of tries) {
    await submitAssignment({
      incident: incident(), form: form(), responders: RESPONDERS, session,
      save: async (p, id) => { ids.push(id); return t(); }, refresh: async () => {}
    });
  }
  assert.deepEqual(ids, ["id1", "id1", "id1"]);
});

test("changing the form after a failure still keeps the id", async () => {
  const session = createAssignSession(counter());
  const ids = [];
  const save = async (p, id) => { ids.push(id); if (ids.length === 1) throw Object.assign(new Error("x"), { code: "unavailable" }); return { ok: true }; };
  await submitAssignment({ incident: incident(), form: form(), responders: RESPONDERS, session, save, refresh: async () => {} });
  await submitAssignment({ incident: incident(), form: form({ responderId: "u2", unit: "BDRRMO" }), responders: RESPONDERS, session, save, refresh: async () => {} });
  assert.deepEqual(ids, ["id1", "id1"]);
});

test("a request that was not valid says so and can be tried again", async () => {
  const r = await submitAssignment({
    incident: incident(), form: form(), responders: RESPONDERS, session: createAssignSession(counter()),
    save: async () => ({ blocked: true }), refresh: async () => {}
  });
  assert.equal(r.kind, "blocked");
  const v = resultView(r);
  assert.equal(v.errors.general, SAVE_TEXT.other);
  assert.equal(v.button, SAVE_LABEL.retry);
});

// ---------- a different assignment is already stored under the id ----------

test("stale: the assignments are re-read, the id is renewed, and a new save is allowed", async () => {
  const session = createAssignSession(counter());
  let refreshed = 0;
  const r = await submitAssignment({
    incident: incident(), form: form(), responders: RESPONDERS, session,
    save: async () => ({ stale: true }), refresh: async () => { refreshed++; }
  });
  assert.equal(refreshed, 1);
  assert.equal(session.id, "id2");
  assert.deepEqual(r, { kind: "stale", text: MSG.staleRefreshed, lock: false });
  const v = resultView(r);
  assert.equal(v.errors.general, MSG.staleRefreshed);
  assert.ok(!v.lock);
});

test("stale and the re-read failed: Save is locked, the dialog says Close, and the id is left alone", async () => {
  const session = createAssignSession(counter());
  const r = await submitAssignment({
    incident: incident(), form: form(), responders: RESPONDERS, session,
    save: async () => ({ stale: true }), refresh: async () => { throw new Error("offline"); }
  });
  assert.equal(session.id, "id1");
  assert.deepEqual(r, { kind: "stale", text: MSG.staleClosed, lock: true });
  const v = resultView(r);
  assert.equal(v.lock, true);
  assert.equal(v.cancel, "Close");
});

// ---------- the markup ----------

test("every id the screen paints into exists in the dialog", () => {
  const html = assignDialogHtml(incident(), USERS);
  for (const sel of Object.values(ERROR_IDS)) assert.ok(html.includes(`id="${sel.slice(1)}"`), sel);
  assert.ok(html.includes('id="asv"'));
});

// ---------- through the real saveAssignment and a fake Firestore ----------

const assignmentsAt = () => Object.keys(fake.store).filter(k => k.startsWith(`incidents/${INC}/assignments/`));

test("REAL: a save that failed offline, then Try again, ends with exactly one assignment", async () => {
  const local = incident(); I.push(local);
  const session = createAssignSession(() => newAssignmentId(INC));
  const attempt = () => submitAssignment({
    incident: local, form: form(), responders: RESPONDERS, session,
    save: (p, id) => saveAssignment(INC, id, p, local), refresh: () => refreshAssignments(INC, local)
  });

  fake.failNextSet(Object.assign(new Error("offline"), { code: "unavailable" }));
  assert.equal((await attempt()).kind, "failed");
  assert.equal(assignmentsAt().length, 0);

  assert.deepEqual(await attempt(), { kind: "saved", already: false });
  assert.equal(assignmentsAt().length, 1);
  assert.deepEqual(local.res.map(r => r[0]), ["u1"]);
});

test("REAL: the first try reached the server but its answer was lost: Try again does NOT add a second one", async () => {
  const local = incident(); I.push(local);
  const session = createAssignSession(() => newAssignmentId(INC));
  // The earlier try already wrote the document (as the server would have), then the answer was lost.
  fake.store[`incidents/${INC}/assignments/${session.id}`] = {
    responderId: "u1", responderName: "Ana Cruz", unit: "BFP", assignedResources: ["Fire truck", "Water tanker"], assignedAt: {}
  };
  const r = await submitAssignment({
    incident: local, form: form(), responders: RESPONDERS, session,
    save: (p, id) => saveAssignment(INC, id, p, local), refresh: () => refreshAssignments(INC, local)
  });
  assert.deepEqual(r, { kind: "saved", already: true });
  assert.equal(assignmentsAt().length, 1);
  assert.equal(fake.calls.filter(c => c.op === "set").length, 0);    // nothing was written again
  assert.deepEqual(local.res.map(r => r[0]), ["u1"]);
});

test("REAL: lost answer, then a different responder is chosen: the earlier one is shown and the new save is a second, separate assignment", async () => {
  const local = incident(); I.push(local);
  const session = createAssignSession(() => newAssignmentId(INC));
  const firstId = session.id;
  fake.store[`incidents/${INC}/assignments/${firstId}`] = {
    responderId: "u1", responderName: "Ana Cruz", unit: "BFP", assignedResources: [], assignedAt: {}
  };
  const attempt = f => submitAssignment({
    incident: local, form: f, responders: RESPONDERS, session,
    save: (p, id) => saveAssignment(INC, id, p, local), refresh: () => refreshAssignments(INC, local)
  });

  const stale = await attempt(form({ responderId: "u2", unit: "BDRRMO", resources: [] }));
  assert.equal(stale.kind, "stale");
  assert.notEqual(session.id, firstId);                              // the id was renewed
  assert.deepEqual(local.res.map(r => r[0]), ["u1"]);                // the earlier assignment is now shown

  // The same person can no longer be chosen again ...
  const again = await attempt(form({ responderId: "u1" }));
  assert.equal(again.kind, "invalid");
  assert.equal(resultView(again).errors.responder, MSG.alreadyAssigned);

  // ... but the other one can, and it lands under the new id.
  assert.deepEqual(await attempt(form({ responderId: "u2", unit: "BDRRMO", resources: [] })), { kind: "saved", already: false });
  assert.equal(assignmentsAt().length, 2);
  assert.deepEqual(local.res.map(r => r[0]).sort(), ["u1", "u2"]);
});

test("REAL: only the allowed fields are written, with the server time", async () => {
  const local = incident(); I.push(local);
  const session = createAssignSession(() => newAssignmentId(INC));
  await submitAssignment({
    incident: local, form: form(), responders: RESPONDERS, session,
    save: (p, id) => saveAssignment(INC, id, p, local), refresh: () => refreshAssignments(INC, local)
  });
  const [call] = fake.calls.filter(c => c.op === "set");
  assert.deepEqual(Object.keys(call.data).sort(), ["assignedAt", "assignedResources", "responderId", "responderName", "unit"]);
  assert.deepEqual(call.data.assignedAt, { __serverTimestamp: true });
});

test("a name with an apostrophe and an ampersand is saved as typed, not escaped", async () => {
  const odd = [{ ...user("u3", "O'Brien & Sons"), name: "O'Brien & Sons", fullName: "O&#39;Brien &amp; Sons" }];
  const local = incident(); I.push(local);
  const session = createAssignSession(() => newAssignmentId(INC));
  await submitAssignment({
    incident: local, form: form({ responderId: "u3" }), responders: responderOptions(odd), session,
    save: (p, id) => saveAssignment(INC, id, p, local), refresh: () => refreshAssignments(INC, local)
  });
  const [call] = fake.calls.filter(c => c.op === "set");
  assert.equal(call.data.responderName, "O'Brien & Sons");
});

// ---------- refreshAssignments ----------

// The cache lives for the whole module, so each of these uses its own incident id (as assignmentCache.test.mjs).
let n = 0;
const seedIncident = () => {
  const id = `rfr${++n}`;
  fake.store[`incidents/${id}`] = { status: "verified", severity: "high", submittedAt: new Date(500), updatedAt: new Date(1000) };
  return id;
};

test("refreshAssignments replaces the screen's copy with what the server holds", async () => {
  const id = seedIncident();
  await loadIncidentsOnce();
  const local = I.find(i => i.id === id);
  local.res = [["old", "BFP", "Gone", []]]; local.assignLoaded = true;
  fake.store[`incidents/${id}/assignments/a1`] = { responderId: "u1", responderName: "Ana Cruz", unit: "BFP", assignedResources: ["Fire truck"] };

  const res = await refreshAssignments(id);
  assert.deepEqual(res.map(r => r[0]), ["u1"]);
  assert.deepEqual(local.res.map(r => r[0]), ["u1"]);
  assert.equal(local.assignLoaded, true);
});

test("refreshAssignments also updates the cache: a rebuilt list shows the new assignment, with no extra read", async () => {
  const id = seedIncident();
  await loadIncidentsOnce();
  fake.store[`incidents/${id}/assignments/a1`] = { responderId: "u1", responderName: "Ana Cruz", unit: "BFP", assignedResources: [] };
  await refreshAssignments(id);

  fake.reads.length = 0;
  await loadIncidentsOnce();                         // what the live listener does on every change
  const rebuilt = I.find(i => i.id === id);
  assert.equal(rebuilt.assignLoaded, true);
  assert.deepEqual(rebuilt.res.map(r => r[0]), ["u1"]);
  assert.equal(fake.reads.filter(r => r.path === `incidents/${id}/assignments`).length, 0);
});

test("refreshAssignments throws when the read fails, and the screen's copy is left as it was", async () => {
  const id = seedIncident();
  await loadIncidentsOnce();
  const local = I.find(i => i.id === id);
  local.res = [["u1", "BFP", "Ana Cruz", []]]; local.assignLoaded = true;
  const before = local.res;

  fake.failNextList(Object.assign(new Error("offline"), { code: "unavailable" }));
  await assert.rejects(() => refreshAssignments(id), { code: "unavailable" });
  assert.equal(local.res, before);
  assert.deepEqual(local.res.map(r => r[0]), ["u1"]);
});
