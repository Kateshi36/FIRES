// Assign responder on the website (W13). Pure functions, no Firebase imports, so this file can be
// tested on its own.
//
// It mirrors the Android app: AssignmentRules (UNITS, RESOURCES, validateUnit, orderedResources) and
// IncidentActionRules.canAssign. The website assigns any RESPONDER ACCOUNT, where the app only lets
// a responder assign themselves, so it also needs the responder list and the "already assigned"
// check. firestore.rules allow the create for admins only (and only for a uid whose users document
// has role = responder), so every check here is a courtesy that gives a clear message first; the
// rules are the real protection.
import { esc, isActiveStatus } from "./mapping.js";
import { UNITS } from "./validators.js";
import { SAVE_TEXT, saveFailText } from "./saveMessages.js";

// Units: the same list as the app (AssignmentRules.UNITS), kept in validators.js and re-exported so
// there is one copy on the website.
export { UNITS };
export const RESOURCES = ["Fire truck", "Water tanker"];

export const MSG = {
  closed: "This report is closed, so nobody can be assigned to it.",
  notLoaded: "The assignments are still loading. Wait a moment and try again.",
  responderRequired: "Choose which responder to assign.",
  responderUnknown: "That account is not a responder. Choose someone from the list.",
  // No name in this message: it ends up in HTML, and names are free text.
  alreadyAssigned: "This responder is already assigned to this incident.",
  unitRequired: "Choose which unit is responding.",
  done: "Responder assigned.",
  // What the Assign dialog says instead of an empty list (W13.16).
  noResponders: "There are no responder accounts yet. Add one under User accounts.",
  allAssigned: "Every responder is already assigned to this incident.",
  // A Save that found a DIFFERENT assignment under its own id: an earlier try of this same dialog did reach
  // the server (its answer was lost) and the form was changed before trying again (W13.18).
  staleRefreshed: "An earlier try was already saved, so the list now shows it. Check it, then save again if you still want to assign someone else.",
  staleClosed: "An earlier try was already saved with different details. Close this and open the report again to check who is assigned."
};

// The only keys an assignments document may carry (firestore.rules validAssignmentFields).
export const ASSIGNMENT_KEYS = ["responderId", "responderName", "unit", "assignedResources", "assignedAt"];

// Offered only on an open report (reported, verified, dispatched, on scene), as in the app. It uses
// the stored status (rawStatus), not the label, so "Ongoing" or "False" can never match by accident.
export const canAssign = incident => !!incident && isActiveStatus(incident.rawStatus);

// The "Assign responder" button in the incident drawer, under "Assigned responders" (W13.15). Drawn only
// on an open report (canAssign), so a resolved or closed one shows nothing. It stays disabled until the
// report's assignments are loaded, because the "already assigned" check needs them (validateAssignment
// says the same). Nothing here comes from a citizen: the text and ids are constants.
export function assignButtonHtml(incident) {
  if (!canAssign(incident)) return "";
  const ready = incident.assignLoaded === true;
  return `<div class="rvbtns"><button class="btn ghost" id="asg"${ready ? "" : ' disabled title="The assignments are still loading."'}>Assign responder</button></div>`;
}

// mapUser() HTML-escapes names for the screens. A name must be saved as typed, or "O'Brien" is
// stored as "O&#39;Brien" and escaped again when it is read back. This undoes esc() in one pass.
const ENTITIES = { "&amp;": "&", "&lt;": "<", "&gt;": ">", "&quot;": '"', "&#39;": "'" };
export const unesc = v => String(v ?? "").replace(/&(?:amp|lt|gt|quot|#39);/g, m => ENTITIES[m]);

// The responder list for the Assign dialog, from the users loaded once (mapUser objects): accounts
// with role = responder, sorted by name. For each one:
//   id     the account's uid (saved as responderId)
//   name   the plain text to SAVE as responderName (full name, then e-mail, then "Responder", as in
//          the app), at most 100 characters (the rules' limit). The full name is mapUser's raw `name`,
//          so it is never un-escaped from the HTML copy
//   label  the same text escaped for HTML, for the list
//   unit   the unit stored on the account (users.address), or "" when it is not one of UNITS
export function responderOptions(users) {
  return (users || [])
    .filter(u => u && u.role === "responder" && u.id)
    .map(u => {
      const raw = typeof u.name === "string" ? u.name : unesc(u.fullName);   // older objects: undo the escaping
      const name = (raw.trim() || unesc(u.email).trim() || "Responder").slice(0, 100);
      return { id: u.id, name, label: esc(name), unit: defaultUnit(u) };
    })
    .sort((a, b) => a.name.localeCompare(b.name));
}

// The unit to preselect for a responder: what their account stores in `address` (Add responder saves
// the unit there, for example "B-FLARE"). Matched ignoring case and spaces around it, and returned
// as the exact value from UNITS. Anything else (an old street address, blank) gives "" so nothing is
// preselected and the admin chooses.
export function defaultUnit(user) {
  const stored = unesc(user && user.address).trim().toLowerCase();
  return UNITS.find(u => u.toLowerCase() === stored) || "";
}

// assignments are [responderId, unit, responderName, resources] (mapAssignment), read as r[0].
export const isAssigned = (incident, responderId) =>
  !!incident && Array.isArray(incident.res) && incident.res.some(r => r[0] === responderId);

// The chosen resources in the list's own order, so the saved list does not depend on tap order.
// Unknown values are dropped, as in the app. Accepts an array or a Set; nothing chosen is fine.
export const orderedResources = selected => {
  const s = new Set(selected || []);
  return RESOURCES.filter(r => s.has(r));
};

// Checks the Assign dialog before saving.
//   incident   the open incident (mapIncident object)
//   form       { responderId, unit, resources }
//   responders the list from responderOptions()
// Returns { errors: {responder, unit, general}, ok, clean }. `clean` is null unless ok, and is
// exactly the four fields to save (see buildAssignment). The name comes from the responder list,
// never from the form, so it always matches the account that was chosen.
export function validateAssignment(incident, form, responders) {
  const errors = { responder: null, unit: null, general: null };
  const f = form || {};

  if (!canAssign(incident)) errors.general = MSG.closed;
  // The "already assigned" check needs the incident's assignments. If they have not loaded yet, a
  // second assignment of the same person could slip through, so wait for them.
  else if (incident.assignLoaded !== true) errors.general = MSG.notLoaded;

  const chosen = (responders || []).find(r => r.id === f.responderId);
  if (!f.responderId) errors.responder = MSG.responderRequired;
  else if (!chosen) errors.responder = MSG.responderUnknown;
  else if (isAssigned(incident, chosen.id)) errors.responder = MSG.alreadyAssigned;

  if (!UNITS.includes(f.unit)) errors.unit = MSG.unitRequired;

  const ok = !Object.values(errors).some(Boolean);
  return {
    errors,
    ok,
    clean: ok
      ? { responderId: chosen.id, responderName: chosen.name, unit: f.unit, assignedResources: orderedResources(f.resources) }
      : null
  };
}

// The exact fields written to incidents/{id}/assignments: responderId, responderName, unit and
// assignedResources, and NOTHING else (the rules accept only ASSIGNMENT_KEYS).
//
// assignedAt is passed in, so the caller supplies serverTimestamp() at write time and this stays
// testable, like updatedAt in buildVerifyUpdate. It must be saved: the app lists assignments with
// orderBy("assignedAt"), and Firestore leaves out documents that do not have the field, so an
// assignment without it would never show in the responder's app. It is added only when passed.
// Returns null when given nothing valid to build from.
export function buildAssignment(clean, assignedAt) {
  if (!clean) return null;
  const data = {
    responderId: clean.responderId,
    responderName: clean.responderName,
    unit: clean.unit,
    assignedResources: [...clean.assignedResources]
  };
  if (assignedAt !== undefined) data.assignedAt = assignedAt;
  return data;
}

// What the report looks like after a save, for patching the local copy without another read: the
// same array shape mapAssignment() produces, with the name escaped for the screens.
export const assignmentRow = clean =>
  clean ? [clean.responderId, esc(clean.unit), esc(clean.responderName), clean.assignedResources.map(esc)] : null;

// The responders the Assign dialog offers (W13.16): the accounts with role = responder in `users` (the
// list already loaded, U), without the ones already assigned to this incident, sorted by name. Each
// entry is a responderOptions() entry (id, name, label, unit).
export const availableResponders = (incident, users) =>
  responderOptions(users).filter(r => !isAssigned(incident, r.id));

// The unit to show after the responder select changes (W13.17). Choosing a responder preselects the unit
// stored on their account (responder.unit, "" when there is none). Once the administrator has picked a
// unit themselves (`touched`), it is left alone: changing the responder never overwrites their choice.
// With no responder chosen (or an unknown unit) the untouched select goes back to "Choose a unit".
export const unitFor = (current, touched, responder) =>
  touched ? current : (responder && UNITS.includes(responder.unit) ? responder.unit : "");

// The Assign dialog (W13.16, W13.17): the report it is for, and the responder list, or a short message when
// nobody is left to choose. With a list it also has the unit select (nothing chosen at first; app.js
// preselects it from the responder, see unitFor) and the two resource checkboxes, which are optional.
// It keeps the ids the shared dialog code uses (dlg-t, dg, dno), and adds Save (asv) with a Cancel in place of
// Close when there is someone to choose; the field errors (ar-e, au-e) and the general one (dg) are filled by
// app.js from resultView() (W13.18). With nobody to choose it has only Close.
// The unit and resource names are constants (UNITS, RESOURCES), so they need no escaping. incident.code and incident.loc are already
// safe for HTML (mapIncident); responder labels are escaped by responderOptions; the ids are escaped here.
export function assignDialogHtml(incident, users) {
  const total = responderOptions(users).length;
  const list = availableResponders(incident, users);
  const head = `<h2 id="dlg-t">Assign responder</h2><p><b>${incident.code}</b><br><span class="muted">${incident.loc}</span></p>`;
  const body = list.length
    ? `<div class="form"><label>Responder<select id="ar"><option value="">Choose a responder</option>`
      + `${list.map(r => `<option value="${esc(r.id)}">${r.label}</option>`).join("")}</select>`
      + `<span class="ferr" id="ar-e"></span></label>`
      + `<label>Unit<select id="au"><option value="">Choose a unit</option>${UNITS.map(u => `<option value="${u}">${u}</option>`).join("")}</select>`
      + `<span class="ferr" id="au-e"></span></label></div>`
      + `<fieldset id="ares" style="border:0;padding:0;margin:14px 0 0"><legend style="font-weight:600;font-size:13px;padding:0 0 4px">Resources (optional)</legend>`
      + RESOURCES.map(r => `<label style="display:flex;align-items:center;gap:8px;margin:4px 0;font-size:14px">`
        + `<input type="checkbox" value="${r}" style="padding:0;width:18px;height:18px;accent-color:var(--red)">${r}</label>`).join("")
      + `</fieldset>`
    : `<p class="muted" id="ar-none">${total === 0 ? MSG.noResponders : MSG.allAssigned}</p>`;
  const acts = list.length
    ? `<button class="btn ghost" id="dno">Cancel</button><button class="btn" id="asv">${SAVE_LABEL.save}</button>`
    : `<button class="btn ghost" id="dno">Close</button>`;
  return `${head}${body}\n<div class="ferr" id="dg" role="alert"></div>\n<div class="acts">${acts}</div>`;
}

// The last check before the data layer writes: is this really what buildAssignment() produces from a
// validated form? A responder id and name, a unit from UNITS, and only known resources, none twice.
// saveAssignment() refuses anything else, so a bug in a screen cannot reach Firestore.
export function isAssignmentPayload(p) {
  if (!p || typeof p !== "object") return false;
  const r = p.assignedResources;
  return typeof p.responderId === "string" && p.responderId !== ""
    && typeof p.responderName === "string" && p.responderName !== "" && p.responderName.length <= 100
    && UNITS.includes(p.unit)
    && Array.isArray(r) && r.every(x => RESOURCES.includes(x)) && new Set(r).size === r.length;
}

// Is the assignment already stored under this id the same one we are about to save? Compared on
// the responder, the unit and the resources (in list order), which are the fields the person chose.
export function sameAssignment(stored, payload) {
  if (!stored || !payload) return false;
  const a = orderedResources(stored.assignedResources), b = orderedResources(payload.assignedResources);
  return stored.responderId === payload.responderId && stored.unit === payload.unit
    && a.length === b.length && a.every((x, k) => x === b[k]);
}

// Shows a saved assignment on the screen's own copy of the incident, in place, without another read.
// Nothing is added if that responder is already listed, so a retry that finds its own earlier save
// cannot list the same person twice. Returns the same object (or what it was given).
export function addAssignment(incident, payload) {
  if (!incident || !Array.isArray(incident.res) || !payload) return incident;
  if (!isAssigned(incident, payload.responderId)) incident.res.push(assignmentRow(payload));
  return incident;
}

// ---------- Save, field errors and Try again (W13.18) ----------

// The Save button's three states. "Try again" follows a save that failed; the id of the assignment is kept.
export const SAVE_LABEL = { save: "Save", busy: "Saving...", retry: "Try again" };

// Where each error is painted: under the responder select, under the unit select, and under the buttons.
export const ERROR_IDS = { responder: "#ar-e", unit: "#au-e", general: "#dg" };

// One Assign dialog's assignment id. It is made ONCE when the dialog opens (makeId is newAssignmentId for
// the report) and every Save / Try again reuses it, whatever was changed in the form in between. That makes
// "Try again" safe: if an earlier try reached the server but its answer was lost, the retry finds that very
// document (saveAssignment: { ok, already }) instead of adding a second assignment. Changing the form after a
// failure does not change the id either: a NEW id would let a lost-answer save and the retry both land, and
// the same person could be assigned twice. renew() is only for the one case where the old id cannot be used
// any more (a different assignment is stored under it, see submitAssignment).
export function createAssignSession(makeId) {
  let id = makeId();
  return {
    get id() { return id; },
    renew() { id = makeId(); return id; }
  };
}

// One press of Save (or Try again). Checks the form first and sends nothing when it is not valid. `save` and
// `refresh` are passed in, so this stays free of Firebase and can be tested:
//   save(payload, assignmentId)   writes it (the screen passes saveAssignment, inside guarded()); it may throw
//   refresh()                     re-reads this report's assignments from the server
// Returns what happened, for resultView():
//   { kind: "invalid", errors }               a field is wrong; nothing was sent
//   { kind: "saved", already }                saved now, or an earlier try of this same dialog had saved it
//   { kind: "failed", text }                  the save threw (offline, refused, expired, anything): same id kept
//   { kind: "blocked", text }                 the request itself was not valid; nothing was written
//   { kind: "stale", text, lock }             a DIFFERENT assignment is stored under this id, so an earlier try
//                                             did land. The assignments were re-read and the id renewed, so a
//                                             new save is safe (lock: false); if the re-read failed the dialog
//                                             must be closed instead (lock: true) and the id is left alone
export async function submitAssignment({ incident, form, responders, session, save, refresh }) {
  const v = validateAssignment(incident, form, responders);
  if (!v.ok) return { kind: "invalid", errors: v.errors };

  let r;
  try { r = await save(v.clean, session.id); }
  catch (e) { return { kind: "failed", text: saveFailText(e), error: e }; }

  if (r && r.ok) return { kind: "saved", already: !!r.already };
  if (r && r.stale) {
    let refreshed = false;
    try { await refresh(); refreshed = true; } catch (e) { /* the message below says what to do instead */ }
    if (refreshed) { session.renew(); return { kind: "stale", text: MSG.staleRefreshed, lock: false }; }
    return { kind: "stale", text: MSG.staleClosed, lock: true };
  }
  return { kind: "blocked", text: SAVE_TEXT.other };
}

// What the dialog should look like after submitAssignment() answered, as plain data for app.js to apply:
//   errors   the text for each of ERROR_IDS (an empty string clears it). Always all three, so an old
//            message never stays behind
//   button   the Save button's label
//   close    true: close the dialog, then toast `toast`
//   lock     true: Save stays disabled and Cancel becomes `cancel` ("Close")
//   focus    "responder" or "unit": the first field with an error, so the keyboard lands on it
export function resultView(r) {
  const none = { responder: "", unit: "", general: "" };
  switch (r && r.kind) {
    case "saved":
      return { close: true, toast: MSG.done, errors: none, button: SAVE_LABEL.save };
    case "invalid":
      return {
        errors: { responder: r.errors.responder || "", unit: r.errors.unit || "", general: r.errors.general || "" },
        button: SAVE_LABEL.save,
        focus: r.errors.responder ? "responder" : r.errors.unit ? "unit" : null
      };
    case "stale":
      return {
        errors: { ...none, general: r.text },
        button: SAVE_LABEL.save,
        ...(r.lock ? { lock: true, cancel: "Close" } : {})
      };
    default:   // failed, blocked, or anything unexpected: say so, and let the person try again
      return { errors: { ...none, general: (r && r.text) || SAVE_TEXT.other }, button: SAVE_LABEL.retry };
  }
}

// ---- After a saved assignment (W13.19) ----

// How many of `incidents` a responder is assigned to. One copy for the Overview "Responder workload" card
// and the "Assigned" column of User accounts, so both always count the same way. An incident whose
// assignments were never loaded has an empty list and counts as zero (loadActiveAssignments fills it).
export const assignedCount = (incidents, uid) =>
  (incidents || []).filter(i => i && Array.isArray(i.res) && i.res.some(x => x[0] === uid)).length;

// What is redrawn behind the drawer once an assignment was saved. Only the screens that show who is
// assigned change:
//   "screen"    draw the whole tab again (Overview: the workload card; Live map: the side panel)
//   "filters"   run the tab's search / role filters again, so what was typed stays (User accounts)
//   "none"      leave it (Incident records and Reports do not show assignees)
export const redrawAfterAssign = tab =>
  tab === "over" || tab === "map" ? "screen" : tab === "resp" ? "filters" : "none";
