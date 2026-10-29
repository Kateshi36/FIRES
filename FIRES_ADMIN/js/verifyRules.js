// Verify / False report / Duplicate on the website (W13). Pure functions, no Firebase imports, so
// this file can be tested on its own.
//
// It mirrors the Android app: IncidentActionRules (canReview, needsConfirmation, confirm wording) and
// IncidentRepository.setVerification (the exact write). The website does ONLY this review. It does
// not dispatch, mark on scene, resolve, override severity or chat: responders do those in the app,
// and resolving must stay there because the record is written in the same batch as the status.
//
import { esc, statusLabel } from "./mapping.js";

// kind is what the button stands for:
//   verify     the report is real          -> verification "verified",  status "verified"
//   false      the report is not real      -> verification "false",     status "dismissed"
//   duplicate  another report covers it    -> verification "duplicate", status "dismissed"

const KINDS = {
  verify: {
    label: "Verify", verification: "verified", status: "verified",
    done: "Report verified."
  },
  false: {
    label: "False report", verification: "false", status: "dismissed",
    done: "Report marked as false.",
    confirm: {
      title: "Mark as false report?",
      body: "The report will be closed and leave the dashboard. It also counts against this reporter's account when later reports are checked.",
      button: "Mark as false"
    }
  },
  duplicate: {
    label: "Duplicate", verification: "duplicate", status: "dismissed",
    done: "Report marked as duplicate.",
    confirm: {
      title: "Mark as duplicate?",
      body: "The report will be closed and leave the dashboard. Use this only when another report already covers the same fire.",
      button: "Mark as duplicate"
    }
  }
};

// The three buttons, in the order they are shown. Verify is the main action.
export const VERIFY_BUTTONS = ["verify", "false", "duplicate"].map(kind => ({
  kind, label: KINDS[kind].label, primary: kind === "verify"
}));

// Offered only on a report nobody has reviewed yet (status "reported"), as in the app. It uses the
// stored status (rawStatus), not the label, so "Ongoing" or "False" can never match by accident.
export const canVerify = incident => !!incident && incident.rawStatus === "reported";

export const isVerifyKind = kind => Object.prototype.hasOwnProperty.call(KINDS, kind);

// Closing a report as false or duplicate takes it off the dashboard, so it asks first. Verify does not.
export const needsConfirmation = kind => isVerifyKind(kind) && !!KINDS[kind].confirm;

// { title, body, button } for the confirm dialog, or null when no confirmation is needed.
export const confirmText = kind => (needsConfirmation(kind) ? { ...KINDS[kind].confirm } : null);

// Toast text after a successful save.
export const doneText = kind => (isVerifyKind(kind) ? KINDS[kind].done : "");

// The exact fields written to incidents/{id}: verification, status and updatedAt, and NOTHING else
// (the rules allow staff to change a few fields, and this write must stay within the app's).
// updatedAt is passed in, so the caller supplies serverTimestamp() at write time and this stays
// testable. Returns null for an unknown kind.
export function buildVerifyUpdate(kind, updatedAt) {
  if (!isVerifyKind(kind)) return null;
  const k = KINDS[kind];
  return { verification: k.verification, status: k.status, updatedAt };
}

// What the report looks like after the write, for patching the local copy: the stored values the
// screens read (rawStatus, verification) and whether it is still active (verified yes, dismissed no).
export function outcomeOf(kind) {
  if (!isVerifyKind(kind)) return null;
  const k = KINDS[kind];
  return { rawStatus: k.status, verification: k.verification, active: k.status !== "dismissed" };
}

// Updates the screen's own copy of the incident after a successful save, in place: the stored status
// (rawStatus), verification, whether it is still active, and the status label the screens show
// ("Verified", "False", "Duplicate"). Nothing else on it changes. The label is worked out with the
// same function the mapping uses, so it can never disagree with a freshly loaded incident. Returns
// the same object (or whatever it was given, when there is nothing to patch).
export function patchIncident(incident, kind) {
  const out = outcomeOf(kind);
  if (!incident || !out) return incident;
  incident.rawStatus = out.rawStatus;
  incident.verification = out.verification;
  incident.active = out.active;
  incident.status = statusLabel({ status: out.rawStatus, verification: out.verification, isDuplicate: incident.isDuplicate });
  return incident;
}

// ---- After a review was saved: what to redraw (W13.14) ----

// The screen behind the drawer, per tab, after a report changed status. Overview and Live map are
// drawn again from the data; Incident records only redraws its table (so the search text and filters
// the administrator typed stay as they are); User accounts and Reports do not show a report's status.
//   "screen"   draw the whole tab again     "filters"   run the tab's filters again     "none"   leave it
export const redrawAfterReview = tab =>
  tab === "over" || tab === "map" ? "screen" : tab === "rec" ? "filters" : "none";

// The open drawer after a review was saved, found again by id (so it is the report as it is NOW, not
// the object the drawer was first drawn from):
//   "close"    the report is gone from the lists: nothing left to show
//   "redraw"   its photo, assignments and messages are already loaded: draw the drawer again from them,
//              without reading them a second time (no "Loading..." flash)
//   "reload"   they are not loaded yet (or failed to load): load them, as when the drawer first opens
export const drawerAfterReview = incident =>
  !incident ? "close" : incident.detailState === "ready" ? "redraw" : "reload";

// ---- The drawer's "Review this report" block and its confirmation dialog (W13) ----

// The block under "Review flags" in the incident drawer: the three buttons, and only on a report
// nobody has reviewed yet (canVerify). Returns "" otherwise, so a reviewed or closed report shows
// nothing at all. `st` is the state of a save that is running or has failed, so drawing the drawer
// again (it is redrawn when the details finish loading) cannot lose it or allow a second click:
//   { busy, kind, error }   busy: disable every button, and the one in `kind` says "Saving..."
//                           error: text shown under the buttons; the button in `kind` says "Try again"
// Nothing here comes from a citizen: labels are constants and the error text is ours (escaped anyway).
export function reviewBlockHtml(incident, st = {}) {
  if (!canVerify(incident)) return "";
  // wait: the report's details are still loading and the drawer will be redrawn once, so the buttons stay
  // disabled until then (a click on a button that is about to be replaced would be lost).
  const busy = st.busy === true || st.wait === true;
  const label = b => (st.busy === true && st.kind === b.kind ? "Saving..." : st.error && st.kind === b.kind ? "Try again" : b.label);
  const buttons = VERIFY_BUTTONS.map(b =>
    `<button class="btn${b.primary ? "" : " ghost"}" data-rv="${b.kind}"${busy ? " disabled" : ""}>${label(b)}</button>`).join("");
  return `<div class="kv" id="rvw"><small>Review this report</small>`
    + `<p class="muted" style="margin:4px 0 8px">Nobody has reviewed this report yet. Verify it, or close it as false or as a duplicate.</p>`
    + `<div class="rvbtns">${buttons}</div>`
    + `<div class="ferr" id="rv" role="alert">${esc(st.error || "")}</div></div>`;
}

// What the confirmation dialog for False report / Duplicate says: { title, body, confirm }, or null for
// Verify (no confirmation) and for an unknown kind. `body` is HTML: it names the report first, so the
// person can see which one they are closing. incident.code and incident.loc are already safe for HTML
// (mapIncident), and the explanation is escaped here.
export function reviewDialog(incident, kind) {
  const c = confirmText(kind);
  if (!c || !incident) return null;
  return {
    title: c.title,
    body: `<p><b>${incident.code}</b><br><span class="muted">${incident.loc}</span></p><p>${esc(c.body)}</p>`,
    confirm: c.button
  };
}
