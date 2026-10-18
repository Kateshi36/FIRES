// The "Assign responder" button in the incident drawer (W13.15).
//   node --test tests/assignButton.test.mjs
import test from "node:test";
import assert from "node:assert/strict";
import { assignButtonHtml, canAssign } from "../js/assignRules.js";
import { mapIncident } from "../js/mapping.js";

const inc = (status = "reported", extra = {}) => Object.assign(mapIncident("abc123def", { status }), { assignLoaded: true }, extra);

test("an open report gets the button, enabled once its assignments are loaded", () => {
  for (const st of ["reported", "verified", "dispatched", "on_scene"]) {
    const html = assignButtonHtml(inc(st));
    assert.match(html, /<button class="btn ghost" id="asg">Assign responder<\/button>/, st);
    assert.doesNotMatch(html, /disabled/, st);
  }
});

test("a resolved or closed report shows no button at all", () => {
  for (const st of ["resolved", "dismissed"]) assert.equal(assignButtonHtml(inc(st)), "", st);
});

test("a report with no stored status counts as reported (open), as everywhere else", () => {
  assert.match(assignButtonHtml(Object.assign(mapIncident("x1", {}), { assignLoaded: true })), /id="asg"/);
});

test("nothing is drawn without an incident", () => {
  for (const none of [null, undefined]) assert.equal(assignButtonHtml(none), "");
});

test("the button follows canAssign exactly", () => {
  for (const st of ["reported", "verified", "dispatched", "on_scene", "resolved", "dismissed"]) {
    assert.equal(assignButtonHtml(inc(st)) !== "", canAssign(inc(st)), st);
  }
});

test("the stored status decides, not the label shown on screen", () => {
  const i = inc("resolved"); i.status = "Ongoing";           // a label that looks active
  assert.equal(assignButtonHtml(i), "");
  const j = inc("verified"); j.status = "Resolved";          // a label that looks closed
  assert.match(assignButtonHtml(j), /id="asg"/);
});

test("until the assignments are loaded the button is there but disabled, with the reason", () => {
  for (const loaded of [false, undefined]) {
    const html = assignButtonHtml(inc("verified", { assignLoaded: loaded }));
    assert.match(html, /id="asg" disabled title="The assignments are still loading\."/);
  }
});

test("the button sits in the same row style as the Review buttons (rvbtns), so it lines up with them", () => {
  assert.match(assignButtonHtml(inc()), /^<div class="rvbtns">/);
});
