// One "AI advice" panel for the incident drawer: the photo check and the text suggestion together.
// Both start by themselves (see app.js), nothing here is a button except "Try again" after a failure.
// Advice only: it never changes a status, a severity or who is notified. No DOM or Firebase imports.
import { esc } from "./mapping.js";
import { AI_NOTE, suggestSectionHtml } from "./aiSuggest.js";
import { photoSectionHtml } from "./photoAi.js";

export function aiAdviceHtml(i, photoState, textState, endpoint = "", all = []) {
  const head = `<small>AI advice</small>`;
  if (!endpoint) return `<div class="kv" id="aip">${head}<p class="muted" style="margin:4px 0 0">Not set up yet.</p></div>`;
  const body = photoSectionHtml(i, photoState) + suggestSectionHtml(i, textState, all);
  if (!body) return "";
  return `<div class="kv" id="aip">${head}${body}<p class="muted small" style="margin:8px 0 0">${esc(AI_NOTE)}</p></div>`;
}
