// Cloudflare Worker: the small "backend" of the F.I.R.E.S. AI helper.
// It keeps the Gemini API key secret, accepts calls only from a signed-in ADMINISTRATOR of project
// fires-a81c0, sends a short text-only prompt to Gemini and returns a validated suggestion.
// It never writes to Firestore and never changes a report.
import { PROMPT_VERSION, SYSTEM_PROMPT, sanitizeRequest, buildPrompt, parseAnswer, verifyIdToken, makeLimiter } from "./core.js";

const JWKS_URL = "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";
const limiter = makeLimiter(20, 60_000);   // 20 suggestions per minute per person
let jwksCache = { at: 0, keys: [] };

async function getKeys() {
  if (Date.now() - jwksCache.at < 3_600_000 && jwksCache.keys.length) return jwksCache.keys;
  const res = await fetch(JWKS_URL);
  if (!res.ok) throw new Error("keys unavailable");
  jwksCache = { at: Date.now(), keys: (await res.json()).keys || [] };
  return jwksCache.keys;
}

const cors = (origin, env) => {
  const allowed = String(env.ALLOWED_ORIGINS || "").split(",").map(s => s.trim()).filter(Boolean);
  return allowed.includes(origin) ? {
    "Access-Control-Allow-Origin": origin,
    "Access-Control-Allow-Headers": "authorization, content-type",
    "Access-Control-Allow-Methods": "POST, OPTIONS",
    "Vary": "Origin"
  } : null;
};
const reply = (status, body, headers) =>
  new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json", ...headers } });

// The role lives in users/{uid}. The person's own token is used to read their own document.
async function isAdmin(uid, token, projectId) {
  const url = `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents/users/${encodeURIComponent(uid)}`;
  const res = await fetch(url, { headers: { Authorization: "Bearer " + token } });
  if (!res.ok) return false;
  const doc = await res.json();
  return doc && doc.fields && doc.fields.role && doc.fields.role.stringValue === "admin";
}

export default {
  async fetch(request, env) {
    const h = cors(request.headers.get("Origin") || "", env);
    if (request.method === "OPTIONS") return new Response(null, { status: h ? 204 : 403, headers: h || {} });
    if (!h) return reply(403, { error: "origin" }, {});
    const url = new URL(request.url);
    if (request.method !== "POST" || url.pathname !== "/suggest") return reply(404, { error: "not found" }, h);

    const projectId = env.FIREBASE_PROJECT_ID;
    const token = (request.headers.get("Authorization") || "").replace(/^Bearer\s+/i, "");
    let claims;
    try { claims = await verifyIdToken(token, await getKeys(), projectId); }
    catch { return reply(401, { error: "Sign in again." }, h); }
    if (!(await isAdmin(claims.sub, token, projectId))) return reply(403, { error: "Administrators only." }, h);
    if (!limiter(claims.sub)) return reply(429, { error: "Too many requests. Try again in a minute." }, h);

    let body;
    try { body = await request.json(); } catch { return reply(400, { error: "Bad request." }, h); }
    const input = sanitizeRequest(body);
    if (!input) return reply(400, { error: "The report has no text to read." }, h);

    const model = env.GEMINI_MODEL || "gemini-3.5-flash-lite";
    let res;
    try {
      res = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`, {
        method: "POST",
        headers: { "content-type": "application/json", "x-goog-api-key": env.GEMINI_API_KEY },
        body: JSON.stringify({
          systemInstruction: { parts: [{ text: SYSTEM_PROMPT }] },
          contents: [{ role: "user", parts: [{ text: buildPrompt(input) }] }],
          generationConfig: { temperature: 0, responseMimeType: "application/json", maxOutputTokens: 2048 }
        }),
        signal: AbortSignal.timeout(12_000)
      });
    } catch { return reply(504, { error: "The AI did not answer in time." }, h); }
    if (res.status === 429) return reply(429, { error: "The free AI quota is used up for now." }, h);
    if (!res.ok) {
      const why = (await res.text().catch(() => "")).slice(0, 300);
      console.error("Gemini error", res.status, why);
      return reply(502, { error: "The AI service is unavailable.", detail: `Gemini ${res.status}` }, h);
    }

    const data = await res.json();
    const text = data && data.candidates && data.candidates[0] && data.candidates[0].content
      && data.candidates[0].content.parts && data.candidates[0].content.parts[0] && data.candidates[0].content.parts[0].text;
    const answer = parseAnswer(text, input.nearby);
    if (!answer) {
      console.error("Unusable answer", String(text).slice(0, 300), JSON.stringify(data && data.promptFeedback || ""));
      return reply(502, { error: "The AI gave an answer we could not use.", detail: "bad answer" }, h);
    }
    return reply(200, { ...answer, promptVersion: PROMPT_VERSION, model }, h);
  }
};
