import test from "node:test";
import assert from "node:assert/strict";
import { mapIncident } from "../js/mapping.js";
import { photoSectionHtml as photoAiHtml, photoMismatch } from "../js/photoAi.js";

const base = { description: "x", severity: "low", status: "reported", hasPhoto: true };
const ai = { aiFireDetected: "yes", aiSeverity: "high", aiConfidence: 0.85, aiReason: "Flames <b>", aiPromptVersion: "v1", aiModel: "m" };

test("no ai fields: no result is shown", () => {
  const i = mapIncident("abc12345", base);
  assert.equal(i.photoAi, null);
  assert.ok(!/Fire likely|No fire/.test(photoAiHtml(i)));
});
test("maps and shows advice, escaped", () => {
  const i = mapIncident("abc12345", { ...base, ...ai });
  const h = photoAiHtml(i);
  assert.match(h, /Fire likely/);
  assert.match(h, /High \(85%\)/);
  assert.ok(!h.includes("Flames <b>")); assert.match(h, /Flames &lt;b&gt;/);
});
test("mismatch with the form score", () => {
  assert.equal(photoMismatch(mapIncident("abc12345", { ...base, ...ai })), true);
  assert.equal(photoMismatch(mapIncident("abc12345", { ...base, ...ai, severity: "high" })), false);
  assert.equal(photoMismatch(mapIncident("abc12345", { ...base, aiFireDetected: "no", aiSeverity: "none", aiConfidence: 0.9, aiReason: "r" })), false);
});
test("bad values are ignored or clamped", () => {
  assert.equal(mapIncident("abc12345", { ...base, aiFireDetected: "maybe" }).photoAi, null);
  assert.equal(mapIncident("abc12345", { ...base, ...ai, aiConfidence: 9 }).photoAi.confidence, 1);
});

import { photoEndpoint, needsPhotoCheck, splitDataUrl, toAiFields, requestPhotoCheck } from "../js/photoAi.js";

const ok = { fireDetected: "yes", severity: "high", confidence: 0.8, reason: "Flames.", promptVersion: "v1", model: "m" };
const open = (extra = {}) => mapIncident("abc12345", { ...base, ...extra });

test("endpoint for photos comes from the suggest address", () => {
  assert.equal(photoEndpoint("https://x.workers.dev/suggest"), "https://x.workers.dev/photo");
  assert.equal(photoEndpoint(""), "");
});

test("only open reports with a photo and no answer need a check", () => {
  assert.equal(needsPhotoCheck(open()), true);
  assert.equal(needsPhotoCheck(open(ai)), false);
  assert.equal(needsPhotoCheck(open({ hasPhoto: false })), false);
  assert.equal(needsPhotoCheck(open({ status: "resolved" })), false);
});

test("data url is split, anything else is refused", () => {
  assert.deepEqual(splitDataUrl("data:image/jpeg;base64,QUJD"), { mimeType: "image/jpeg", base64: "QUJD" });
  assert.equal(splitDataUrl("data:text/html;base64,QUJD"), null);
  assert.equal(splitDataUrl(null), null);
});

test("toAiFields keeps exactly the six fields and refuses bad answers", () => {
  const f = toAiFields(ok);
  assert.deepEqual(Object.keys(f).sort(), ["aiConfidence", "aiFireDetected", "aiGenerated", "aiModel", "aiPromptVersion", "aiReason", "aiSeverity"]);
  assert.equal(f.aiGenerated, "unsure");
  assert.equal(toAiFields({ ...ok, aiGenerated: "yes" }).aiGenerated, "yes");
  assert.equal(toAiFields({ ...ok, aiGenerated: "x" }).aiGenerated, "unsure");
  assert.equal(toAiFields({ ...ok, fireDetected: "maybe" }), null);
  assert.equal(toAiFields({ ...ok, reason: " " }), null);
  assert.equal(toAiFields({ ...ok, confidence: 5 }).aiConfidence, 1);
});

test("requestPhotoCheck: success, http errors and timeouts never throw", async () => {
  const good = async () => ({ ok: true, json: async () => ok });
  assert.equal((await requestPhotoCheck("u", "t", {}, good)).ok, true);
  const bad = async () => ({ ok: false, status: 403, json: async () => ({ detail: "x" }) });
  const r = await requestPhotoCheck("u", "t", {}, bad);
  assert.equal(r.ok, false);
  assert.match(r.text, /administrators/i);
  const wrong = async () => ({ ok: true, json: async () => ({ severity: "high" }) });
  assert.equal((await requestPhotoCheck("u", "t", {}, wrong)).ok, false);
  const boom = async () => { const e = new Error("t"); e.name = "TimeoutError"; throw e; };
  assert.match((await requestPhotoCheck("u", "t", {}, boom)).text, /did not answer/);
});

test("photo section: starts by itself, no button until a failure, hidden when there is nothing to check", () => {
  const i = open();
  const idle = photoAiHtml(i, {});
  assert.match(idle, /Reading the photo/); assert.ok(!idle.includes("pcb"));
  assert.match(photoAiHtml(i, { status: "loading" }), /Reading the photo/);
  const e = photoAiHtml(i, { status: "error", text: "bad <i>" });
  assert.match(e, /id="pcb"[^>]*>Try again/);
  assert.ok(!e.includes("<i>"));
  assert.equal(photoAiHtml(open({ status: "resolved" }), {}), "");
  assert.match(photoAiHtml(open(ai), {}), /Fire likely/);
});
test("the model name is never shown", () => {
  const h = photoAiHtml(open({ ...ai, aiModel: "gemini-3.5-flash-lite" }), {});
  assert.ok(!/gemini|flash/i.test(h));
});

test("a photo that looks AI-generated shows a warning, a normal one does not", () => {
  const yes = photoAiHtml(open({ ...ai, aiGenerated: "yes" }), {});
  assert.match(yes, /id="aigen"[^>]*>This photo may be AI-generated or edited/);
  assert.ok(!photoAiHtml(open({ ...ai, aiGenerated: "no" }), {}).includes("aigen"));
  assert.ok(!photoAiHtml(open(ai), {}).includes("aigen"));
  assert.equal(open({ ...ai, aiGenerated: "yes" }).photoAi.generated, "yes");
  assert.equal(open({ ...ai, aiGenerated: "bad" }).photoAi.generated, null);
});
