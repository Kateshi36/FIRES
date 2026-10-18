// Run from FIRES_ADMIN with:  node --test tests/verifyRules.test.mjs
import test from "node:test";
import assert from "node:assert/strict";
import { mapIncident, isActiveStatus } from "../js/mapping.js";
import {
  VERIFY_BUTTONS, canVerify, isVerifyKind, needsConfirmation, confirmText, doneText,
  buildVerifyUpdate, outcomeOf, patchIncident
} from "../js/verifyRules.js";

const KINDS = ["verify", "false", "duplicate"];
const STAMP = { server: "timestamp" };
const report = status => mapIncident("abc123", { status });

test("three buttons, in order, with Verify as the main one", () => {
  assert.deepEqual(VERIFY_BUTTONS, [
    { kind: "verify", label: "Verify", primary: true },
    { kind: "false", label: "False report", primary: false },
    { kind: "duplicate", label: "Duplicate", primary: false }
  ]);
});

test("canVerify: only a report nobody has reviewed yet", () => {
  assert.equal(canVerify(report("reported")), true);
  for (const s of ["verified", "dispatched", "on_scene", "resolved", "dismissed"]) {
    assert.equal(canVerify(report(s)), false, s);
  }
  assert.equal(canVerify(null), false);
  assert.equal(canVerify(undefined), false);
});

test("canVerify reads the stored status, never the label", () => {
  // a status with no value is read as "reported" by the mapping, same as the app
  assert.equal(canVerify(mapIncident("x", {})), true);
  // labels that look like "reported" must not match
  assert.equal(canVerify({ rawStatus: "dismissed", status: "Reported" }), false);
  assert.equal(canVerify({ status: "Reported" }), false);
});

test("isVerifyKind: the three kinds only, not inherited object keys", () => {
  for (const k of KINDS) assert.equal(isVerifyKind(k), true, k);
  for (const k of ["", "dispatch", "resolve", "VERIFY", "toString", "constructor", "__proto__", "hasOwnProperty", null, undefined, 1]) {
    assert.equal(isVerifyKind(k), false, String(k));
  }
});

test("only False report and Duplicate ask first", () => {
  assert.equal(needsConfirmation("verify"), false);
  assert.equal(needsConfirmation("false"), true);
  assert.equal(needsConfirmation("duplicate"), true);
  assert.equal(needsConfirmation("nope"), false);
  assert.equal(needsConfirmation("toString"), false);
});

test("confirm text matches the app's wording", () => {
  assert.equal(confirmText("verify"), null);
  assert.equal(confirmText("nope"), null);
  assert.deepEqual(confirmText("false"), {
    title: "Mark as false report?",
    body: "The report will be closed and leave the dashboard. It also counts against this reporter's account when later reports are checked.",
    button: "Mark as false"
  });
  assert.deepEqual(confirmText("duplicate"), {
    title: "Mark as duplicate?",
    body: "The report will be closed and leave the dashboard. Use this only when another report already covers the same fire.",
    button: "Mark as duplicate"
  });
});

test("confirmText hands out a copy, so a screen cannot change the wording for the next one", () => {
  const a = confirmText("false");
  a.title = "changed";
  assert.equal(confirmText("false").title, "Mark as false report?");
});

test("toast text after saving", () => {
  assert.equal(doneText("verify"), "Report verified.");
  assert.equal(doneText("false"), "Report marked as false.");
  assert.equal(doneText("duplicate"), "Report marked as duplicate.");
  assert.equal(doneText("nope"), "");
});

test("the write is the app's setVerification: verified, or dismissed for false and duplicate", () => {
  assert.deepEqual(buildVerifyUpdate("verify", STAMP), { verification: "verified", status: "verified", updatedAt: STAMP });
  assert.deepEqual(buildVerifyUpdate("false", STAMP), { verification: "false", status: "dismissed", updatedAt: STAMP });
  assert.deepEqual(buildVerifyUpdate("duplicate", STAMP), { verification: "duplicate", status: "dismissed", updatedAt: STAMP });
});

test("NOTHING ELSE is written: exactly verification, status and updatedAt", () => {
  for (const kind of KINDS) {
    const data = buildVerifyUpdate(kind, STAMP);
    assert.deepEqual(Object.keys(data).sort(), ["status", "updatedAt", "verification"], kind);
    // the fields a responder or reporter owns must never appear
    for (const forbidden of ["severity", "severitySource", "isDuplicate", "duplicateOf", "isSuspicious",
      "reporterId", "description", "latitude", "longitude", "submittedAt", "hasPhoto"]) {
      assert.equal(forbidden in data, false, `${kind} writes ${forbidden}`);
    }
  }
});

test("updatedAt is passed through untouched, so the caller supplies serverTimestamp()", () => {
  assert.equal(buildVerifyUpdate("verify", STAMP).updatedAt, STAMP);
  assert.equal(Object.keys(buildVerifyUpdate("verify", undefined)).includes("updatedAt"), true);
});

test("every call builds a fresh object", () => {
  const a = buildVerifyUpdate("verify", STAMP);
  a.status = "changed";
  assert.equal(buildVerifyUpdate("verify", STAMP).status, "verified");
});

test("unknown kinds build nothing", () => {
  for (const k of ["", "dispatch", "resolve", "toString", "__proto__", undefined, null]) {
    assert.equal(buildVerifyUpdate(k, STAMP), null, String(k));
    assert.equal(outcomeOf(k), null, String(k));
  }
});

test("outcomeOf: what the report looks like after the write", () => {
  assert.deepEqual(outcomeOf("verify"), { rawStatus: "verified", verification: "verified", active: true });
  assert.deepEqual(outcomeOf("false"), { rawStatus: "dismissed", verification: "false", active: false });
  assert.deepEqual(outcomeOf("duplicate"), { rawStatus: "dismissed", verification: "duplicate", active: false });
});

test("outcome agrees with the write and with the rest of the website", () => {
  for (const kind of KINDS) {
    const write = buildVerifyUpdate(kind, STAMP), out = outcomeOf(kind);
    assert.equal(out.rawStatus, write.status, kind);
    assert.equal(out.verification, write.verification, kind);
    assert.equal(out.active, isActiveStatus(write.status), kind);   // same rule as mapping.js
    // once reviewed, the buttons are gone: nobody can review the same report twice
    assert.equal(canVerify({ rawStatus: out.rawStatus }), false, kind);
  }
});

test("patchIncident: status label, stored status, verification and active, in place", () => {
  for (const [kind, label, raw, verification, active] of [
    ["verify", "Verified", "verified", "verified", true],
    ["false", "False", "dismissed", "false", false],
    ["duplicate", "Duplicate", "dismissed", "duplicate", false]
  ]) {
    const i = report("reported");
    assert.equal(patchIncident(i, kind), i, "same object comes back");
    assert.equal(i.status, label, kind);
    assert.equal(i.rawStatus, raw, kind);
    assert.equal(i.verification, verification, kind);
    assert.equal(i.active, active, kind);
  }
});

test("patchIncident changes nothing else on the incident", () => {
  const i = mapIncident("abc123", { status: "reported", severity: "high", description: "Smoke", reporterName: "Ana", isSuspicious: true });
  const before = { ...i };
  patchIncident(i, "verify");
  for (const key of Object.keys(before)) {
    if (["status", "rawStatus", "verification", "active"].includes(key)) continue;
    assert.deepEqual(i[key], before[key], key);
  }
  assert.deepEqual(Object.keys(i).sort(), Object.keys(before).sort());   // no new keys
});

test("patchIncident gives the label a freshly loaded incident would have", () => {
  for (const kind of KINDS) {
    const out = outcomeOf(kind);
    const patched = patchIncident(report("reported"), kind);
    const reloaded = mapIncident("abc123", { status: out.rawStatus, verification: out.verification });
    assert.equal(patched.status, reloaded.status, kind);
    assert.equal(patched.active, reloaded.active, kind);
  }
});

test("patchIncident on an incident that is already a duplicate keeps the Duplicate label", () => {
  const i = mapIncident("abc123", { status: "reported", isDuplicate: true, duplicateOf: "other" });
  patchIncident(i, "verify");
  assert.equal(i.status, "Duplicate");           // statusLabel puts isDuplicate first, as in the mapping
  assert.equal(i.rawStatus, "verified");
});

test("patchIncident with nothing to patch returns what it was given", () => {
  assert.equal(patchIncident(null, "verify"), null);
  assert.equal(patchIncident(undefined, "verify"), undefined);
  const i = report("reported");
  assert.equal(patchIncident(i, "nope"), i);
  assert.equal(i.status, "Reported");
});
