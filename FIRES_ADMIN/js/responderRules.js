// Pure rules for showing where the responders are (W14). The phone app writes one document per responder under
// incidents/{id}/responderLocations/{responderId} while the incident is DISPATCHED and deletes it when the
// responder stops or arrives. These functions turn those documents into what the website shows.
// Mirrors util/ResponderTrackingRules.kt in the app (a location older than 60 s is no longer "live").
import { toMs, esc } from "./mapping.js";

export const STALE_MS = 60000;

const num = v => (typeof v === "number" && Number.isFinite(v) ? v : null);
const pairs = list => {
  if (!Array.isArray(list)) return [];
  const out = [];
  for (let k = 0; k + 1 < list.length; k += 2) {
    const a = num(list[k]), b = num(list[k + 1]);
    if (a != null && b != null) out.push([a, b]);
  }
  return out;
};

// d = the stored document. A write still waiting for the server has no time yet: it is brand new.
export function mapResponderLocation(id, d, nowMs = Date.now()) {
  const lat = num(d && d.latitude), lng = num(d && d.longitude);
  if (lat == null || lng == null || lat < -90 || lat > 90 || lng < -180 || lng > 180) return null;
  const updatedMs = toMs(d.updatedAt) ?? nowMs;
  return {
    id, lat, lng, heading: num(d.heading), etaSeconds: num(d.etaSeconds), distanceMeters: num(d.distanceMeters),
    route: pairs(d.routePath), trail: pairs(d.trail), updatedMs, fresh: nowMs - updatedMs < STALE_MS
  };
}

export function sinceText(ms, now = Date.now()) {
  const s = Math.max(0, Math.round((now - ms) / 1000));
  if (s < 10) return "just now";
  if (s < 60) return s + " s ago";
  if (s < 3600) return Math.round(s / 60) + " min ago";
  return Math.round(s / 3600) + " h ago";
}
export const etaText = sec => sec == null ? "" : "about " + Math.max(1, Math.round(sec / 60)) + " min";
export const distText = m => m == null ? "" : m < 1000 ? Math.round(m) + " m" : (m / 1000).toFixed(1) + " km";

// raw = the stored status. locs = mapped locations (nulls are ignored). nameOf(id) gives a plain name.
// Returns { kind: "none" | "waiting" | "live" | "stale" | "arrived", text, items: [...] } with PLAIN text:
// escape the names before putting them in HTML (see responderHtml).
export function summarize(raw, locs, nowMs, nameOf = () => "A responder") {
  if (raw === "on_scene") return { kind: "arrived", text: "Responders have arrived at the scene.", items: [] };
  if (raw !== "dispatched") return { kind: "none", text: "", items: [] };
  const items = (locs || []).filter(Boolean).map(l => {
    const name = nameOf(l.id) || "A responder";
    return {
      ...l, name, line: l.fresh
        ? name + " is on the way, updated " + sinceText(l.updatedMs, nowMs)
        : name + "'s location was last updated " + sinceText(l.updatedMs, nowMs)
    };
  });
  if (!items.length) return { kind: "waiting", text: "Waiting for a responder to share a location.", items };
  return { kind: items.some(x => x.fresh) ? "live" : "stale", text: items.map(x => x.line).join("\n"), items };
}

export const isTracked = raw => raw === "dispatched";

// The text as safe HTML (names are user text).
export const responderHtml = s => s.kind === "none" ? "" : esc(s.text).split("\n").join("<br>");

// ---- the Responder box in the incident panel (live map side panel and the report drawer) ----
// assigned = [{ id, name }] of the responders assigned to the report (plain names); locs = mapped locations.
// Only assigned responders get a row. An assigned responder who is not sharing yet reads "Waiting for the
// responder's location", like the app. The ETA and the distance are for the panel only (not the map label), and
// never shown when the location is stale.
export function panelModel(raw, assigned, locs, nowMs) {
  if (raw === "on_scene") return { kind: "arrived", note: "Responders have arrived at the scene.", rows: [] };
  if (raw !== "dispatched") return { kind: "none", note: "", rows: [] };
  const by = new Map((locs || []).filter(Boolean).map(l => [l.id, l]));
  const rows = (assigned || []).map(a => {
    const l = by.get(a.id), name = a.name || "A responder";
    if (!l) return { id: a.id, name, state: "waiting", text: "Waiting for the responder's location", eta: "", dist: "", since: "" };
    if (!l.fresh) return { id: a.id, name, state: "stale", text: "Location last updated " + sinceText(l.updatedMs, nowMs), eta: "", dist: "", since: "" };
    return { id: a.id, name, state: "live", text: "On the way", eta: etaText(l.etaSeconds), dist: distText(l.distanceMeters), since: sinceText(l.updatedMs, nowMs) };
  });
  if (!rows.length) return { kind: "waiting", note: "Waiting for the responder's location.", rows };
  return { kind: rows.some(r => r.state === "live") ? "live" : rows.some(r => r.state === "stale") ? "stale" : "waiting", note: "", rows };
}

export function panelHtml(m) {
  if (m.kind === "none") return "";
  if (m.note) return `<div>${esc(m.note)}</div>`;
  return m.rows.map(r => r.state === "live"
    ? `<div class="rrow"><b>${esc(r.name)}</b> <span class="muted">${esc(r.text)}</span>
<div class="rgrid"><div><small>ETA</small><b>${esc(r.eta || "-")}</b></div><div><small>Distance</small><b>${esc(r.dist || "-")}</b></div><div><small>Updated</small><b>${esc(r.since)}</b></div></div></div>`
    : `<div class="rrow"><b>${esc(r.name)}</b><br><span class="muted">${esc(r.text)}</span></div>`).join("");
}
