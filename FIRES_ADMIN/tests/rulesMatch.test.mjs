// Checks the two website writes against the REAL firestore.rules, so a change on either side that
// the rules would refuse fails here instead of in the browser. Run with:
//   node --test tests/rulesMatch.test.mjs
// firestore.rules lives in the Android project (FIRES_APP). If this site is moved to its own
// repository (W15) the file is not there, and these tests are skipped.
import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync, existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { buildVerifyUpdate } from "../js/verifyRules.js";
import { ASSIGNMENT_KEYS, UNITS, buildAssignment } from "../js/assignRules.js";

const path = fileURLToPath(new URL("../../FIRES_APP/firestore.rules", import.meta.url));
const skip = existsSync(path) ? false : "firestore.rules not found next to this project";
const rules = skip ? "" : readFileSync(path, "utf8");

// ['a', 'b'] -> ["a","b"], for the first list that follows `marker`.
const listAfter = marker => {
  const at = rules.indexOf(marker);
  assert.ok(at >= 0, `not found in firestore.rules: ${marker}`);
  const m = /\[([^\]]*)\]/.exec(rules.slice(at));
  return [...m[1].matchAll(/'([^']*)'/g)].map(x => x[1]);
};

test("verify: the fields written are all ones staff may change on an incident", { skip }, () => {
  const allowed = listAfter("allow update: if isStaff()\n        && changesOnly(");
  assert.ok(allowed.includes("status") && allowed.includes("verification") && allowed.includes("updatedAt"));
  for (const kind of ["verify", "false", "duplicate"]) {
    for (const key of Object.keys(buildVerifyUpdate(kind, {}))) {
      assert.ok(allowed.includes(key), `${kind}: rules do not allow changing ${key}`);
    }
  }
});

test("verify: status and verification values are ones the rules accept", { skip }, () => {
  const statuses = listAfter("function validStatus"), verifications = listAfter("function validVerification");
  for (const kind of ["verify", "false", "duplicate"]) {
    const d = buildVerifyUpdate(kind, {});
    assert.ok(statuses.includes(d.status), `${kind}: status ${d.status}`);
    assert.ok(verifications.includes(d.verification), `${kind}: verification ${d.verification}`);
  }
});

test("assign: the allowed keys are exactly the rules' list", { skip }, () => {
  assert.deepEqual([...ASSIGNMENT_KEYS].sort(), listAfter("function validAssignmentFields").sort());
});

test("assign: a built payload only has keys the rules allow", { skip }, () => {
  const allowed = listAfter("function validAssignmentFields");
  const data = buildAssignment(
    { responderId: "u1", responderName: "Ana", unit: UNITS[0], assignedResources: ["Fire truck"] }, {});
  for (const key of Object.keys(data)) assert.ok(allowed.includes(key), key);
});

test("activity log: the fields the website writes are all ones the rules accept, and admins only", { skip }, async () => {
  const { activityDoc, ACTIVITY_TYPES } = await import("../js/viewRules.js");
  const keys = listAfter("function validActivity");
  const types = [...rules.slice(rules.indexOf("function validActivity")).match(/d\.type in \[([^\]]*)\]/)[1].matchAll(/'([^']*)'/g)].map(x => x[1]);
  for (const t of ACTIVITY_TYPES) assert.ok(types.includes(t), `rules do not accept activity type ${t}`);
  const d = activityDoc("verify", "x", { id: "a", name: "A" }, "inc1");
  for (const k of [...Object.keys(d), "createdAt"]) assert.ok(keys.includes(k), `rules do not allow ${k}`);
  const block = rules.slice(rules.indexOf("match /activity/"));
  assert.match(block, /allow read: if isAdmin\(\);/);
  assert.match(block, /allow create: if isAdmin\(\)/);
  assert.match(block, /allow update, delete: if false;/);
});
