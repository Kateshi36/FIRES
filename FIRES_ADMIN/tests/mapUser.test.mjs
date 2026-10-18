// Run from FIRES_ADMIN with:  node --test tests/mapUser.test.mjs
import test from "node:test";
import assert from "node:assert/strict";
import { mapUser } from "../js/mapping.js";
import { responderOptions } from "../js/assignRules.js";

const base = { fullName: "Ana Cruz", email: "ana@x.ph", contactNo: "09171234567", address: "BFP", purok: "3", role: "responder" };

test("mapUser keeps the raw name next to the escaped one", () => {
  const u = mapUser("u1", { ...base, fullName: `O'Brien & <Sons> "Jr"` });
  assert.equal(u.name, `O'Brien & <Sons> "Jr"`);                       // as stored, for saving
  assert.equal(u.fullName, "O&#39;Brien &amp; &lt;Sons&gt; &quot;Jr&quot;"); // for the screens
});

test("a plain name is the same in both", () => {
  const u = mapUser("u1", base);
  assert.equal(u.name, "Ana Cruz");
  assert.equal(u.fullName, "Ana Cruz");
});

test("a missing or odd name becomes text, never undefined or 'null'", () => {
  assert.equal(mapUser("u1", { ...base, fullName: undefined }).name, "");
  assert.equal(mapUser("u1", { ...base, fullName: null }).name, "");
  assert.equal(mapUser("u1", { ...base, fullName: 42 }).name, "42");
});

test("the other fields are unchanged", () => {
  const u = mapUser("u1", { ...base, email: "a&b@x.ph", role: undefined });
  assert.deepEqual(Object.keys(u).sort(), ["address", "contactNo", "email", "fullName", "id", "name", "purok", "role"]);
  assert.equal(u.id, "u1");
  assert.equal(u.email, "a&amp;b@x.ph");
  assert.equal(u.role, "citizen");
});

test("responderOptions saves the raw name, not an un-escaped copy", () => {
  const [o] = responderOptions([mapUser("u1", { ...base, fullName: "O'Brien" })]);
  assert.equal(o.name, "O'Brien");
  assert.equal(o.label, "O&#39;Brien");
});

test("responderOptions prefers `name` over fullName", () => {
  const [o] = responderOptions([{ id: "u1", role: "responder", name: "Raw Name", fullName: "Other", email: "" }]);
  assert.equal(o.name, "Raw Name");
});

test("responderOptions still works for a user object without `name`", () => {
  const [o] = responderOptions([{ id: "u1", role: "responder", fullName: "O&#39;Brien", email: "" }]);
  assert.equal(o.name, "O'Brien");
});

test("a blank raw name falls back to the e-mail", () => {
  const [o] = responderOptions([mapUser("u1", { ...base, fullName: "   " })]);
  assert.equal(o.name, "ana@x.ph");
});
