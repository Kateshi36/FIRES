// Pure helpers that turn Firestore documents into the objects the screens expect (W7).
// No Firebase imports here, so this file can be tested on its own.
//
// SECURITY: the screens build HTML strings, and citizens write the text (description, address,
// names, chat). So every free-text value is HTML-escaped here, once, when it is converted.

export const esc = v => String(v ?? "").replace(/[&<>"']/g, c =>
  ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

const cap = s => s ? s[0].toUpperCase() + s.slice(1) : "";

// Firestore Timestamp (or Date) -> milliseconds, or null.
export const toMs = t => !t ? null : typeof t.toMillis === "function" ? t.toMillis()
  : t instanceof Date ? t.getTime() : null;

// Milliseconds -> "YYYY-MM-DD HH:mm" in Philippine time (UTC+8, no daylight saving).
const PH = new Intl.DateTimeFormat("en-CA", {
  timeZone: "Asia/Manila", year: "numeric", month: "2-digit", day: "2-digit",
  hour: "2-digit", minute: "2-digit", hourCycle: "h23"
});
export function fmtPH(ms) {
  if (ms == null) return "";
  const p = Object.fromEntries(PH.formatToParts(new Date(ms)).map(x => [x.type, x.value]));
  return `${p.year}-${p.month}-${p.day} ${p.hour}:${p.minute}`;
}

// "INC-" + first 6 characters of the document id, capitals. The full id stays in `id` for lookups.
export const incCode = docId => "INC-" + String(docId).slice(0, 6).toUpperCase();

// Website status label from the stored status / verification / isDuplicate.
export function statusLabel(d) {
  const st = d.status, v = d.verification;
  if (v === "duplicate" || d.isDuplicate === true) return "Duplicate";
  if (st === "dismissed" || v === "false") return "False";
  if (st === "dispatched" || st === "on_scene") return "Ongoing";
  if (st === "verified") return "Verified";
  if (st === "resolved") return "Resolved";
  return "Reported";
}

// Active = reported, verified, dispatched, on_scene (by the stored status, not the label).
export const isActiveStatus = st => ["reported", "verified", "dispatched", "on_scene"].includes(st);

export const severityLabel = s => ({ low: "Low", medium: "Medium", high: "High", critical: "Critical" }[s] || "Low");

// fullName, email, contactNo and address are escaped for the screens. `name` is the full name exactly
// as stored (plain text, NOT escaped): use it when the name is SAVED somewhere else, such as
// responderName on an assignment. Saving the escaped fullName would store "O&#39;Brien" and escape it
// a second time when it is read back. Never put `name` into HTML.
export function mapUser(id, d) {
  return {
    id,
    name: String(d.fullName ?? ""),
    fullName: esc(d.fullName), email: esc(d.email), contactNo: esc(d.contactNo),
    address: esc(d.address), purok: esc(d.purok), role: d.role || "citizen"
  };
}

// extra = { record, assignments } taken from the caches; either may be missing.
// The AI's photo advice saved by the app. Read-only here: the website never calls the AI for photos.
const AI_DETECTED = ["yes", "no", "unsure"], AI_LEVELS = ["none", "low", "medium", "high", "critical"];
export function mapPhotoAi(d) {
  if (!AI_DETECTED.includes(d.aiFireDetected)) return null;
  const level = AI_LEVELS.includes(d.aiSeverity) ? d.aiSeverity : "none";
  const c = Number(d.aiConfidence);
  return {
    fire: d.aiFireDetected, severity: level,
    confidence: Number.isFinite(c) ? Math.min(1, Math.max(0, c)) : 0,
    reason: String(d.aiReason ?? "").slice(0, 200),
    generated: AI_DETECTED.includes(d.aiGenerated) ? d.aiGenerated : null,   // "yes": may be AI-generated or edited
    version: String(d.aiPromptVersion ?? "").slice(0, 20), model: String(d.aiModel ?? "").slice(0, 60)
  };
}

export function mapIncident(id, d, extra = {}) {
  const lat = Number(d.latitude), lng = Number(d.longitude);
  const hasCoords = Number.isFinite(lat) && Number.isFinite(lng) && !(lat === 0 && lng === 0);
  const addr = String(d.addressText ?? "").trim();
  const submittedMs = toMs(d.submittedAt), updatedMs = toMs(d.updatedAt);
  const i = {
    id,                                   // full document id, used for every lookup and Firestore read
    code: incCode(id),                    // what the screens show
    reporterId: d.reporterId || "",
    reporterName: esc(d.reporterName || "Unknown"),
    reporterContact: esc(d.reporterContact || ""),
    description: esc(d.description),
    fireType: d.fireType || "other",
    loc: addr ? esc(addr) : hasCoords ? `${lat.toFixed(5)}, ${lng.toFixed(5)}` : "No location",
    lat: hasCoords ? lat : null, lng: hasCoords ? lng : null,
    locSource: d.locationSource === "pin" ? "Pin" : "GPS",
    severity: severityLabel(d.severity),
    status: statusLabel(d),
    rawStatus: d.status || "reported",
    verification: d.verification || "pending",
    active: isActiveStatus(d.status || "reported"),
    isDuplicate: d.isDuplicate === true,
    isSuspicious: d.isSuspicious === true,
    photo: d.hasPhoto === true,           // true when a photo exists; the image itself loads in the drawer
    photoUrl: null,
    photoAi: mapPhotoAi(d),               // the app's AI photo advice (read-only), or null
    submittedMs, updatedMs,
    submittedAt: fmtPH(submittedMs),
    dateResolved: null, resolvedMs: null, resolvedBy: "", remarks: "",
    res: [], msgs: [],
    assignLoaded: false, detailState: "idle",
    // Plain text (NOT escaped) for the AI helper only. Never put it into HTML. No name or phone here.
    plain: {
      fireType: d.fireType || "other", description: String(d.description ?? ""), address: addr,
      fireSize: d.fireSize || "small", peopleAtRisk: Number(d.peopleAtRisk) || 0, trapped: d.trapped === true,
      vulnerable: Array.isArray(d.vulnerablePersons) ? d.vulnerablePersons.map(String) : [],
      hazards: Array.isArray(d.hazards) ? d.hazards.map(String) : [],
      severity: String(d.severity || "low")
    }
  };
  if (extra.record) applyRecord(i, extra.record);
  if (extra.assignments && extra.assignments.updatedMs === updatedMs) {
    i.res = extra.assignments.res; i.assignLoaded = true;
  }
  return i;
}

export function applyRecord(i, r) {
  i.resolvedMs = toMs(r.dateResolved);
  i.dateResolved = i.resolvedMs != null ? fmtPH(i.resolvedMs) : null;
  i.resolvedBy = String(r.resolvedBy || "");
  i.remarks = esc(r.remarks);
}

// [responderId, unit, responderName, resources]  (the screens read r[0], r[1])
export const mapAssignment = d =>
  [d.responderId || "", esc(d.unit), esc(d.responderName || "Responder"),
   (Array.isArray(d.assignedResources) ? d.assignedResources : []).map(esc)];

// [sender, text, sentMs]
export const mapMessage = d =>
  [`${esc(d.senderName || "Unknown")} (${esc(d.senderRole || "citizen")})`, esc(d.messageText), toMs(d.sentAt)];

// incident_photos/{id}: base64 + mimeType -> data: URL. Only image types are accepted.
export function photoDataUrl(d) {
  const mime = /^image\/(jpeg|png|webp|gif)$/.test(d.mimeType || "") ? d.mimeType : "image/jpeg";
  const b64 = String(d.base64 || "").replace(/[^A-Za-z0-9+/=]/g, "");
  return b64 ? `data:${mime};base64,${b64}` : null;
}
