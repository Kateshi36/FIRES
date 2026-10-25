// AI suggestion panel of the admin website (advice only). No Firebase or DOM imports, so it is tested alone.
// The panel never writes anything: the administrator still verifies, closes or assigns by hand, and the
// responders set the severity in the app. Only the report text goes to the AI. No name, phone or photo.
import { esc } from "./mapping.js";

const R = 6371000;
export function metersBetween(a, b) {
  const rad = x => x * Math.PI / 180, dLat = rad(b.lat - a.lat), dLng = rad(b.lng - a.lng);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(h));
}

export const NEARBY_METERS = 300;
export const NEARBY_MINUTES = 120;
export const NEARBY_MAX = 5;

// Other OPEN reports close in place and time. The AI is only asked about these, never about the whole list.
export function nearbyCandidates(incident, all) {
  if (incident.lat == null || incident.submittedMs == null) return [];
  const here = { lat: incident.lat, lng: incident.lng };
  return all
    .filter(o => o.id !== incident.id && o.active && o.lat != null && o.submittedMs != null)
    .map(o => ({ o, m: metersBetween(here, { lat: o.lat, lng: o.lng }), min: Math.abs(incident.submittedMs - o.submittedMs) / 60000 }))
    .filter(x => x.m <= NEARBY_METERS && x.min <= NEARBY_MINUTES)
    .sort((a, b) => a.m - b.m)
    .slice(0, NEARBY_MAX)
    .map(({ o, m, min }) => ({
      id: o.id, code: o.code, fireType: o.plain.fireType, description: o.plain.description, address: o.plain.address,
      metersApart: Math.round(m), minutesApart: Math.round(min)
    }));
}

export function buildPayload(incident, all) {
  const p = incident.plain;
  return {
    report: {
      fireType: p.fireType, description: p.description, address: p.address, fireSize: p.fireSize,
      peopleAtRisk: p.peopleAtRisk, trapped: p.trapped, vulnerable: p.vulnerable, hazards: p.hazards,
      formSeverity: p.severity
    },
    nearby: nearbyCandidates(incident, all)
  };
}

export const AI_NOTE = "Advice only. The report text and photo are sent to an AI service (no name or phone). You decide.";
const LEVEL = { low: "Low", medium: "Medium", high: "High", critical: "Critical" };

// Turns a failed call into one plain sentence.
export function failText(status) {
  if (status === 401) return "Sign in again, then try once more.";
  if (status === 403) return "Only administrators can use the AI suggestion.";
  if (status === 429) return "The free AI limit is reached for now. Try again in a minute.";
  if (status === 504 || status === "timeout") return "The AI did not answer in time. Try again, or decide without it.";
  return "The AI suggestion is not available right now. You can still review the report yourself.";
}

// Calls the Worker. Resolves { ok:true, answer } or { ok:false, text }. Never throws.
export async function requestSuggestion(endpoint, idToken, payload, fetchFn = fetch, timeoutMs = 15000) {
  try {
    const res = await fetchFn(endpoint, {
      method: "POST",
      headers: { "content-type": "application/json", Authorization: "Bearer " + idToken },
      body: JSON.stringify(payload),
      signal: typeof AbortSignal !== "undefined" && AbortSignal.timeout ? AbortSignal.timeout(timeoutMs) : undefined
    });
    if (!res.ok) {
      let detail = "";
      try { detail = String((await res.json()).detail || ""); } catch { /* no body */ }
      return { ok: false, text: failText(res.status) + (detail ? " (" + detail + ")" : "") };
    }
    const a = await res.json();
    if (!a || !LEVEL[a.severity]) return { ok: false, text: failText(502) };
    return { ok: true, answer: a };
  } catch (e) {
    return { ok: false, text: failText(e && e.name === "TimeoutError" ? "timeout" : 0) };
  }
}

// Text part of the AI advice panel. It starts by itself, so there is no "Get" button: only "Try again" after a failure.
// state: { status: "idle"|"loading"|"done"|"error", answer, text }. `all` finds the duplicate's code.
export function suggestSectionHtml(incident, state = {}, all = []) {
  if (!incident.active) return "";
  const st = state.status || "idle";
  const head = `<div id="aitx" class="aisec"><b>Severity and duplicates</b>`;
  if (st === "loading" || st === "idle") return `${head}<p class="muted" style="margin:4px 0 0">Reading the report...</p></div>`;
  if (st === "done" && state.answer) {
    const a = state.answer, level = LEVEL[a.severity];
    const differs = level !== incident.severity;
    const dup = a.duplicateOf ? all.find(o => o.id === a.duplicateOf) : null;
    return `${head}`
      + `<div style="margin:4px 0"><span class="pill ${esc(level)}">${esc(level)}</span> <span class="muted">${Math.round(a.confidence * 100)}% sure</span></div>`
      + (differs ? `<p class="muted" style="margin:4px 0">The form score says <b>${esc(incident.severity)}</b>. Look at this report twice.</p>` : `<p class="muted" style="margin:4px 0">Matches the form score.</p>`)
      + `<ul style="margin:4px 0 4px 18px;padding:0">${a.reasons.map(r => `<li>${esc(r)}</li>`).join("")}</ul>`
      + (dup ? `<p style="margin:6px 0"><b>Looks like the same fire as</b> <a href="#" class="lnk" data-aid="${esc(dup.id)}">${esc(dup.code)}</a>${a.duplicateReason ? `<br><span class="muted">${esc(a.duplicateReason)}</span>` : ""}</p>` : "")
      + `</div>`;
  }
  return `${head}<p class="ferr" role="alert" style="margin:4px 0">${esc(state.text || failText(0))}</p><button type="button" class="btn ghost" id="aib">Try again</button></div>`;
}
