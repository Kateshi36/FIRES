// The drawer's "Review this report" block, its confirmation dialog, and the dialog markup (W13.11, W13.12).
//   node --test tests/reviewBlock.test.mjs
import test from "node:test";
import assert from "node:assert/strict";
import { reviewBlockHtml, reviewDialog, canVerify, confirmText, VERIFY_BUTTONS } from "../js/verifyRules.js";
import { confirmHtml } from "../js/dialogHtml.js";
import { mapIncident } from "../js/mapping.js";

const inc = (status = "reported", extra = {}) => mapIncident("abc123def", { status, addressText: "12 Rizal St.", ...extra });
const buttons = html => [...html.matchAll(/<button class="btn( ghost)?" data-rv="(\w+)"( disabled)?>([^<]*)<\/button>/g)]
  .map(m => ({ ghost: !!m[1], kind: m[2], disabled: !!m[3], text: m[4] }));

// ---------- W13.11: the block ----------

test("a report nobody has reviewed shows the block, with the three buttons in order", () => {
  const html = reviewBlockHtml(inc("reported"));
  assert.match(html, /Review this report/);
  assert.deepEqual(buttons(html).map(b => [b.kind, b.text]), [["verify", "Verify"], ["false", "False report"], ["duplicate", "Duplicate"]]);
});

test("Verify is the main button: the other two are the quieter (ghost) style", () => {
  assert.deepEqual(buttons(reviewBlockHtml(inc())).map(b => b.ghost), [false, true, true]);
});

test("the block is hidden for every status except reported", () => {
  for (const st of ["verified", "dispatched", "on_scene", "resolved", "dismissed"]) {
    assert.equal(reviewBlockHtml(inc(st)), "", st);
  }
});

test("a report with no stored status counts as reported, as everywhere else", () => {
  assert.notEqual(reviewBlockHtml(mapIncident("x1", {})), "");
});

test("the block follows canVerify exactly, and nothing is drawn without an incident", () => {
  for (const st of ["reported", "verified", "dispatched", "on_scene", "resolved", "dismissed"]) {
    assert.equal(reviewBlockHtml(inc(st)) !== "", canVerify(inc(st)), st);
  }
  for (const none of [null, undefined]) assert.equal(reviewBlockHtml(none), "");
});

test("the label can't fool it: a report SHOWN as Reported by label but stored otherwise gets no block", () => {
  const i = inc("verified"); i.status = "Reported";            // only rawStatus counts
  assert.equal(reviewBlockHtml(i), "");
});

test("the buttons match VERIFY_BUTTONS, so the list has one source", () => {
  assert.deepEqual(buttons(reviewBlockHtml(inc())).map(b => b.kind), VERIFY_BUTTONS.map(b => b.kind));
});

test("while saving: every button is disabled and only the chosen one says Saving...", () => {
  const b = buttons(reviewBlockHtml(inc(), { busy: true, kind: "verify" }));
  assert.ok(b.every(x => x.disabled));
  assert.deepEqual(b.map(x => x.text), ["Saving...", "False report", "Duplicate"]);
});

test("after a failed save: buttons usable again, the one that failed says Try again, the message is shown", () => {
  const html = reviewBlockHtml(inc(), { busy: false, kind: "verify", error: "No internet connection." });
  const b = buttons(html);
  assert.ok(b.every(x => !x.disabled));
  assert.deepEqual(b.map(x => x.text), ["Try again", "False report", "Duplicate"]);
  assert.match(html, /<div class="ferr" id="rv" role="alert">No internet connection\.<\/div>/);
});

test("with nothing wrong the message area is empty (and hidden by the .ferr:empty rule)", () => {
  assert.match(reviewBlockHtml(inc()), /<div class="ferr" id="rv" role="alert"><\/div>/);
});

test("the error text is escaped", () => {
  assert.match(reviewBlockHtml(inc(), { kind: "verify", error: "<img src=x onerror=1>" }), /&lt;img src=x onerror=1&gt;/);
});

// ---------- W13.12: the confirmation dialogs ----------

test("False report and Duplicate each get a dialog with the app's wording, and name the report", () => {
  for (const kind of ["false", "duplicate"]) {
    const d = reviewDialog(inc(), kind), c = confirmText(kind);
    assert.equal(d.title, c.title);
    assert.equal(d.confirm, c.button);
    assert.match(d.body, /INC-ABC123/);
    assert.match(d.body, /12 Rizal St\./);
  }
  assert.match(reviewDialog(inc(), "false").body, /counts against this reporter/);
  assert.match(reviewDialog(inc(), "duplicate").body, /another report already covers the same fire/);
});

test("Verify needs no dialog, and neither does an unknown kind or a missing report", () => {
  assert.equal(reviewDialog(inc(), "verify"), null);
  assert.equal(reviewDialog(inc(), "close"), null);
  assert.equal(reviewDialog(null, "false"), null);
});

test("the dialog body is escaped: the wording's apostrophe, and a location written by a citizen", () => {
  const i = inc("reported", { addressText: "<script>alert(1)</script>" });
  const body = reviewDialog(i, "false").body;
  assert.ok(!body.includes("<script>"));
  assert.match(body, /&lt;script&gt;/);
  assert.match(body, /reporter&#39;s/);
});

// ---------- the shared dialog markup ----------

test("confirmHtml has the ids the dialog code looks for, Cancel before the confirm button", () => {
  const html = confirmHtml({ title: "Mark as false report?", body: "<p>Body</p>", confirm: "Mark as false" });
  assert.match(html, /<h2 id="dlg-t">Mark as false report\?<\/h2>/);
  assert.match(html, /<p>Body<\/p>/);
  assert.match(html, /<div class="ferr" id="dg" role="alert"><\/div>/);
  assert.ok(html.indexOf('id="dno"') < html.indexOf('id="dyes"'));
  assert.match(html, /id="dno">Cancel</);
  assert.match(html, /id="dyes">Mark as false</);
});

test("confirmHtml escapes the title and the confirm label, but passes the body through (callers make it safe)", () => {
  const html = confirmHtml({ title: "<b>t</b>", body: "<p><b>Ana</b></p>", confirm: "A & B" });
  assert.match(html, /&lt;b&gt;t&lt;\/b&gt;/);
  assert.match(html, /A &amp; B/);
  assert.match(html, /<p><b>Ana<\/b><\/p>/);
});

test("confirmHtml tolerates a missing body", () => {
  assert.doesNotMatch(confirmHtml({ title: "t", confirm: "ok" }), /undefined/);
});
