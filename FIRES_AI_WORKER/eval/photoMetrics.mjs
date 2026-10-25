// The numbers for the photo check (Chapter 4). Pure functions, so they are tested (tests/photoMetrics.test.mjs).
export const LEVELS = ["none", "low", "medium", "high", "critical"];

// pairs: [{ expected: true|false, predicted: true|false }]. Returns the counts and the four usual measures.
export function binaryStats(pairs) {
  let tp = 0, fp = 0, fn = 0, tn = 0;
  for (const p of pairs) {
    if (p.expected && p.predicted) tp++;
    else if (!p.expected && p.predicted) fp++;
    else if (p.expected && !p.predicted) fn++;
    else tn++;
  }
  const n = tp + fp + fn + tn;
  const div = (a, b) => (b ? a / b : null);
  const precision = div(tp, tp + fp), recall = div(tp, tp + fn);
  const f1 = precision !== null && recall !== null && precision + recall > 0 ? (2 * precision * recall) / (precision + recall) : null;
  return { tp, fp, fn, tn, n, accuracy: div(tp + tn, n), precision, recall, f1 };
}

// rows = what the labelers said, columns = what the AI said.
export function confusion(pairs, levels = LEVELS) {
  const m = Object.fromEntries(levels.map(a => [a, Object.fromEntries(levels.map(b => [b, 0]))]));
  for (const p of pairs) if (m[p.expected] && p.predicted in m[p.expected]) m[p.expected][p.predicted]++;
  return m;
}

// Share of pairs where the AI level is the same, or within one level, of the labelers'.
export function levelAgreement(pairs, levels = LEVELS) {
  const idx = s => levels.indexOf(s);
  const ok = pairs.filter(p => idx(p.expected) >= 0 && idx(p.predicted) >= 0);
  const exact = ok.filter(p => p.expected === p.predicted).length;
  const within1 = ok.filter(p => Math.abs(idx(p.expected) - idx(p.predicted)) <= 1).length;
  return { n: ok.length, exact, within1, exactRate: ok.length ? exact / ok.length : null, within1Rate: ok.length ? within1 / ok.length : null };
}

export const pct = v => (v === null || v === undefined ? "n/a" : (100 * v).toFixed(1) + "%");
