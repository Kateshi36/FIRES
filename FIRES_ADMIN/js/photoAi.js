// AI photo check (Phase AI), website side. The administrator's browser sends the report's photo to the
// Cloudflare Worker (/photo), which asks Gemini and returns a checked answer. The answer is saved once on
// the incident (six ai* fields) and shown here and in the responder app as ADVICE ONLY: it never changes
// a status, a severity or who is notified. No Firebase or DOM imports, so it is tested alone.
import { esc } from "./mapping.js";
import { failText } from "./aiSuggest.js";

export const PHOTO_AI_NOTE = "Advice only. The administrator decides.";
export const GENERATED_NOTE = "This photo may be AI-generated or edited. Look at it carefully.";
const HEAD = { yes: "Fire likely", no: "No fire seen", unsure: "Not sure there is a fire" };
const DETECTED = ["yes", "no", "unsure"], LEVELS = ["none", "low", "medium", "high", "critical"];

// The Worker address for photos, made from the text address in config.js (".../suggest" -> ".../photo").
export const photoEndpoint = ep => (ep ? String(ep).replace(/\/suggest\/?$/, "") + "/photo" : "");

// An open report that has a photo and no AI answer yet.
export const needsPhotoCheck = i => !!(i && i.photo && !i.photoAi && i.active);

// "data:image/jpeg;base64,...." -> { mimeType, base64 }, or null.
export function splitDataUrl(url) {
  const m = /^data:(image\/(?:jpeg|png|webp));base64,([A-Za-z0-9+/=]+)$/.exec(String(url || ""));
  return m ? { mimeType: m[1], base64: m[2] } : null;
}

// The Worker's answer -> the six fields saved on the incident, or null when it does not fit.
export function toAiFields(a) {
  if (!a || !DETECTED.includes(a.fireDetected) || !LEVELS.includes(a.severity)) return null;
  const c = Number(a.confidence);
  if (!Number.isFinite(c)) return null;
  const reason = String(a.reason ?? "").replace(/\s+/g, " ").trim().slice(0, 200);
  if (!reason) return null;
  return {
    aiFireDetected: a.fireDetected, aiSeverity: a.severity, aiConfidence: Math.min(1, Math.max(0, c)),
    aiGenerated: ["yes", "no", "unsure"].includes(a.aiGenerated) ? a.aiGenerated : "unsure",
    aiReason: reason, aiPromptVersion: String(a.promptVersion || "").slice(0, 20), aiModel: String(a.model || "").slice(0, 60)
  };
}

// Calls the Worker. Resolves { ok:true, answer } or { ok:false, text }. Never throws.
export async function requestPhotoCheck(endpoint, idToken, photo, fetchFn = fetch, timeoutMs = 30000) {
  try {
    const res = await fetchFn(endpoint, {
      method: "POST",
      headers: { "content-type": "application/json", Authorization: "Bearer " + idToken },
      body: JSON.stringify(photo),
      signal: typeof AbortSignal !== "undefined" && AbortSignal.timeout ? AbortSignal.timeout(timeoutMs) : undefined
    });
    if (!res.ok) {
      let detail = "";
      try { detail = String((await res.json()).detail || ""); } catch { /* no body */ }
      return { ok: false, text: failText(res.status).replace("AI suggestion", "AI photo check") + (detail ? " (" + detail + ")" : "") };
    }
    const a = await res.json();
    if (!toAiFields(a)) return { ok: false, text: failText(502).replace("AI suggestion", "AI photo check") };
    return { ok: true, answer: a };
  } catch (e) {
    return { ok: false, text: failText(e && e.name === "TimeoutError" ? "timeout" : 0).replace("AI suggestion", "AI photo check") };
  }
}

// Compare with the form-based score only when the AI saw a fire at some level.
export function photoMismatch(i) {
  const a = i.photoAi;
  if (!a || a.fire !== "yes" || a.severity === "none") return false;
  return a.severity !== String(i.plain?.severity || "low");
}

// Photo part of the AI advice panel. Starts by itself, so only "Try again" after a failure.
// state: { status: "idle"|"loading"|"error", text }. Returns "" when there is no photo to check.
export function photoSectionHtml(i, state = {}) {
  const a = i.photoAi, head = `<div id="pai" class="aisec"><b>Photo</b>`;
  if (a) {
    const lvl = a.severity === "none" ? "" : a.severity[0].toUpperCase() + a.severity.slice(1);
    const line = a.fire === "yes" && lvl ? `<div>Severity: ${esc(lvl)} (${Math.round(a.confidence * 100)}%)</div>` : "";
    const warn = photoMismatch(i)
      ? `<div class="muted" id="aimis">The form score says ${esc(i.severity)}. Look at this report twice.</div>` : "";
    const gen = a.generated === "yes" ? `<div class="ferr" id="aigen" role="alert" style="margin:4px 0">${esc(GENERATED_NOTE)}</div>` : "";
    return `${head}${gen}<div>${HEAD[a.fire]}</div>${line}${a.reason ? `<div>${esc(a.reason)}</div>` : ""}${warn}</div>`;
  }
  if (!needsPhotoCheck(i)) return "";
  const st = state.status || "idle";
  if (st === "error") return `${head}<p class="ferr" role="alert" style="margin:4px 0">${esc(state.text || failText(0))}</p><button type="button" class="btn ghost" id="pcb">Try again</button></div>`;
  return `${head}<p class="muted" style="margin:4px 0 0">Reading the photo...</p></div>`;
}
