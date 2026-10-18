// Date ranges, counts, resolve times and CSV for the Reports screen (W12). Pure functions: the only
// import is mapping.js, which has no Firebase code, so this file can be tested on its own.
//
// Days are "YYYY-MM-DD" strings in Philippine time (UTC+8, no daylight saving), the same time zone
// the screens show. Day arithmetic goes through Date.UTC, so the browser's own time zone never
// shifts a day.
import { fmtPH } from "./mapping.js";

export const MAX_RANGE_DAYS = 366;
export const DAY_BARS_MAX = 31;   // longer ranges are drawn one bar per week

const DAY_RE = /^\d{4}-\d{2}-\d{2}$/;
const utc = day => { const [y, m, d] = day.split("-").map(Number); return Date.UTC(y, m - 1, d); };
const MS_DAY = 86400000;
// Date.UTC rolls 2026-13-45 over into a later date instead of failing, so check the round trip.
const isRealDay = day => DAY_RE.test(day) && new Date(utc(day)).toISOString().slice(0, 10) === day;

export const todayPH = (nowMs = Date.now()) => fmtPH(nowMs).slice(0, 10);

export function addDays(day, n) {
  const t = new Date(utc(day) + n * MS_DAY);
  return t.toISOString().slice(0, 10);
}

// Number of calendar days from..to, both included.
export const daysInRange = (from, to) => Math.round((utc(to) - utc(from)) / MS_DAY) + 1;

// The last `days` days, today included (30 -> today and the 29 days before).
export function lastDays(days, nowMs = Date.now()) {
  const to = todayPH(nowMs);
  return { from: addDays(to, -(days - 1)), to };
}
export const defaultRange = nowMs => lastDays(30, nowMs);

// First and last millisecond of the range, in Philippine time. Used for the Firestore query.
export function rangeBounds(from, to) {
  return {
    fromMs: Date.parse(`${from}T00:00:00+08:00`),
    toMs: Date.parse(`${to}T23:59:59.999+08:00`)
  };
}

// null when fine, otherwise the message to show.
export function rangeError(from, to) {
  if (!from || !to) return "Choose both dates.";
  if (!isRealDay(from) || !isRealDay(to)) return "Enter valid dates.";
  if (from > to) return "The start date must be on or before the end date.";
  if (daysInRange(from, to) > MAX_RANGE_DAYS) return "Choose a range of one year or less.";
  return null;
}

const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
// "2026-09-24" -> "24 Sep 2026"
export function fmtDay(day) {
  const [y, m, d] = day.split("-").map(Number);
  return `${d} ${MONTHS[m - 1]} ${y}`;
}
const shortDay = day => { const [, m, d] = day.split("-").map(Number); return `${d} ${MONTHS[m - 1]}`; };

// Incidents per day (ranges up to 31 days) or per week starting Monday (longer ranges).
// Every day or week in the range gets a bucket, including the empty ones, so the bars are even.
// Returns { unit, buckets: [{ key, label, title, count }] }.
export function bucketCounts(list, from, to) {
  const perDay = daysInRange(from, to) <= DAY_BARS_MAX;
  const keyOf = day => {
    if (perDay) return day;
    const dow = new Date(utc(day)).getUTCDay();   // 0 = Sunday
    return addDays(day, -((dow + 6) % 7));        // back to Monday
  };
  const buckets = [];
  const at = new Map();
  const step = perDay ? 1 : 7;
  for (let k = keyOf(from); k <= to; k = addDays(k, step)) {
    const b = {
      key: k,
      label: perDay ? String(Number(k.slice(8))) : shortDay(k),
      title: perDay ? fmtDay(k) : `Week of ${fmtDay(k)}`,
      count: 0
    };
    buckets.push(b); at.set(k, b);
  }
  for (const i of list) {
    if (i.submittedMs == null) continue;
    const day = fmtPH(i.submittedMs).slice(0, 10);
    if (day < from || day > to) continue;
    const b = at.get(keyOf(day));
    if (b) b.count++;
  }
  return { unit: perDay ? "day" : "week", buckets };
}

export function severityCounts(list) {
  const c = { Critical: 0, High: 0, Medium: 0, Low: 0 };
  for (const i of list) if (i.severity in c) c[i.severity]++;
  return c;
}

export const resolvedCount = list => list.filter(i => i.rawStatus === "resolved").length;

// Average minutes from the report being submitted (incident.submittedAt) to it being resolved
// (records.dateResolved). Only resolved incidents whose record has been read count; n says how many.
export function resolveStats(list) {
  const mins = [];
  for (const i of list) {
    if (i.submittedMs == null || i.resolvedMs == null || i.resolvedMs < i.submittedMs) continue;
    mins.push((i.resolvedMs - i.submittedMs) / 60000);
  }
  const n = mins.length;
  return { n, avgMin: n ? Math.round(mins.reduce((a, b) => a + b, 0) / n) : null };
}

export function fmtMin(m) {
  if (m == null) return "-";
  if (m < 60) return `${m} min`;
  const h = Math.floor(m / 60), r = m % 60;
  return r ? `${h} h ${r} min` : `${h} h`;
}

// ---- CSV ----
// A cell that starts with = + - @ (or a tab / carriage return) can be run as a formula when the file
// is opened in Excel, so it gets a leading apostrophe. Quotes are doubled and cells with a comma,
// quote or line break are wrapped in quotes.
export function csvCell(v) {
  let s = v == null ? "" : String(v);
  if (/^[=+\-@\t\r]/.test(s)) s = "'" + s;
  return /[",\r\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
}
export const toCsv = rows => rows.map(r => r.map(csvCell).join(",")).join("\r\n") + "\r\n";

// One row per incident. Only system fields: no names, phone numbers, addresses or descriptions,
// so the file can go into a thesis appendix without personal data.
export const CSV_HEADER = [
  "Incident ID", "Document ID", "Submitted (PH time)", "Severity", "Status", "Flags",
  "Photo", "Location source", "Resolved (PH time)", "Minutes to resolve"
];
export function reportCsv(list) {
  const rows = list.map(i => [
    i.code, i.id, i.submittedAt, i.severity, i.status,
    [i.isDuplicate && "Duplicate", i.isSuspicious && "Suspicious"].filter(Boolean).join(" + "),
    i.photo ? "Yes" : "No", i.locSource, i.dateResolved || "",
    i.resolvedMs != null && i.submittedMs != null && i.resolvedMs >= i.submittedMs
      ? Math.round((i.resolvedMs - i.submittedMs) / 60000) : ""
  ]);
  return toCsv([CSV_HEADER, ...rows]);
}

export const csvFileName = (from, to) => `fires-report-${from}_to_${to}.csv`;
