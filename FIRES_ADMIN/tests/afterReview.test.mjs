// What is redrawn after a review was saved (W13.14).
//   node --test tests/afterReview.test.mjs
import test from "node:test";
import assert from "node:assert/strict";
import { redrawAfterReview, drawerAfterReview, patchIncident, canVerify, reviewBlockHtml, doneText } from "../js/verifyRules.js";
import { mapIncident } from "../js/mapping.js";

const inc = (extra = {}) => Object.assign(mapIncident("abc123def", { status: "reported", addressText: "12 Rizal St." }), extra);

// ---------- the tab behind the drawer ----------

test("Overview and Live map are drawn again; Incident records only re-runs its filters", () => {
  assert.equal(redrawAfterReview("over"), "screen");
  assert.equal(redrawAfterReview("map"), "screen");
  assert.equal(redrawAfterReview("rec"), "filters");
});

test("User accounts, Reports and anything unknown are left alone", () => {
  for (const tab of ["resp", "rep", "", undefined, null, "nonsense"]) assert.equal(redrawAfterReview(tab), "none", String(tab));
});

// ---------- the drawer ----------

test("details already loaded: the drawer is redrawn from them, with no second read", () => {
  assert.equal(drawerAfterReview(inc({ detailState: "ready" })), "redraw");
});

test("details not loaded yet, still loading, or failed: they are loaded as when the drawer first opens", () => {
  for (const s of ["idle", "loading", "error"]) assert.equal(drawerAfterReview(inc({ detailState: s })), "reload", s);
});

test("the report is gone: close the drawer", () => {
  for (const none of [null, undefined]) assert.equal(drawerAfterReview(none), "close");
});

// ---------- what the redraw then shows ----------

test("after a saved Verify the drawer has no Review block and shows the new status", () => {
  const i = inc({ detailState: "ready" });
  assert.notEqual(reviewBlockHtml(i), "");          // before: the three buttons
  patchIncident(i, "verify");                       // what verifyIncident does after the write
  assert.equal(reviewBlockHtml(i), "");             // after: no buttons
  assert.equal(i.status, "Verified");
  assert.equal(canVerify(i), false);
});

test("after False report or Duplicate the report is closed: not active, no buttons, its own label", () => {
  for (const [kind, label] of [["false", "False"], ["duplicate", "Duplicate"]]) {
    const i = inc({ detailState: "ready" });
    patchIncident(i, kind);
    assert.equal(i.active, false, kind);
    assert.equal(i.status, label, kind);
    assert.equal(reviewBlockHtml(i), "", kind);
  }
});

test("the toast for each kind says what happened", () => {
  assert.equal(doneText("verify"), "Report verified.");
  assert.equal(doneText("false"), "Report marked as false.");
  assert.equal(doneText("duplicate"), "Report marked as duplicate.");
});
