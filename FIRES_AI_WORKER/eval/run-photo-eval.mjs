// Measures the AI photo check on the team's labelled photos (for Chapter 4 of the paper).
//   1. Put the photos in eval/photos/ (jpg, png or webp, each under about 1.5 MB).
//   2. Copy eval/labels.example.json to eval/labels.json and describe every photo:
//        fire: "yes" | "no"        (smoke only or look-alikes such as a sunset are "no")
//        severity: none | low | medium | high | critical   (the labelers' agreed level; "none" when fire is "no")
//        aiGenerated: "yes" | "no" (is the picture computer-generated?)
//        set: "tune" | "test"      (about 20 percent "tune"; the prompt may be tuned on these, NEVER on "test")
//      Two members label every photo; a B-FLARE or BFP person checks the ones they disagree on.
//   3. GEMINI_API_KEY=... GEMINI_MODEL=<model-name> node eval/run-photo-eval.mjs
// Every photo is sent 3 times. Results go to the screen and to eval/photo-results.md (only the "test" photos are scored).
import { readFileSync, writeFileSync, existsSync } from "node:fs";
import { extname } from "node:path";
import { PHOTO_PROMPT, PHOTO_PROMPT_VERSION, sanitizePhotoRequest, parsePhotoAnswer } from "../src/core.js";
import { binaryStats, confusion, levelAgreement, LEVELS, pct } from "./photoMetrics.mjs";

const key = process.env.GEMINI_API_KEY, model = process.env.GEMINI_MODEL || "gemini-3.5-flash-lite";
if (!key) { console.error("Set GEMINI_API_KEY first."); process.exit(1); }
const labelsUrl = new URL("./labels.json", import.meta.url);
if (!existsSync(labelsUrl)) { console.error("eval/labels.json not found. Copy eval/labels.example.json and describe your photos."); process.exit(1); }
const labels = JSON.parse(readFileSync(labelsUrl));
const RUNS = 3;
const MIME = { ".jpg": "image/jpeg", ".jpeg": "image/jpeg", ".png": "image/png", ".webp": "image/webp" };

async function ask(photo) {
  const res = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`, {
    method: "POST", headers: { "content-type": "application/json", "x-goog-api-key": key },
    body: JSON.stringify({
      contents: [{ role: "user", parts: [{ text: PHOTO_PROMPT }, { inlineData: { mimeType: photo.mimeType, data: photo.base64 } }] }],
      generationConfig: { temperature: 0, responseMimeType: "application/json", maxOutputTokens: 2048 }
    })
  });
  if (!res.ok) throw new Error("HTTP " + res.status);
  const d = await res.json();
  return parsePhotoAnswer(d.candidates?.[0]?.content?.parts?.[0]?.text);
}

const fire = [], level = [], gen = [];
let consistent = 0, scored = 0, invalid = 0, unsureFire = 0, skipped = 0;
for (const l of labels) {
  if (l.set !== "test") { skipped++; continue; }
  const path = new URL("./photos/" + l.file, import.meta.url);
  const mimeType = MIME[extname(l.file).toLowerCase()];
  if (!existsSync(path) || !mimeType) { console.warn("skipped (missing or unsupported):", l.file); continue; }
  const photo = sanitizePhotoRequest({ mimeType, base64: readFileSync(path).toString("base64") });
  if (!photo) { console.warn("skipped (too large or unreadable):", l.file); continue; }
  const answers = [];
  for (let k = 0; k < RUNS; k++) { try { answers.push(await ask(photo)); } catch { answers.push(null); } }
  const ok = answers.filter(Boolean); invalid += answers.length - ok.length;
  if (!ok.length) continue;
  scored++;
  const a = ok[0];
  if (ok.length === RUNS && ok.every(x => x.fireDetected === a.fireDetected && x.severity === a.severity && x.aiGenerated === a.aiGenerated)) consistent++;
  if (a.fireDetected === "unsure") unsureFire++;
  fire.push({ expected: l.fire === "yes", predicted: a.fireDetected === "yes" });     // "unsure" counts as no fire seen
  if (l.fire === "yes") level.push({ expected: l.severity, predicted: a.severity });
  gen.push({ expected: l.aiGenerated === "yes", predicted: a.aiGenerated === "yes" });
}

const fs = binaryStats(fire), gs = binaryStats(gen), ls = levelAgreement(level), cm = confusion(level);
const out = [
  `# AI photo check results`,
  `Model: ${model} | Photo prompt: ${PHOTO_PROMPT_VERSION} | Photos scored: ${scored} (tuning photos skipped: ${skipped}) | Unusable answers: ${invalid} | Date: ${new Date().toISOString().slice(0, 10)}`, "",
  `## Fire detection (yes = fire; "unsure" counted as no fire seen, ${unsureFire} photos)`,
  `| Accuracy | Precision | Recall | F1 | TP | FP | FN | TN |`, `|---|---|---|---|---|---|---|---|`,
  `| ${pct(fs.accuracy)} | ${pct(fs.precision)} | ${pct(fs.recall)} | ${pct(fs.f1)} | ${fs.tp} | ${fs.fp} | ${fs.fn} | ${fs.tn} |`, "",
  `## Severity (photos that really show a fire: ${ls.n})`,
  `Same level as the labelers: ${pct(ls.exactRate)} | Within one level: ${pct(ls.within1Rate)}`, "",
  `Confusion matrix (rows = labelers, columns = AI)`, `| | ${LEVELS.join(" | ")} |`, `|---|${LEVELS.map(() => "---").join("|")}|`,
  ...LEVELS.map(a => `| ${a} | ${LEVELS.map(b => cm[a][b]).join(" | ")} |`), "",
  `## "AI-generated" hint (yes = the picture is generated)`,
  `| Accuracy | Precision | Recall | F1 | TP | FP | FN | TN |`, `|---|---|---|---|---|---|---|---|`,
  `| ${pct(gs.accuracy)} | ${pct(gs.precision)} | ${pct(gs.recall)} | ${pct(gs.f1)} | ${gs.tp} | ${gs.fp} | ${gs.fn} | ${gs.tn} |`, "",
  `Consistency (${RUNS} runs give the same answer): ${scored ? pct(consistent / scored) : "n/a"}`
].join("\n");
console.log(out);
writeFileSync(new URL("./photo-results.md", import.meta.url), out + "\n");
