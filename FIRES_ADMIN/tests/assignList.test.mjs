// The responder list in the Assign dialog (W13.16).
//   node --test tests/assignList.test.mjs
import test from "node:test";
import assert from "node:assert/strict";
import { mapUser, mapIncident } from "../js/mapping.js";
import { availableResponders, assignDialogHtml, MSG } from "../js/assignRules.js";

const user = (id, o = {}) => mapUser(id, { fullName: "Ana Cruz", email: "ana@x.ph", address: "BFP", role: "responder", ...o });
// res rows are [responderId, unit, responderName, resources], as mapAssignment makes them
const incident = (res = [], o = {}) => Object.assign(mapIncident("abc123xyz", { status: "verified", addressText: "12 Rizal St.", ...o }), { res, assignLoaded: true });
// The options of the responder select only (the dialog also has a unit select since W13.17).
const options = html => {
  const sel = html.match(/<select id="ar">([\s\S]*?)<\/select>/);
  return sel ? [...sel[1].matchAll(/<option value="([^"]*)">([^<]*)<\/option>/g)].map(m => [m[1], m[2]]) : [];
};

// ---------- the list ----------

test("only accounts with the responder role are offered, sorted by name", () => {
  const users = [user("c1", { fullName: "Carla", role: "citizen" }), user("r2", { fullName: "Ben Reyes" }),
    user("a1", { fullName: "Admin Person", role: "admin" }), user("r1", { fullName: "Ana Cruz" })];
  assert.deepEqual(availableResponders(incident(), users).map(r => r.id), ["r1", "r2"]);
});

test("responders already assigned to this incident are left out", () => {
  const users = [user("r1"), user("r2", { fullName: "Ben Reyes" }), user("r3", { fullName: "Cora Diaz" })];
  const res = [["r2", "BFP", "Ben Reyes", []]];
  assert.deepEqual(availableResponders(incident(res), users).map(r => r.id), ["r1", "r3"]);
});

test("being assigned to ANOTHER incident does not matter here", () => {
  const users = [user("r1")];
  const other = incident([["r1", "BFP", "Ana Cruz", []]], {});
  assert.equal(availableResponders(incident([]), users).length, 1);
  assert.equal(availableResponders(other, users).length, 0);
});

test("the list is built from the users it is given: no users, or nobody a responder, gives an empty list", () => {
  assert.deepEqual(availableResponders(incident(), []), []);
  assert.deepEqual(availableResponders(incident(), null), []);
  assert.deepEqual(availableResponders(incident(), [user("c1", { role: "citizen" })]), []);
});

test("it does not change the users or the incident it reads", () => {
  const users = [user("r1"), user("r2", { fullName: "Ben" })];
  const i = incident([["r2", "BFP", "Ben", []]]);
  const before = JSON.stringify([users, i]);
  availableResponders(i, users);
  assert.equal(JSON.stringify([users, i]), before);
});

// ---------- the dialog ----------

test("the dialog names the report and offers a 'Choose a responder' list of the ones left", () => {
  const html = assignDialogHtml(incident(), [user("r1"), user("r2", { fullName: "Ben Reyes" })]);
  assert.match(html, /<h2 id="dlg-t">Assign responder<\/h2>/);
  assert.match(html, /INC-ABC123/);
  assert.match(html, /12 Rizal St\./);
  assert.match(html, /<select id="ar">/);
  assert.deepEqual(options(html), [["", "Choose a responder"], ["r1", "Ana Cruz"], ["r2", "Ben Reyes"]]);
  assert.doesNotMatch(html, /ar-none/);
});

test("an assigned responder is not in the dialog's list", () => {
  const html = assignDialogHtml(incident([["r1", "BFP", "Ana Cruz", []]]), [user("r1"), user("r2", { fullName: "Ben Reyes" })]);
  assert.deepEqual(options(html).map(o => o[0]), ["", "r2"]);
});

test("nobody left to choose: a short message instead of an empty list", () => {
  const all = assignDialogHtml(incident([["r1", "BFP", "Ana Cruz", []]]), [user("r1")]);
  assert.match(all, /<p class="muted" id="ar-none">Every responder is already assigned to this incident\.<\/p>/);
  assert.doesNotMatch(all, /<select/);
  const none = assignDialogHtml(incident(), [user("c1", { role: "citizen" })]);
  assert.match(none, new RegExp(`id="ar-none">${MSG.noResponders.replace(".", "\\.")}`));
  assert.doesNotMatch(none, /<select/);
  assert.notEqual(MSG.noResponders, MSG.allAssigned);
});

test("the messages are short, plain and name nobody", () => {
  for (const m of [MSG.noResponders, MSG.allAssigned]) assert.ok(m.length < 90 && !/[<>&]/.test(m), m);
});

test("names are escaped in the list, but the value is the account id, not the name", () => {
  const html = assignDialogHtml(incident(), [user("r1", { fullName: `O'Brien & <Sons>` })]);
  assert.match(html, /<option value="r1">O&#39;Brien &amp; &lt;Sons&gt;<\/option>/);
  assert.ok(!html.includes("<Sons>"));
});

test("a location written by a citizen is escaped (it comes out of mapIncident)", () => {
  const html = assignDialogHtml(incident([], { addressText: "<script>alert(1)</script>" }), [user("r1")]);
  assert.ok(!html.includes("<script>"));
});

test("the dialog keeps the ids the shared dialog code and the next steps use", () => {
  const withList = assignDialogHtml(incident(), [user("r1")]);
  const empty = assignDialogHtml(incident(), []);
  for (const html of [withList, empty]) {
    assert.match(html, /id="dlg-t"/);
    assert.match(html, /<div class="ferr" id="dg" role="alert"><\/div>/);
    assert.match(html, /id="dno"/);
  }
  assert.match(withList, /<span class="ferr" id="ar-e"><\/span>/);
});

// W13.18: with someone to choose there is Cancel + Save; with nobody there is only Close (nothing to save).
test("with a list the buttons are Cancel and Save; with nobody left there is only Close", () => {
  const withList = assignDialogHtml(incident(), [user("r1")]);
  const empty = assignDialogHtml(incident(), []);
  assert.match(withList, /<button class="btn ghost" id="dno">Cancel<\/button><button class="btn" id="asv">Save<\/button>/);
  assert.match(empty, /<button class="btn ghost" id="dno">Close<\/button>/);
  assert.ok(!empty.includes('id="asv"'));
});
