// The unit select and the resource checkboxes in the Assign dialog (W13.17).
//   node --test tests/assignFields.test.mjs
import test from "node:test";
import assert from "node:assert/strict";
import { mapUser, mapIncident } from "../js/mapping.js";
import { UNITS, RESOURCES, responderOptions, assignDialogHtml, unitFor } from "../js/assignRules.js";

const user = (id, o = {}) => mapUser(id, { fullName: "Ana Cruz", email: "ana@x.ph", address: "BFP", role: "responder", ...o });
const incident = () => Object.assign(mapIncident("abc123xyz", { status: "verified" }), { res: [], assignLoaded: true });
const html = (users = [user("r1")]) => assignDialogHtml(incident(), users);
const unitOptions = h => [...h.match(/<select id="au">([\s\S]*?)<\/select>/)[1].matchAll(/<option value="([^"]*)"( selected)?>([^<]*)<\/option>/g)].map(m => [m[1], !!m[2], m[3]]);
const boxes = h => [...h.matchAll(/<input type="checkbox" value="([^"]*)"([^>]*)>([^<]*)<\/label>/g)].map(m => ({ value: m[1], checked: /checked/.test(m[2]), text: m[3] }));

// ---------- the unit select ----------

test("the unit select lists 'Choose a unit' first, then the app's units in order, with nothing selected", () => {
  assert.deepEqual(unitOptions(html()), [["", false, "Choose a unit"], ...UNITS.map(u => [u, false, u])]);
});

test("the unit select comes after the responder select, each with its own error line", () => {
  const h = html();
  assert.ok(h.indexOf('id="ar"') < h.indexOf('id="au"'));
  assert.match(h, /<span class="ferr" id="ar-e"><\/span>/);
  assert.match(h, /<span class="ferr" id="au-e"><\/span>/);
});

// ---------- preselecting the unit ----------

test("the options carry each responder's stored unit, and only a real unit counts", () => {
  const list = responderOptions([user("r1", { address: "BFP" }), user("r2", { fullName: "Ben", address: "  b-flare " }),
    user("r3", { fullName: "Cora", address: "12 Rizal St." }), user("r4", { fullName: "Dino", address: "" })]);
  assert.deepEqual(list.map(r => r.unit), ["BFP", "B-FLARE", "", ""]);
});

test("choosing a responder preselects their unit while the unit is untouched", () => {
  const [ana, ben] = responderOptions([user("r1", { address: "BFP" }), user("r2", { fullName: "Ben", address: "BDRRMO" })]);
  assert.equal(unitFor("", false, ana), "BFP");
  assert.equal(unitFor("BFP", false, ben), "BDRRMO");       // changing the responder follows along
});

test("a responder with no stored unit leaves the select on 'Choose a unit'", () => {
  const [cora] = responderOptions([user("r3", { fullName: "Cora", address: "12 Rizal St." })]);
  assert.equal(unitFor("BFP", false, cora), "");
});

test("going back to 'Choose a responder' clears the preselection", () => {
  assert.equal(unitFor("BFP", false, undefined), "");
  assert.equal(unitFor("BFP", false, null), "");
});

test("once the administrator has picked a unit, changing the responder never overwrites it", () => {
  const [ana] = responderOptions([user("r1", { address: "BFP" })]);
  assert.equal(unitFor("Neighboring barangay", true, ana), "Neighboring barangay");
  assert.equal(unitFor("", true, ana), "");                  // they chose "no unit": that is respected too
});

test("an unknown unit on a responder object is never preselected", () => {
  assert.equal(unitFor("", false, { id: "x", unit: "Fire Marshal" }), "");
});

// ---------- the resource checkboxes ----------

test("two checkboxes, Fire truck then Water tanker, both unchecked", () => {
  assert.deepEqual(boxes(html()), RESOURCES.map(r => ({ value: r, checked: false, text: r })));
  assert.equal(boxes(html()).length, 2);
});

test("the resources are marked optional, and are in a labelled group", () => {
  assert.match(html(), /<fieldset id="ares"[^>]*><legend[^>]*>Resources \(optional\)<\/legend>/);
});

test("nothing is required about the resources: no 'required' anywhere on the checkboxes", () => {
  assert.doesNotMatch(html().match(/<fieldset id="ares"[\s\S]*<\/fieldset>/)[0], /required/);
});

// ---------- when nobody can be assigned ----------

test("with no responder left there is no unit select and no checkboxes, just the message", () => {
  const h = assignDialogHtml(incident(), []);
  for (const gone of ['id="au"', 'id="ares"', "checkbox", "<select"]) assert.ok(!h.includes(gone), gone);
  assert.match(h, /id="ar-none"/);
  assert.match(h, /id="dno"/);
});
