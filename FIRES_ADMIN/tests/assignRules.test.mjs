// Run from FIRES_ADMIN with:  node --test tests/assignRules.test.mjs
import test from "node:test";
import assert from "node:assert/strict";
import { mapUser, mapIncident, mapAssignment } from "../js/mapping.js";
import {
  UNITS, RESOURCES, MSG, ASSIGNMENT_KEYS, canAssign, unesc, responderOptions, defaultUnit,
  isAssigned, orderedResources, validateAssignment, buildAssignment, assignmentRow,
  isAssignmentPayload, sameAssignment, addAssignment
} from "../js/assignRules.js";

const user = (id, o = {}) => mapUser(id, { fullName: "Ana Cruz", email: "ana@x.ph", address: "BFP", role: "responder", ...o });
const incident = (o = {}, res = []) => {
  const i = mapIncident("abc123xyz", { status: "verified", ...o });
  i.res = res; i.assignLoaded = true;
  return i;
};
const responders = responderOptions([user("u1"), user("u2", { fullName: "Ben Reyes", address: "BDRRMO" })]);
const good = { responderId: "u1", unit: "BFP", resources: ["Water tanker", "Fire truck"] };

test("lists match the app", () => {
  assert.deepEqual(UNITS, ["B-FLARE", "BDRRMO", "BFP", "Neighboring barangay"]);
  assert.deepEqual(RESOURCES, ["Fire truck", "Water tanker"]);
});

test("canAssign: active statuses only, by stored status", () => {
  for (const s of ["reported", "verified", "dispatched", "on_scene"]) assert.equal(canAssign(incident({ status: s })), true, s);
  for (const s of ["resolved", "dismissed"]) assert.equal(canAssign(incident({ status: s })), false, s);
  assert.equal(canAssign(null), false);
  // a label that merely looks active must not count: stored status decides
  assert.equal(canAssign({ rawStatus: "dismissed", status: "Ongoing", active: true }), false);
});

test("responderOptions: responders only, sorted, with plain name and escaped label", () => {
  const list = responderOptions([
    user("b", { fullName: "Zed" }), user("a", { fullName: "O'Brien <b>" }),
    user("c", { role: "citizen" }), user("d", { role: "admin" })
  ]);
  assert.deepEqual(list.map(r => r.id), ["a", "b"]);
  assert.equal(list[0].name, "O'Brien <b>");          // saved as typed
  assert.equal(list[0].label, "O&#39;Brien &lt;b&gt;"); // safe for HTML
});

test("responderOptions: name falls back to e-mail, then 'Responder', and is capped at 100", () => {
  assert.equal(responderOptions([user("a", { fullName: "  " })])[0].name, "ana@x.ph");
  assert.equal(responderOptions([user("a", { fullName: "", email: "" })])[0].name, "Responder");
  assert.equal(responderOptions([user("a", { fullName: "x".repeat(150) })])[0].name.length, 100);
  assert.deepEqual(responderOptions(null), []);
});

test("unesc undoes the screens' escaping exactly", () => {
  for (const s of ["O'Brien", "A & B", "<i>", '"q"', "&lt;", "&amp;", "&#39;", "plain"]) {
    assert.equal(unesc(mapUser("x", { fullName: s }).fullName), s);
  }
});

test("defaultUnit: from the stored unit, ignoring case and spaces", () => {
  assert.equal(defaultUnit(user("u", { address: "B-FLARE" })), "B-FLARE");
  assert.equal(defaultUnit(user("u", { address: "  bfp " })), "BFP");
  assert.equal(defaultUnit(user("u", { address: "neighboring BARANGAY" })), "Neighboring barangay");
  assert.equal(defaultUnit(user("u", { address: "12 Rizal St., Purok 3" })), "");
  assert.equal(defaultUnit(user("u", { address: "" })), "");
  assert.equal(defaultUnit(null), "");
  assert.equal(responders.find(r => r.id === "u2").unit, "BDRRMO");
});

test("isAssigned reads the responder id of each assignment", () => {
  const i = incident({}, [mapAssignment({ responderId: "u1", unit: "BFP" })]);
  assert.equal(isAssigned(i, "u1"), true);
  assert.equal(isAssigned(i, "u2"), false);
});

test("orderedResources: list order, unknown dropped, optional", () => {
  assert.deepEqual(orderedResources(["Water tanker", "Fire truck"]), ["Fire truck", "Water tanker"]);
  assert.deepEqual(orderedResources(new Set(["Helicopter", "Fire truck"])), ["Fire truck"]);
  assert.deepEqual(orderedResources([]), []);
  assert.deepEqual(orderedResources(undefined), []);
});

test("valid form passes and cleans to the four fields", () => {
  const v = validateAssignment(incident(), good, responders);
  assert.equal(v.ok, true);
  assert.deepEqual(v.clean, {
    responderId: "u1", responderName: "Ana Cruz", unit: "BFP", assignedResources: ["Fire truck", "Water tanker"]
  });
});

test("resources are optional", () => {
  const v = validateAssignment(incident(), { responderId: "u1", unit: "BFP" }, responders);
  assert.equal(v.ok, true);
  assert.deepEqual(v.clean.assignedResources, []);
});

test("responder is required and must be one of the loaded responders", () => {
  assert.equal(validateAssignment(incident(), { ...good, responderId: "" }, responders).errors.responder, MSG.responderRequired);
  assert.equal(validateAssignment(incident(), { ...good, responderId: undefined }, responders).errors.responder, MSG.responderRequired);
  assert.equal(validateAssignment(incident(), { ...good, responderId: "citizen9" }, responders).errors.responder, MSG.responderUnknown);
});

test("unit is required and must be from UNITS", () => {
  for (const unit of ["", undefined, "Somebody else", "bfp"]) {
    const v = validateAssignment(incident(), { ...good, unit }, responders);
    assert.equal(v.errors.unit, MSG.unitRequired, String(unit));
    assert.equal(v.ok, false);
  }
  for (const unit of UNITS) assert.equal(validateAssignment(incident(), { ...good, unit }, responders).errors.unit, null);
});

test("a responder who is already assigned is refused", () => {
  const i = incident({}, [mapAssignment({ responderId: "u1", unit: "BDRRMO" })]);
  const v = validateAssignment(i, good, responders);
  assert.equal(v.ok, false);
  assert.equal(v.errors.responder, MSG.alreadyAssigned);
  assert.equal(v.clean, null);
  // someone else is still fine
  assert.equal(validateAssignment(i, { ...good, responderId: "u2" }, responders).ok, true);
});

test("a closed incident is refused", () => {
  for (const status of ["resolved", "dismissed"]) {
    const v = validateAssignment(incident({ status }), good, responders);
    assert.equal(v.ok, false);
    assert.equal(v.errors.general, MSG.closed);
  }
});

test("assignments not loaded yet: refused, so the duplicate check cannot be skipped", () => {
  const i = incident(); i.assignLoaded = false;
  const v = validateAssignment(i, good, responders);
  assert.equal(v.ok, false);
  assert.equal(v.errors.general, MSG.notLoaded);
});

test("errors for several problems are reported together", () => {
  const v = validateAssignment(incident(), { responderId: "", unit: "" }, responders);
  assert.equal(v.errors.responder, MSG.responderRequired);
  assert.equal(v.errors.unit, MSG.unitRequired);
});

test("buildAssignment: exactly the four fields, plus assignedAt only when passed", () => {
  const clean = validateAssignment(incident(), good, responders).clean;
  const noTime = buildAssignment(clean);
  assert.deepEqual(Object.keys(noTime).sort(), ["assignedResources", "responderId", "responderName", "unit"]);
  const stamp = { server: "timestamp" };
  const withTime = buildAssignment(clean, stamp);
  assert.deepEqual(Object.keys(withTime).sort(), [...ASSIGNMENT_KEYS].sort());
  assert.equal(withTime.assignedAt, stamp);
  assert.equal(buildAssignment(null, stamp), null);
});

test("buildAssignment copies the resources, and a name with quotes is saved plain", () => {
  const list = responderOptions([user("q", { fullName: "O'Brien" })]);
  const v = validateAssignment(incident(), { responderId: "q", unit: "BFP", resources: ["Fire truck"] }, list);
  const data = buildAssignment(v.clean);
  assert.equal(data.responderName, "O'Brien");
  data.assignedResources.push("x");
  assert.deepEqual(v.clean.assignedResources, ["Fire truck"]);
});

test("assignmentRow has the shape mapAssignment produces", () => {
  const clean = validateAssignment(incident(), good, responders).clean;
  assert.deepEqual(assignmentRow(clean), mapAssignment(buildAssignment(clean)));
  assert.equal(assignmentRow(null), null);
});

test("NOTHING ELSE is written: exactly the four fields (five with the server time)", () => {
  const clean = validateAssignment(incident(), good, responders).clean;
  assert.deepEqual(Object.keys(buildAssignment(clean)).sort(),
    ["assignedResources", "responderId", "responderName", "unit"]);
  assert.deepEqual(Object.keys(buildAssignment(clean, { server: "timestamp" })).sort(),
    ["assignedAt", "assignedResources", "responderId", "responderName", "unit"]);
});

test("extra form fields never reach the payload", () => {
  const form = { ...good, role: "admin", status: "resolved", severity: "low", assignedAt: "2020-01-01", responderName: "Hacker", isAdmin: true };
  const v = validateAssignment(incident(), form, responders);
  assert.equal(v.ok, true);
  const data = buildAssignment(v.clean);
  assert.deepEqual(data, { responderId: "u1", responderName: "Ana Cruz", unit: "BFP", assignedResources: ["Fire truck", "Water tanker"] });
});

test("extra fields on the clean object are not copied either", () => {
  const clean = { ...validateAssignment(incident(), good, responders).clean, role: "admin", assignedAt: "x" };
  assert.deepEqual(Object.keys(buildAssignment(clean)).sort(),
    ["assignedResources", "responderId", "responderName", "unit"]);
});

test("a name typed into the form cannot replace the chosen account's name", () => {
  const v = validateAssignment(incident(), { ...good, responderName: "Someone else" }, responders);
  assert.equal(v.clean.responderName, "Ana Cruz");
});

test("only values the rules accept: strings, a list of strings, at most 100 / 50 / 20", () => {
  const long = responderOptions([user("L", { fullName: "x".repeat(300) })]);
  const v = validateAssignment(incident(), { responderId: "L", unit: "Neighboring barangay", resources: RESOURCES }, long);
  const d = buildAssignment(v.clean);
  assert.equal(typeof d.responderId, "string");
  assert.ok(d.responderName.length <= 100);
  assert.ok(d.unit.length >= 1 && d.unit.length <= 50);
  assert.ok(Array.isArray(d.assignedResources) && d.assignedResources.length <= 20);
  assert.ok(d.assignedResources.every(r => typeof r === "string"));
});

test("isAssignmentPayload accepts what buildAssignment makes, and refuses anything else", () => {
  const clean = validateAssignment(incident(), good, responders).clean;
  assert.equal(isAssignmentPayload(buildAssignment(clean)), true);
  assert.equal(isAssignmentPayload({ ...clean, assignedResources: [] }), true);
  for (const bad of [null, undefined, "x", 5, {}, { ...clean, unit: "bfp" }, { ...clean, responderId: "" },
    { ...clean, responderName: "" }, { ...clean, assignedResources: ["Helicopter"] },
    { ...clean, assignedResources: ["Fire truck", "Fire truck"] }, { ...clean, assignedResources: undefined }]) {
    assert.equal(isAssignmentPayload(bad), false, JSON.stringify(bad));
  }
});

test("sameAssignment compares responder, unit and resources, not the order resources were stored in", () => {
  const p = { responderId: "u1", unit: "BFP", assignedResources: ["Fire truck", "Water tanker"] };
  assert.equal(sameAssignment({ ...p, assignedAt: 1, responderName: "x" }, p), true);
  assert.equal(sameAssignment({ ...p, assignedResources: ["Water tanker", "Fire truck"] }, p), true);
  assert.equal(sameAssignment({ ...p, responderId: "u2" }, p), false);
  assert.equal(sameAssignment({ ...p, unit: "BDRRMO" }, p), false);
  assert.equal(sameAssignment({ ...p, assignedResources: ["Fire truck"] }, p), false);
  assert.equal(sameAssignment({ responderId: "u1", unit: "BFP" }, { ...p, assignedResources: [] }), true);
  assert.equal(sameAssignment(null, p), false);
});

test("addAssignment lists the responder once and never twice", () => {
  const i = incident();
  const clean = validateAssignment(i, good, responders).clean;
  assert.equal(addAssignment(i, clean), i);
  assert.equal(i.res.length, 1);
  addAssignment(i, clean);
  assert.equal(i.res.length, 1);
  assert.deepEqual(i.res[0], assignmentRow(clean));
  assert.equal(addAssignment(null, clean), null);
  assert.equal(addAssignment(i, null), i);
});
