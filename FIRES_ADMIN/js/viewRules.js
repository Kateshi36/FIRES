// Pure helpers for the Overview, Incident records, User accounts, Reports and Activity log screens.
// No Firebase and no DOM, so every rule here can be tested on its own (tests/viewRules.test.mjs).
import { fmtPH } from "./mapping.js";

const MIN = 60000, HOUR = 3600000, DAY = 86400000;

// ---- labels ----
export const FIRE_TYPES = {
  structural: "House or building", electrical: "Electrical", rubbish: "Rubbish",
  vehicle: "Vehicle", other: "Other"
};
export const typeLabel = t => FIRE_TYPES[t] || "Other";
// "house fire", "electrical fire" ... and "fire" when the type is not known. With the right article: "a house fire".
const FIRE_NAMES = { structural: "house fire", electrical: "electrical fire", rubbish: "rubbish fire", vehicle: "vehicle fire", other: "fire" };
export const fireName = t => FIRE_NAMES[t] || "fire";
export const withArticle = w => (/^[aeiou]/i.test(w) ? "an " : "a ") + w;
export const SEV_RANK = { Critical: 4, High: 3, Medium: 2, Low: 1 };

// ---- Incident records ----
// Waiting for a decision: not reviewed yet, or still open and flagged as duplicate / suspicious.
export const needsReview = i => i.rawStatus === "reported" || ((i.isSuspicious || i.isDuplicate) && i.active);
export const isClosedOut = i => i.status === "False" || i.status === "Duplicate";

export const CHIPS = [
  { id: "all", label: "All", test: () => true },
  { id: "review", label: "Needs review", test: needsReview },
  { id: "active", label: "Active", test: i => i.active },
  { id: "resolved", label: "Resolved", test: i => i.rawStatus === "resolved" },
  { id: "closed", label: "False or duplicate", test: isClosedOut }
];

// Text, severity, type and date filters. `esc` is the HTML-escaped search text (the stored text is escaped).
export function filterRecords(list, f = {}) {
  const q = (f.q || "").toLowerCase();
  return list.filter(i =>
    (!q || (i.code + " " + i.description + " " + i.loc + " " + i.reporterName).toLowerCase().includes(q)) &&
    (!f.sv || i.severity === f.sv) &&
    (!f.ty || i.fireType === f.ty) &&
    (!f.d1 || i.submittedAt.slice(0, 10) >= f.d1) &&
    (!f.d2 || i.submittedAt.slice(0, 10) <= f.d2));
}
export function chipCounts(list) {
  return Object.fromEntries(CHIPS.map(c => [c.id, list.filter(c.test).length]));
}
export const applyChip = (list, id) => list.filter((CHIPS.find(c => c.id === id) || CHIPS[0]).test);

// sort: "sev" = most severe first, then newest; "new" = newest first.
export function sortRecords(list, sort) {
  const by = sort === "new"
    ? (a, b) => (b.submittedMs || 0) - (a.submittedMs || 0)
    : (a, b) => (SEV_RANK[b.severity] || 0) - (SEV_RANK[a.severity] || 0) || (b.submittedMs || 0) - (a.submittedMs || 0);
  return [...list].sort(by);
}

export function pageSlice(list, page, size = 10) {
  const total = list.length, pages = Math.max(1, Math.ceil(total / size));
  const p = Math.min(Math.max(1, page | 0 || 1), pages);
  const start = (p - 1) * size;
  return { items: list.slice(start, start + size), page: p, pages, total, from: total ? start + 1 : 0, to: Math.min(total, start + size) };
}

// "Ana Cruz, Ben Reyes" for the Responder column. loaded = the assignments were read.
export function responderText(i) {
  if (!i.assignLoaded) return null;                       // not read yet: the screen shows "..."
  const names = (i.res || []).map(r => r[2]);
  return names.length ? names.join(", ") : i.active ? "Unassigned" : "None";
}

// "4 min", "2 h", "3 d"
export function ageText(ms, now = Date.now()) {
  if (ms == null) return "-";
  const d = Math.max(0, now - ms);
  if (d < HOUR) return Math.max(1, Math.round(d / MIN)) + " min";
  if (d < DAY) return Math.round(d / HOUR) + " h";
  return Math.round(d / DAY) + " d";
}

// ---- numbers ----
export function median(nums) {
  const a = nums.filter(Number.isFinite).sort((x, y) => x - y);
  if (!a.length) return null;
  const m = a.length >> 1;
  return a.length % 2 ? a[m] : (a[m - 1] + a[m]) / 2;
}
export const minutesToResolve = i =>
  i.submittedMs != null && i.resolvedMs != null && i.resolvedMs >= i.submittedMs ? (i.resolvedMs - i.submittedMs) / MIN : null;
export const medianResolve = list => median(list.map(minutesToResolve).filter(x => x != null));

const phYm = ms => fmtPH(ms).slice(0, 7);
export function monthCounts(list, now = Date.now()) {
  const cur = phYm(now), [y, m] = cur.split("-").map(Number);
  const prev = m === 1 ? `${y - 1}-12` : `${y}-${String(m - 1).padStart(2, "0")}`;
  let thisMonth = 0, lastMonth = 0;
  for (const i of list) { if (i.submittedMs == null) continue; const k = phYm(i.submittedMs); if (k === cur) thisMonth++; else if (k === prev) lastMonth++; }
  return { thisMonth, lastMonth };
}

// Median minutes to resolve for each of the last `weeks` weeks (oldest first). null = nothing resolved.
export function weeklyMedians(list, now = Date.now(), weeks = 6) {
  const bins = Array.from({ length: weeks }, () => []);
  for (const i of list) {
    const m = minutesToResolve(i);
    if (m == null) continue;
    const k = Math.floor((now - i.submittedMs) / (7 * DAY));
    if (k >= 0 && k < weeks) bins[weeks - 1 - k].push(m);
  }
  return bins.map(b => ({ n: b.length, value: median(b) }));
}

// ---- Reports ----
export function typeCounts(list) {
  const out = {};
  for (const i of list) out[i.fireType || "other"] = (out[i.fireType || "other"] || 0) + 1;
  return Object.entries(out).map(([id, n]) => ({ id, label: typeLabel(id), n })).sort((a, b) => b.n - a.n);
}
// Reporter's purok, from the users list. Reporters without one are left out.
export function purokCounts(list, users) {
  const purok = new Map(users.map(u => [u.id, (u.purok || "").trim()]));
  const out = {};
  for (const i of list) { const p = purok.get(i.reporterId); if (p) out[p] = (out[p] || 0) + 1; }
  return Object.entries(out).map(([label, n]) => ({ label, n })).sort((a, b) => b.n - a.n || a.label.localeCompare(b.label));
}
export const DAYS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];
export const HOUR_LABELS = ["12a", "2a", "4a", "6a", "8a", "10a", "12p", "2p", "4p", "6p", "8p", "10p"];
// 7 weekdays (Mon first) x 12 two-hour slots, in Philippine time (UTC+8, no daylight saving).
export function hourHeat(list) {
  const g = Array.from({ length: 7 }, () => Array(12).fill(0));
  for (const i of list) {
    if (i.submittedMs == null) continue;
    const d = new Date(i.submittedMs + 8 * HOUR);
    g[(d.getUTCDay() + 6) % 7][Math.floor(d.getUTCHours() / 2)]++;
  }
  return g;
}
export function busiestSlot(grid) {
  let best = { n: 0, d: 0, h: 0 };
  grid.forEach((row, d) => row.forEach((n, h) => { if (n > best.n) best = { n, d, h }; }));
  if (!best.n) return null;
  const hr = h => { const x = h % 24, ap = x < 12 ? "AM" : "PM"; return `${x % 12 || 12} ${ap}`; };
  return `${DAYS[best.d]} ${hr(best.h * 2)} to ${hr(best.h * 2 + 2)}`;
}
export function withinHour(list) {
  const done = list.filter(i => minutesToResolve(i) != null);
  return { n: done.length, within: done.filter(i => minutesToResolve(i) <= 60).length };
}

// ---- Activity log ----
// Categories used by the filter chips. An entry can be in several.
export const ACTIVITY_CHIPS = [
  { id: "all", label: "Everything" }, { id: "report", label: "Reports" },
  { id: "assign", label: "Assignments" }, { id: "account", label: "Accounts" }, { id: "admin", label: "Admin actions" }
];
const KIND = {
  reported: { icon: "🔥", tone: "hi", cats: ["report"] },
  resolved: { icon: "✔", tone: "lo", cats: ["report"] },
  status: { icon: "🚒", tone: "info", cats: ["report"] },
  verify: { icon: "✔", tone: "lo", cats: ["report", "admin"] },
  false: { icon: "✖", tone: "neu", cats: ["report", "admin"] },
  duplicate: { icon: "⧉", tone: "neu", cats: ["report", "admin"] },
  assign: { icon: "👤", tone: "info", cats: ["assign", "admin"] },
  unassign: { icon: "👤", tone: "neu", cats: ["assign", "admin"] },
  role: { icon: "🔑", tone: "md", cats: ["account", "admin"] },
  create: { icon: "🔑", tone: "md", cats: ["account", "admin"] }
};
export const kindInfo = k => KIND[k] || { icon: "•", tone: "neu", cats: [] };
export const ACTIVITY_TYPES = Object.keys(KIND).filter(k => !["reported", "resolved", "status"].includes(k));

// Events that can be worked out from the incidents themselves. who / what are already HTML-escaped.
export function incidentEvents(list, users = []) {
  const name = new Map(users.map(u => [u.id, u.fullName]));
  const out = [];
  for (const i of list) {
    if (i.submittedMs != null) out.push({ ms: i.submittedMs, kind: "reported", who: i.reporterName, verb: "reported", what: `${withArticle(fireName(i.fireType))} at ${i.loc}`, id: i.id });
    if (i.resolvedMs != null) out.push({ ms: i.resolvedMs, kind: "resolved", who: name.get(i.resolvedBy) || "A responder", verb: "resolved", what: `${i.code} at ${i.loc}`, id: i.id });
    else if (["dispatched", "on_scene"].includes(i.rawStatus) && i.updatedMs != null && i.updatedMs !== i.submittedMs)
      out.push({ ms: i.updatedMs, kind: "status", who: "Responders", verb: "are on", what: `${i.code} at ${i.loc}`, id: i.id });
  }
  return out;
}
export function mergeEvents(a, b, limit = 80) {
  return [...a, ...b].sort((x, y) => y.ms - x.ms).slice(0, limit);
}
export function filterEvents(events, chip, q = "") {
  const t = q.toLowerCase();
  return events.filter(e => (chip === "all" || kindInfo(e.kind).cats.includes(chip)) &&
    (!t || `${e.who} ${e.verb} ${e.what}`.toLowerCase().includes(t)));
}
// "Today", "Yesterday" or "2026-10-20", in Philippine time.
export function dayHeading(ms, now = Date.now()) {
  const d = fmtPH(ms).slice(0, 10), today = fmtPH(now).slice(0, 10), yest = fmtPH(now - DAY).slice(0, 10);
  return d === today ? "Today" : d === yest ? "Yesterday" : d;
}
export const clockText = ms => {
  const t = fmtPH(ms).slice(11), [h, m] = t.split(":").map(Number);
  return `${h % 12 || 12}:${String(m).padStart(2, "0")} ${h < 12 ? "AM" : "PM"}`;
};

// What an administrator did, as the one-line text saved with the entry (plain text, max 200 chars).
export function activityDoc(type, text, actor, incidentId = "") {
  if (!ACTIVITY_TYPES.includes(type)) return null;
  const t = String(text || "").trim().slice(0, 200);
  if (!t) return null;
  const d = { type, text: t, actorId: String(actor.id || ""), actorName: String(actor.name || "Admin").slice(0, 80) };
  if (incidentId) d.incidentId = String(incidentId).slice(0, 40);
  return d;
}

// ---- new report alerts ----
// Returns the incidents in `list` that were not in `seen`, then adds them to it. The first call only
// fills `seen` (nothing is "new" at page load).
export function newReports(list, seen) {
  const first = seen.size === 0 && !seen.primed;
  const fresh = first ? [] : list.filter(i => !seen.has(i.id) && i.rawStatus === "reported");
  list.forEach(i => seen.add(i.id));
  seen.primed = true;
  return fresh;
}
// Active reports with nobody assigned for longer than `mins` (only ones whose assignments were read).
export function unassignedTooLong(list, now = Date.now(), mins = 5) {
  return list.filter(i => i.active && i.rawStatus !== "reported" && i.assignLoaded && !(i.res || []).length &&
    i.submittedMs != null && now - i.submittedMs > mins * MIN);
}
