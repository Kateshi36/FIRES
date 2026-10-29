// Pure rules behind the Overview, Records, Reports and Activity screens.   node --test tests/viewRules.test.mjs
import test from "node:test";
import assert from "node:assert/strict";
import {
  typeLabel, needsReview, isClosedOut, filterRecords, chipCounts, applyChip, sortRecords, nextSort, pageSlice, responderText, ageText,
  median, medianResolve, monthCounts, weeklyMedians, typeCounts, purokCounts, hourHeat, busiestSlot, withinHour,
  incidentEvents, mergeEvents, filterEvents, dayHeading, clockText, activityDoc, newReports, unassignedTooLong
} from "../js/viewRules.js";
import { mapIncident } from "../js/mapping.js";

const T = ms => ({ toMillis: () => ms });
const NOW = Date.UTC(2026, 9, 22, 4, 0);                 // Thu 22 Oct 2026, 12:00 PH time
const inc = (id, d = {}, extra = {}) => Object.assign(mapIncident(id, { status: "reported", severity: "low", submittedAt: T(NOW - 3600e3), addressText: "12 Rizal St.", reporterId: "u1", ...d }), extra);

test("labels: fire types, with a safe fallback", () => {
  assert.equal(typeLabel("structural"), "House or building");
  assert.equal(typeLabel("nonsense"), "Other");
  assert.equal(inc("a1", { fireType: "vehicle" }).fireType, "vehicle");
  assert.equal(inc("a2").fireType, "other");
});

test("needs review: unreviewed, or open and flagged", () => {
  assert.equal(needsReview(inc("a1")), true);
  assert.equal(needsReview(inc("a2", { status: "verified" })), false);
  assert.equal(needsReview(inc("a3", { status: "verified", isSuspicious: true })), true);
  assert.equal(needsReview(inc("a4", { status: "resolved", isSuspicious: true })), false);
  assert.equal(isClosedOut(inc("a5", { status: "dismissed", verification: "false" })), true);
});

test("chips count and filter the same way", () => {
  const list = [inc("a1"), inc("a2", { status: "verified" }), inc("a3", { status: "resolved" }), inc("a4", { status: "dismissed", verification: "false" })];
  assert.deepEqual(chipCounts(list), { all: 4, review: 1, active: 2, resolved: 1, closed: 1 });
  assert.equal(applyChip(list, "active").length, 2);
  assert.equal(applyChip(list, "nope").length, 4);
});

test("filters: text (code, location, reporter), severity, type, dates", () => {
  const list = [inc("abc123", { severity: "high", fireType: "vehicle" }), inc("zzz999", { addressText: "Mabini Ave" })];
  assert.equal(filterRecords(list, { q: "mabini" }).length, 1);
  assert.equal(filterRecords(list, { q: "inc-abc" }).length, 1);
  assert.equal(filterRecords(list, { sv: "High" }).length, 1);
  assert.equal(filterRecords(list, { ty: "vehicle" }).length, 1);
  assert.equal(filterRecords(list, { d1: "2099-01-01" }).length, 0);
  assert.equal(filterRecords(list, {}).length, 2);
});

test("sort: severity first (then newest), or newest first", () => {
  const a = inc("a1", { severity: "low", submittedAt: T(NOW - 10) }), b = inc("b1", { severity: "critical", submittedAt: T(NOW - 99999) }), c = inc("c1", { severity: "low", submittedAt: T(NOW - 5) });
  assert.deepEqual(sortRecords([a, b, c], "sev").map(i => i.id), ["b1", "c1", "a1"]);
  assert.deepEqual(sortRecords([a, b, c], "new").map(i => i.id), ["c1", "a1", "b1"]);
});

test("paging", () => {
  const list = Array.from({ length: 23 }, (_, k) => k);
  assert.deepEqual(pageSlice(list, 1, 10), { items: list.slice(0, 10), page: 1, pages: 3, total: 23, from: 1, to: 10 });
  assert.equal(pageSlice(list, 9, 10).page, 3);
  assert.equal(pageSlice(list, 3, 10).to, 23);
  assert.deepEqual(pageSlice([], 1, 10), { items: [], page: 1, pages: 1, total: 0, from: 0, to: 0 });
});

test("responder column", () => {
  assert.equal(responderText(inc("a1")), null);
  assert.equal(responderText(inc("a2", {}, { assignLoaded: true })), "Unassigned");
  assert.equal(responderText(inc("a3", { status: "resolved" }, { assignLoaded: true })), "None");
  assert.equal(responderText(inc("a4", {}, { assignLoaded: true, res: [["u", "BFP", "Ana Cruz", []], ["v", "BFP", "Ben", []]] })), "Ana Cruz, Ben");
});

test("age text", () => {
  assert.equal(ageText(NOW - 4 * 60000, NOW), "4 min");
  assert.equal(ageText(NOW - 2 * 3600e3, NOW), "2 h");
  assert.equal(ageText(NOW - 3 * 86400e3, NOW), "3 d");
  assert.equal(ageText(null, NOW), "-");
});

test("median and time to resolve", () => {
  assert.equal(median([3, 1, 2]), 2);
  assert.equal(median([1, 2, 3, 4]), 2.5);
  assert.equal(median([]), null);
  const r = inc("a1", { status: "resolved" }, { resolvedMs: NOW }), r2 = inc("a2", { status: "resolved", submittedAt: T(NOW - 7200e3) }, { resolvedMs: NOW });
  assert.equal(medianResolve([r, r2]), 90);
  assert.equal(medianResolve([inc("a3")]), null);
  assert.deepEqual(withinHour([r, r2]), { n: 2, within: 1 });
});

test("reports this month vs last month, in Philippine time", () => {
  const list = [inc("a1"), inc("a2", { submittedAt: T(Date.UTC(2026, 8, 20)) }), inc("a3", { submittedAt: T(Date.UTC(2026, 8, 2)) }), inc("a4", { submittedAt: T(Date.UTC(2026, 6, 2)) })];
  assert.deepEqual(monthCounts(list, NOW), { thisMonth: 1, lastMonth: 2 });
  // 31 Oct 17:00 UTC is already 1 Nov in the Philippines
  assert.deepEqual(monthCounts([inc("b1", { submittedAt: T(Date.UTC(2026, 9, 31, 17)) })], NOW), { thisMonth: 0, lastMonth: 0 });
});

test("weekly medians: six bins, oldest first", () => {
  const r = (id, ageDays, mins) => inc(id, { status: "resolved", submittedAt: T(NOW - ageDays * 86400e3) }, { resolvedMs: NOW - ageDays * 86400e3 + mins * 60000 });
  const w = weeklyMedians([r("a1", 1, 10), r("a2", 2, 30), r("a3", 9, 60), r("a4", 100, 5)], NOW, 6);
  assert.equal(w.length, 6);
  assert.deepEqual(w[5], { n: 2, value: 20 });
  assert.deepEqual(w[4], { n: 1, value: 60 });
  assert.equal(w[0].value, null);
});

test("reports: type counts, purok counts, heatmap", () => {
  const list = [inc("a1", { fireType: "vehicle" }), inc("a2", { fireType: "vehicle" }), inc("a3", { fireType: "structural", reporterId: "u2" })];
  assert.deepEqual(typeCounts(list).map(t => [t.id, t.n]), [["vehicle", 2], ["structural", 1]]);
  const users = [{ id: "u1", purok: "Purok 3" }, { id: "u2", purok: "" }];
  assert.deepEqual(purokCounts(list, users), [{ label: "Purok 3", n: 2 }]);
  const g = hourHeat([inc("a1", { submittedAt: T(Date.UTC(2026, 9, 22, 12, 30)) })]);     // Thu 20:30 PH
  assert.equal(g[3][10], 1);
  assert.equal(g.flat().reduce((a, b) => a + b, 0), 1);
  assert.equal(busiestSlot(g), "Thu 8 PM to 10 PM");
  assert.equal(busiestSlot(hourHeat([])), null);
});

test("activity: events from incidents, merge, filter, headings", () => {
  const users = [{ id: "r1", fullName: "Ana Cruz" }];
  const list = [inc("a1", { reporterName: "Maria" }), inc("a2", { status: "resolved" }, { resolvedMs: NOW - 1000, resolvedBy: "r1" })];
  const ev = incidentEvents(list, users);
  assert.deepEqual(ev.map(e => e.kind).sort(), ["reported", "reported", "resolved"]);
  assert.equal(ev.find(e => e.kind === "resolved").who, "Ana Cruz");
  const merged = mergeEvents(ev, [{ ms: NOW, kind: "assign", who: "Admin", verb: "", what: "assigned X" }], 3);
  assert.equal(merged.length, 3);
  assert.equal(merged[0].kind, "assign");
  assert.equal(filterEvents(merged, "assign").length, 1);
  assert.equal(filterEvents(merged, "report").every(e => ["reported", "resolved"].includes(e.kind)), true);
  assert.equal(filterEvents(merged, "all", "assigned x").length, 1);
  assert.equal(dayHeading(NOW, NOW), "Today");
  assert.equal(dayHeading(NOW - 86400e3, NOW), "Yesterday");
  assert.match(dayHeading(NOW - 5 * 86400e3, NOW), /^2026-10-17$/);
  assert.equal(clockText(Date.UTC(2026, 9, 22, 13, 5)), "9:05 PM");
});

test("activity entry to save: only known kinds, short text, no extra fields", () => {
  const me = { id: "admin1", name: "Admin One" };
  assert.deepEqual(activityDoc("verify", "verified INC-1", me, "abc"), { type: "verify", text: "verified INC-1", actorId: "admin1", actorName: "Admin One", incidentId: "abc" });
  assert.deepEqual(Object.keys(activityDoc("role", "x", me)).sort(), ["actorId", "actorName", "text", "type"]);
  assert.equal(activityDoc("delete", "x", me), null);
  assert.equal(activityDoc("verify", "   ", me), null);
  assert.equal(activityDoc("verify", "x".repeat(500), me).text.length, 200);
});

test("new report alerts: nothing at page load, then only unseen unreviewed reports", () => {
  const seen = new Set();
  const a = inc("a1"), b = inc("b1");
  assert.deepEqual(newReports([a], seen), []);
  assert.deepEqual(newReports([a, b], seen).map(i => i.id), ["b1"]);
  assert.deepEqual(newReports([a, b], seen), []);
  const c = inc("c1", { status: "resolved" });
  assert.deepEqual(newReports([a, b, c], seen), []);
});

test("unassigned warning: only verified-and-open reports with nobody assigned for over 5 minutes", () => {
  const old = inc("a1", { status: "verified", submittedAt: T(NOW - 10 * 60000) }, { assignLoaded: true, res: [] });
  const fresh = inc("a2", { status: "verified", submittedAt: T(NOW - 60000) }, { assignLoaded: true, res: [] });
  const taken = inc("a3", { status: "verified", submittedAt: T(NOW - 10 * 60000) }, { assignLoaded: true, res: [["u", "BFP", "Ana", []]] });
  const unknown = inc("a4", { status: "verified", submittedAt: T(NOW - 10 * 60000) });
  assert.deepEqual(unassignedTooLong([old, fresh, taken, unknown], NOW).map(i => i.id), ["a1"]);
});

test("report wording: the right article for each fire type", async () => {
  const { fireName, withArticle } = await import("../js/viewRules.js");
  assert.equal(withArticle(fireName("structural")), "a house fire");
  assert.equal(withArticle(fireName("electrical")), "an electrical fire");
  assert.equal(withArticle(fireName("other")), "a fire");
  assert.equal(withArticle(fireName("???")), "a fire");
});

test("sortRecords and nextSort: a column header flips its direction", () => {
  const mk = (id, severity, submittedMs) => ({ id, severity, submittedMs });
  const a = mk("a1", "Low", 1), b = mk("b1", "Critical", 2), c = mk("c1", "Low", 3);
  assert.deepEqual(sortRecords([a, b, c], "sevlow").map(i => i.id), ["a1", "c1", "b1"]);
  assert.deepEqual(sortRecords([a, b, c], "old").map(i => i.id), ["a1", "b1", "c1"]);
  assert.equal(nextSort("sev", "sev"), "sevlow");
  assert.equal(nextSort("sevlow", "sev"), "sev");
  assert.equal(nextSort("new", "sev"), "sev");
  assert.equal(nextSort("new", "new"), "old");
  assert.equal(nextSort("old", "new"), "new");
  assert.equal(nextSort("sev", "new"), "new");
});
