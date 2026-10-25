import test from "node:test";
import assert from "node:assert/strict";
import { binaryStats, confusion, levelAgreement, pct } from "../eval/photoMetrics.mjs";

test("binary stats: counts, accuracy, precision, recall and F1", () => {
  const pairs = [
    { expected: true, predicted: true }, { expected: true, predicted: true }, { expected: true, predicted: false },
    { expected: false, predicted: true }, { expected: false, predicted: false }, { expected: false, predicted: false }
  ];
  const s = binaryStats(pairs);
  assert.deepEqual([s.tp, s.fp, s.fn, s.tn, s.n], [2, 1, 1, 2, 6]);
  assert.equal(s.accuracy, 4 / 6); assert.equal(s.precision, 2 / 3); assert.equal(s.recall, 2 / 3);
  assert.ok(Math.abs(s.f1 - 2 / 3) < 1e-9);
});
test("binary stats: empty input and no positives give n/a, not NaN", () => {
  const e = binaryStats([]);
  assert.equal(e.accuracy, null); assert.equal(e.f1, null);
  const none = binaryStats([{ expected: false, predicted: false }]);
  assert.equal(none.precision, null); assert.equal(none.recall, null); assert.equal(none.accuracy, 1);
});
test("confusion matrix and level agreement", () => {
  const pairs = [
    { expected: "high", predicted: "high" }, { expected: "high", predicted: "medium" },
    { expected: "low", predicted: "critical" }, { expected: "critical", predicted: "high" }
  ];
  const m = confusion(pairs);
  assert.equal(m.high.high, 1); assert.equal(m.high.medium, 1); assert.equal(m.low.critical, 1); assert.equal(m.critical.high, 1);
  const a = levelAgreement(pairs);
  assert.deepEqual([a.n, a.exact, a.within1], [4, 1, 3]);
  assert.equal(pct(a.exactRate), "25.0%"); assert.equal(pct(null), "n/a");
});
