// Measures the AI helper on the labelled sample reports (for Chapter 4 of the paper).
//   GEMINI_API_KEY=... GEMINI_MODEL=gemini-2.5-flash node eval/run-eval.mjs
// Each report is sent 3 times. The script prints a Markdown table and saves eval/results.md.
// The labels in sample-reports.json are EXAMPLES. Replace them with the team's own labels, checked by
// B-FLARE or BFP, and add more reports. Keep about 20 percent aside for tuning the prompt and never tune on the rest.
import { readFileSync, writeFileSync } from "node:fs";
import { SYSTEM_PROMPT, PROMPT_VERSION, sanitizeRequest, buildPrompt, parseAnswer, SEVERITIES } from "../src/core.js";

const key = process.env.GEMINI_API_KEY, model = process.env.GEMINI_MODEL || "gemini-3.5-flash-lite";
if (!key) { console.error("Set GEMINI_API_KEY first."); process.exit(1); }
const RUNS = 3;
const samples = JSON.parse(readFileSync(new URL("./sample-reports.json", import.meta.url)));

// The form score of the app (Chapter 3): trapped +4, vulnerable +2, people +1/+2, size 0/+1/+3, structural +1.
function formScore(r) {
  let s = (r.trapped ? 4 : 0) + (r.vulnerable.length ? 2 : 0) + (r.peopleAtRisk >= 6 ? 2 : r.peopleAtRisk >= 1 ? 1 : 0)
    + ({ small: 0, medium: 1, large: 3 }[r.fireSize] || 0) + (r.fireType === "structural" ? 1 : 0);
  return s >= 7 ? "critical" : s >= 4 ? "high" : s >= 2 ? "medium" : "low";
}
async function ask(input) {
  const res = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`, {
    method: "POST", headers: { "content-type": "application/json", "x-goog-api-key": key },
    body: JSON.stringify({ systemInstruction: { parts: [{ text: SYSTEM_PROMPT }] }, contents: [{ role: "user", parts: [{ text: buildPrompt(input) }] }],
      generationConfig: { temperature: 0, responseMimeType: "application/json", maxOutputTokens: 400 } })
  });
  if (!res.ok) throw new Error("HTTP " + res.status);
  const d = await res.json();
  return parseAnswer(d.candidates?.[0]?.content?.parts?.[0]?.text, input.nearby);
}
const idx = s => SEVERITIES.indexOf(s);
let exact = 0, within1 = 0, formExact = 0, formWithin1 = 0, consistent = 0, invalid = 0, n = 0;
let dupTP = 0, dupFP = 0, dupFN = 0;
const conf = Object.fromEntries(SEVERITIES.map(a => [a, Object.fromEntries(SEVERITIES.map(b => [b, 0]))]));
for (const s of samples) {
  const input = sanitizeRequest({ report: { ...s.report, formSeverity: formScore(s.report) }, nearby: s.nearby });
  const answers = [];
  for (let k = 0; k < RUNS; k++) { try { answers.push(await ask(input)); } catch { answers.push(null); } }
  const ok = answers.filter(Boolean); invalid += answers.length - ok.length;
  if (!ok.length) continue;
  n++;
  const first = ok[0];
  if (ok.length === RUNS && ok.every(a => a.severity === first.severity && a.duplicateOf === first.duplicateOf)) consistent++;
  conf[s.expected][first.severity]++;
  if (first.severity === s.expected) exact++;
  if (Math.abs(idx(first.severity) - idx(s.expected)) <= 1) within1++;
  const f = formScore(s.report);
  if (f === s.expected) formExact++;
  if (Math.abs(idx(f) - idx(s.expected)) <= 1) formWithin1++;
  if (first.duplicateOf && first.duplicateOf === s.expectedDuplicateOf) dupTP++;
  else if (first.duplicateOf) dupFP++;
  else if (s.expectedDuplicateOf) dupFN++;
}
const pct = (a, b) => b ? (100 * a / b).toFixed(1) + "%" : "n/a";
const out = [
  `# AI helper results`, `Model: ${model} | Prompt: ${PROMPT_VERSION} | Reports answered: ${n}/${samples.length} | Unusable answers: ${invalid} | Date: ${new Date().toISOString().slice(0, 10)}`, "",
  `| Measure | AI | Form score |`, `|---|---|---|`,
  `| Same level as the labelers | ${pct(exact, n)} | ${pct(formExact, n)} |`,
  `| Within one level | ${pct(within1, n)} | ${pct(formWithin1, n)} |`, "",
  `Consistency (${RUNS} runs give the same answer): ${pct(consistent, n)}`,
  `Duplicate check: true positives ${dupTP}, false positives ${dupFP}, missed ${dupFN}`, "",
  `Confusion matrix (rows = labelers, columns = AI)`, `| | ${SEVERITIES.join(" | ")} |`, `|---|${SEVERITIES.map(() => "---").join("|")}|`,
  ...SEVERITIES.map(a => `| ${a} | ${SEVERITIES.map(b => conf[a][b]).join(" | ")} |`)
].join("\n");
console.log(out);
writeFileSync(new URL("./results.md", import.meta.url), out + "\n");
