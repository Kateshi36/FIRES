import test from "node:test";
import assert from "node:assert/strict";
import { mapIncident } from "../js/mapping.js";
import { aiAdviceHtml } from "../js/aiPanel.js";

const base = { description: "x", severity: "low", status: "reported", hasPhoto: true };
const ai = { aiFireDetected: "yes", aiSeverity: "high", aiConfidence: 0.85, aiReason: "Flames", aiPromptVersion: "v2", aiModel: "gemini-3.5-flash-lite" };
const answer = { severity: "high", confidence: 0.7, reasons: ["Two houses"], duplicateOf: null };
const mk = extra => mapIncident("abc12345", { ...base, ...extra });

test("one panel holds the photo advice and the text suggestion", () => {
  const h = aiAdviceHtml(mk(ai), {}, { status: "done", answer }, "https://w/suggest", []);
  assert.equal((h.match(/id="aip"/g) || []).length, 1);
  assert.match(h, /AI advice/); assert.match(h, /Fire likely/); assert.match(h, /Two houses/);
  assert.match(h, /Advice only/);
});
test("no endpoint: one line, no buttons", () => {
  const h = aiAdviceHtml(mk(ai), {}, {}, "", []);
  assert.match(h, /Not set up yet/); assert.ok(!h.includes("<button"));
});
test("starts by itself: no 'Get' button anywhere, both parts say they are reading", () => {
  const h = aiAdviceHtml(mk({}), {}, {}, "https://w/suggest", []);
  assert.match(h, /Reading the photo/); assert.match(h, /Reading the report/);
  assert.ok(!h.includes("<button") && !/Get AI|Check photo/.test(h));
});
test("report without a photo shows only the text part; closed report with advice shows only the photo part", () => {
  const nophoto = aiAdviceHtml(mk({ hasPhoto: false }), {}, { status: "done", answer }, "https://w/suggest", []);
  assert.ok(!nophoto.includes('id="pai"')); assert.match(nophoto, /id="aitx"/);
  const closed = aiAdviceHtml(mk({ ...ai, status: "resolved" }), {}, {}, "https://w/suggest", []);
  assert.match(closed, /id="pai"/); assert.ok(!closed.includes('id="aitx"'));
});
test("a failure of one part leaves the other readable and shows only its own Try again", () => {
  const h = aiAdviceHtml(mk(ai), {}, { status: "error", text: "down" }, "https://w/suggest", []);
  assert.match(h, /Fire likely/); assert.match(h, /id="aib"/); assert.ok(!h.includes('id="pcb"'));
});
test("the model name is never shown", () => {
  assert.ok(!/gemini|flash/i.test(aiAdviceHtml(mk(ai), {}, { status: "done", answer }, "https://w/suggest", [])));
});
