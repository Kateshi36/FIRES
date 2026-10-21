import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { sanitizeRequest, SEVERITIES } from "../src/core.js";
const S = JSON.parse(readFileSync(new URL("../eval/sample-reports.json", import.meta.url)));
test("sample reports are valid: labels exist and every report survives sanitizing", () => {
  assert.ok(S.length >= 15);
  for (const s of S) {
    assert.ok(SEVERITIES.includes(s.expected), s.id);
    assert.ok(sanitizeRequest({ report: s.report, nearby: s.nearby }), s.id);
    if (s.expectedDuplicateOf) assert.ok((s.nearby || []).some(n => n.id === s.expectedDuplicateOf), s.id);
  }
});
