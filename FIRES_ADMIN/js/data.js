// Data layer (W7): reads the shared Firestore and converts documents into the objects the
// screens use. U (users) and I (incidents) are exported as live arrays that are refilled in
// place, so `import { U, I }` in app.js always sees the current data.
import {
  firebaseConfig, initializeApp, initializeAuth, inMemoryPersistence,
  auth, signOut, createUserWithEmailAndPassword, sendPasswordResetEmail,
  setDoc, updateDoc, deleteDoc, runTransaction, serverTimestamp,
  db, doc, getDoc, getDocs, collection, query, where, orderBy, limit, onSnapshot, Timestamp
} from "./firebase.js";
import { randomPassword } from "./validators.js";
import { ROLE_CHANGES, canChange } from "./roleRules.js";
import { isVerifyKind, canVerify, buildVerifyUpdate, patchIncident } from "./verifyRules.js";
import { isAssignmentPayload, sameAssignment, buildAssignment, addAssignment } from "./assignRules.js";
import {
  mapUser, mapIncident, applyRecord, mapAssignment, mapMessage, photoDataUrl, toMs, esc, mapPhotoAi
} from "./mapping.js";

export const U = [];
export const I = [];

export const RS = () => U.filter(u => u.role === "responder");
export const ACT = i => i.active;
export const fl = i => (i.isSuspicious ? '<span class="pill flag">Suspicious</span> ' : '') + (i.isDuplicate ? '<span class="pill flag">Duplicate</span>' : '');

// Photo block: the image is only fetched when the drawer opens (never for lists).
export const photoBlock = i =>
  i.photoUrl ? `<img class="ph" src="${i.photoUrl}" alt="Photo submitted by the reporter for ${i.code}" data-id="${i.id}">`
  : i.photo ? `<span class="muted">${i.detailState === "error" ? "Photo could not be loaded." : "Loading photo..."}</span>`
  : `<span class="muted">No photo attached.</span>`;

// ---- caches (kept across snapshots so we do not re-read what has not changed) ----
const recordCache = new Map();   // incidentId -> record data (a resolved incident's record is final)
const assignCache = new Map();   // incidentId -> { res, updatedMs }
const photoCache = new Map();    // incidentId -> data URL

export const INCIDENT_LIMIT = 200;
const incidentsQuery = () =>
  query(collection(db, "incidents"), orderBy("submittedAt", "desc"), limit(INCIDENT_LIMIT));

function fillIncidents(snap) {
  const list = snap.docs.map(d =>
    mapIncident(d.id, d.data(), { record: recordCache.get(d.id), assignments: assignCache.get(d.id) }));
  I.length = 0; I.push(...list);
}

// ---- users: one getDocs call (admins only) ----
export async function loadUsers() {
  const snap = await getDocs(collection(db, "users"));
  const list = snap.docs.map(d => mapUser(d.id, d.data()))
    .sort((a, b) => a.fullName.localeCompare(b.fullName));
  U.length = 0; U.push(...list);
}

// ---- incidents ----
let lastIncidentLoad = 0;
export const incidentsAreFresh = (ms = 60000) => Date.now() - lastIncidentLoad < ms;

// One-time read (Records, Reports, User accounts screens).
export async function loadIncidentsOnce() {
  fillIncidents(await getDocs(incidentsQuery()));
  lastIncidentLoad = Date.now();
  await loadResolvedRecords();
}

// Live listener (Overview and Live map). Returns the unsubscribe function.
export function listenIncidents(onChange, onError) {
  return onSnapshot(incidentsQuery(), async snap => {
    fillIncidents(snap);
    lastIncidentLoad = Date.now();
    onChange();                       // draw right away with what we have
    if (await loadResolvedRecords()) onChange();   // then again once resolve times arrive
  }, onError);
}

// records/{incidentId}: only for resolved incidents, once each (then cached). Works on any list of
// mapped incidents: the live list, or the result of a date-range query. Reads run a few at a time.
// Returns true if anything was read, so the caller can redraw.
async function loadResolvedRecords(list = I) {
  const todo = list.filter(i => i.rawStatus === "resolved" && !recordCache.has(i.id));
  if (!todo.length) return false;
  for (let k = 0; k < todo.length; k += 20) {
    await Promise.all(todo.slice(k, k + 20).map(async i => {
      try {
        const s = await getDoc(doc(db, "records", i.id));
        if (s.exists()) { recordCache.set(i.id, s.data()); applyRecord(i, s.data()); }
      } catch (e) { console.warn("record read failed", i.id, e); }
    }));
  }
  return true;
}

// ---- Incidents by submittedAt range (W12) ----
// For Records (older history) and Reports. One query on a single field (submittedAt, newest first),
// so Firestore needs no composite index. fromMs / toMs may be null for "no limit on that side".
// At most `max` incidents come back; capped = true means there were more, so the caller can tell
// the person to choose a shorter range. Each resolved incident also costs one read for its record.
export const RANGE_LIMIT = 500;
export async function loadRange(fromMs, toMs, max = RANGE_LIMIT) {
  const cs = [];
  if (fromMs != null) cs.push(where("submittedAt", ">=", Timestamp.fromMillis(fromMs)));
  if (toMs != null) cs.push(where("submittedAt", "<=", Timestamp.fromMillis(toMs)));
  const snap = await getDocs(query(collection(db, "incidents"), ...cs, orderBy("submittedAt", "desc"), limit(max + 1)));
  const capped = snap.docs.length > max;
  const list = snap.docs.slice(0, max).map(d =>
    mapIncident(d.id, d.data(), { record: recordCache.get(d.id), assignments: assignCache.get(d.id) }));
  await loadResolvedRecords(list);
  return { list, capped };
}

// Assignments of the active incidents only (there is no collection-group rule, so one small
// read per active incident). Used by the workload card and the Users screen. Returns true if
// anything was loaded, so the caller can redraw.
export async function loadActiveAssignments() {
  const todo = I.filter(i => i.active && !i.assignLoaded);
  if (!todo.length) return false;
  await Promise.all(todo.map(async i => {
    try {
      const s = await getDocs(collection(db, "incidents", i.id, "assignments"));
      i.res = s.docs.map(d => mapAssignment(d.data()));
      i.assignLoaded = true;
      assignCache.set(i.id, { res: i.res, updatedMs: i.updatedMs });
    } catch (e) { console.warn("assignments read failed", i.id, e); }
  }));
  return true;
}

// Assignments of the given incidents (the rows on one page of Records), each read once and then cached.
// Returns true if anything was read, so the caller can redraw.
export async function loadAssignmentsFor(list) {
  const todo = list.filter(i => !i.assignLoaded);
  if (!todo.length) return false;
  await Promise.all(todo.map(async i => {
    try {
      const s = await getDocs(collection(db, "incidents", i.id, "assignments"));
      i.res = s.docs.map(d => mapAssignment(d.data()));
      i.assignLoaded = true;
      assignCache.set(i.id, { res: i.res, updatedMs: i.updatedMs });
    } catch (e) { console.warn("assignments read failed", i.id, e); }
  }));
  return true;
}

// The photo as a data URL (cached). Used by the AI photo check.
export async function getPhotoUrl(i) {
  if (!i.photo) return null;
  if (photoCache.has(i.id)) return photoCache.get(i.id);
  const s = await getDoc(doc(db, "incident_photos", i.id));
  const url = s.exists() ? photoDataUrl(s.data()) : null;
  if (url) { photoCache.set(i.id, url); i.photoUrl = url; }
  return url;
}

// Saves the AI photo answer on the incident: exactly the six ai* fields, ONCE. If the incident already
// has an answer (another administrator was faster), nothing is written and { stale: true } comes back.
// Rules allow this for staff only, only these fields, and never over an existing answer.
export async function saveAiPhoto(id, fields, local = I.find(i => i.id === id)) {
  if (!id || !fields) return { blocked: true };
  const ref = doc(db, "incidents", id);
  const out = await runTransaction(db, async tx => {
    const snap = await tx.get(ref);
    if (!snap.exists()) return { stale: true };
    const d = snap.data();
    if (d.aiFireDetected != null || d.aiSeverity != null) return { stale: true };
    tx.update(ref, fields);
    return { ok: true };
  });
  if (out.ok && local) local.photoAi = mapPhotoAi(fields);
  return out;
}

// Everything the drawer needs for ONE incident: photo, assignments, messages, record.
export async function loadDetail(i) {
  i.detailState = "loading";
  try {
    const [photo, asn, msgs, rec] = await Promise.all([
      !i.photo ? null : photoCache.has(i.id) ? photoCache.get(i.id)
        : getDoc(doc(db, "incident_photos", i.id)).then(s => s.exists() ? photoDataUrl(s.data()) : null),
      getDocs(collection(db, "incidents", i.id, "assignments")),
      getDocs(query(collection(db, "incidents", i.id, "messages"), orderBy("sentAt"))),
      i.rawStatus === "resolved" ? getDoc(doc(db, "records", i.id)) : null
    ]);
    if (photo) { i.photoUrl = photo; photoCache.set(i.id, photo); }
    i.res = asn.docs.map(d => mapAssignment(d.data()));
    i.assignLoaded = true;
    assignCache.set(i.id, { res: i.res, updatedMs: i.updatedMs });
    i.msgs = msgs.docs.map(d => mapMessage(d.data()));
    if (rec && rec.exists()) { recordCache.set(i.id, rec.data()); applyRecord(i, rec.data()); }
    i.detailState = "ready";
  } catch (e) {
    console.warn("detail read failed", i.id, e);
    i.detailState = "error";
  }
  return i;
}

// ---- Verify / False report / Duplicate (W13) ----
// Writes exactly verification, status and updatedAt to incidents/{id} (see buildVerifyUpdate), the
// same write as the app's setVerification. kind is "verify", "false" or "duplicate".
//
// The check and the write happen in ONE transaction: the incident is read from the server, and only
// if it is still "reported" is it updated. If someone already reviewed it (from the app or another
// administrator), or it no longer exists, nothing is written and { stale: true } comes back, so a
// review made from an old screen cannot overwrite a newer one. A transaction also needs the server,
// so with no connection it fails at once (code "unavailable") instead of waiting in a local queue
// and being applied hours later, on a report that has changed since. { blocked: true } means
// the request itself was not valid (unknown kind or no id). { ok: true } means it was saved, and
// the screen's own copy of the incident is patched so it shows the change at once.
//
// `local` is that copy. It defaults to the one in I; the Records screen can hold copies loaded for
// a date range, so it can pass its own. Rules allow this write for staff, and only these fields.
export async function verifyIncident(id, kind, local = I.find(i => i.id === id)) {
  if (!id || !isVerifyKind(kind)) return { blocked: true };
  const ref = doc(db, "incidents", id);
  const outcome = await runTransaction(db, async tx => {
    const snap = await tx.get(ref);
    // A missing status counts as "reported", the same as in the mapping and in the app.
    if (!snap.exists() || !canVerify({ rawStatus: snap.data().status || "reported" })) return { stale: true };
    tx.update(ref, buildVerifyUpdate(kind, serverTimestamp()));
    return { ok: true };
  });
  if (outcome.ok) patchIncident(local, kind);
  return outcome;
}

// ---- Assign responder (W13) ----
// The id is made BEFORE the first save and kept for every try, so "Try again" can never add a second
// assignment: it is always the same document. The Assign dialog calls this once when it opens, and
// again only when the person changes the responder, unit or resources, or after a { stale: true }.
export const newAssignmentId = incidentId =>
  incidentId ? doc(collection(db, "incidents", incidentId, "assignments")).id : "";

// Writes incidents/{incidentId}/assignments/{assignmentId} with setDoc: the fields of `payload`
// (responderId, responderName, unit, assignedResources, from buildAssignment) and assignedAt =
// serverTimestamp(), and nothing else. Anything else on `payload` is dropped.
//
// A retry looks at the document first. firestore.rules let an administrator CREATE an assignment for
// another responder but not overwrite one, so a plain second setDoc would be refused exactly when it
// is needed (the first try reached the server but the answer got lost). If the same assignment is
// already there, that earlier save counts: { ok: true, already: true } and nothing is written. If a
// DIFFERENT assignment is under this id, { stale: true } and nothing is written: use a new id.
// { blocked: true } means the request itself was not valid. { ok: true } means it was saved now.
// Either way of success, the assignment is shown straight away, without another read (see
// showSavedAssignment): on the screen's copy of the incident (`local`, default: the one in I) AND in
// the assignment cache, which is what the live listener rebuilds every incident from.
export async function saveAssignment(incidentId, assignmentId, payload, local = I.find(i => i.id === incidentId)) {
  if (!incidentId || !assignmentId || !isAssignmentPayload(payload)) return { blocked: true };
  const ref = doc(db, "incidents", incidentId, "assignments", assignmentId);
  const existing = await getDoc(ref);
  if (existing.exists()) {
    if (!sameAssignment(existing.data(), payload)) return { stale: true };
    showSavedAssignment(incidentId, local, payload);
    return { ok: true, already: true };
  }
  await setDoc(ref, buildAssignment(payload, serverTimestamp()));
  showSavedAssignment(incidentId, local, payload);
  return { ok: true };
}

// Re-reads incidents/{incidentId}/assignments from the server and REPLACES what the screen and the cache
// hold for that report. Used when a Save finds that an earlier try of the same dialog was already stored
// (saveAssignment: { stale: true }): the screen does not know about that assignment yet, so without this
// the same responder could be offered, and assigned, a second time. Returns the new list (mapAssignment
// rows). Throws if the read fails; the caller says what to do then. `local` is the screen's copy of the
// report (default: the one in I).
export async function refreshAssignments(incidentId, local = I.find(i => i.id === incidentId)) {
  const snap = await getDocs(collection(db, "incidents", incidentId, "assignments"));
  const res = snap.docs.map(d => mapAssignment(d.data()));
  if (local) { local.res = res; local.assignLoaded = true; }
  const previous = assignCache.get(incidentId);
  assignCache.set(incidentId, { res, updatedMs: local ? local.updatedMs : previous && previous.updatedMs });
  return res;
}

// Puts a saved assignment where every screen will find it, so nobody has to read it back:
//   1. the screen's own copy of the incident (`local`), which the open drawer draws from
//   2. assignCache, the list that mapIncident() hands to every incident the live listener or a date
//      range rebuilds. A cached list is trusted without a read (assignLoaded = true), so one that
//      lacks a saved assignee would hide that responder everywhere AND let the same person be
//      assigned twice. The cache must not depend on `local` happening to share its list: after a
//      rebuild `local` can be a newer object than the one that was cached, or missing from I.
// Saving does not touch incidents/{id}, so the cached entry stays valid (its updatedMs is unchanged).
// Each list gets the row at most once (addAssignment skips a responder who is already listed), so
// the usual case, where `local` and the cache share one list, adds it once, not twice.
//
// A list that was never loaded is left alone: one known row is not "everyone assigned", and marking
// it loaded would stop the rest from ever being read. loadActiveAssignments() / loadDetail() fetch it.
function showSavedAssignment(incidentId, local, payload) {
  addAssignment(local, payload);
  const cached = assignCache.get(incidentId);
  if (cached) addAssignment(cached, payload);        // { res, updatedMs }: has the res list addAssignment wants
  else if (local && local.assignLoaded === true && Array.isArray(local.res)) {
    assignCache.set(incidentId, { res: local.res, updatedMs: local.updatedMs });   // local is complete: keep it
  }
}

// ---- Add responder (W10) ----
// The sign-in account is created on a SECOND Firebase app instance ("creator"), so the
// administrator stays signed in on the main one. It uses in-memory persistence, so nothing about
// the new account is ever stored in the browser, and it is signed out straight away.
let creatorAuthInst = null;
const creatorAuth = () => creatorAuthInst ||
  (creatorAuthInst = initializeAuth(initializeApp(firebaseConfig, "creator"), { persistence: inMemoryPersistence }));

// Step 1: create the account with a long random password nobody sees. Returns the new uid.
export async function createResponderAuth(email) {
  const a = creatorAuth();
  try {
    const cred = await createUserWithEmailAndPassword(a, email, randomPassword());
    return cred.user.uid;
  } finally {
    try { await signOut(a); } catch (e) { /* nothing to sign out if creation failed */ }
  }
}

// Step 2: users/{uid} with the main (admin) db. Without this document the account counts as a
// citizen, so a failure here must be retried with the same uid. Rules allow it for admins only.
export function saveResponderProfile(uid, clean) {
  return setDoc(doc(db, "users", uid), {
    fullName: clean.fullName, email: clean.email, contactNo: clean.contactNo,
    address: clean.address, role: "responder", createdAt: serverTimestamp()
  });
}

// Step 3: the responder sets their own password from this e-mail.
export const sendResponderReset = email => sendPasswordResetEmail(auth, email);

// ---- Remove responder access / Make responder (W11) ----
// Changes users/{uid}.role and nothing else. The sign-in account stays: a browser cannot delete
// other people's accounts (delete it in the Firebase Console if needed). Rules allow this for
// admins only. kind is "remove" (responder -> citizen) or "make" (citizen -> responder).
//
// The role is read again just before writing. If another administrator changed it meanwhile (or
// the account was deleted), nothing is written and { stale: true } comes back, so one admin
// cannot silently overwrite another's change from an old screen.
export async function changeUserRole(uid, kind) {
  const user = U.find(u => u.id === uid);
  if (!canChange(kind, user, auth.currentUser?.uid)) return { blocked: true };
  const ref = doc(db, "users", uid);
  const snap = await getDoc(ref);
  if (!snap.exists() || snap.data().role !== ROLE_CHANGES[kind].from) return { stale: true };
  await updateDoc(ref, { role: ROLE_CHANGES[kind].to, updatedAt: serverTimestamp() });
  user.role = ROLE_CHANGES[kind].to;   // the screens read U, so they show the change at once
  return { ok: true };
}

// ---- Activity log ----
// activity/{id}: one line per administrator action (review, assign, role change, new responder).
// Written best-effort AFTER the real change succeeded, so a failure here never blocks the work.
// Rules: only an admin can create or read; nobody can change or delete an entry.
export async function logActivity(entry) {
  try {
    await setDoc(doc(collection(db, "activity")), { ...entry, createdAt: serverTimestamp() });
    return true;
  } catch (e) { console.warn("activity not saved", e); return false; }
}
// users/{responderUid}/notifications/{id}: "you were assigned" for ONE responder. The phone listens to its own
// subcollection and shows a notification (no push server on the free plan). Best-effort, AFTER the assignment
// is saved. Rules: only an admin can create; only the owner can read; nobody can change or delete.
export async function notifyAssigned(responderId, incidentId, text, type = "assigned") {
  if (!responderId || !incidentId) return false;
  try {
    await setDoc(doc(collection(db, "users", responderId, "notifications")),
      { type: type === "removed" ? "removed" : "assigned", incidentId, text: String(text || "").slice(0, 200), createdAt: serverTimestamp() });
    return true;
  } catch (e) { console.warn("responder not notified", e); return false; }
}
// Takes ONE responder off ONE incident (admin only; rules: assignments delete = isAdmin). The assignment
// documents are found by responderId, deleted, and the same person is dropped from the screen's copy and
// the cache, so every screen agrees without a re-read. Returns { ok: true, removed: n }.
export async function removeAssignment(incidentId, responderId, local = I.find(i => i.id === incidentId)) {
  if (!incidentId || !responderId) return { blocked: true };
  const snap = await getDocs(query(collection(db, "incidents", incidentId, "assignments"), where("responderId", "==", responderId)));
  const mine = snap.docs.filter(d => d.data().responderId === responderId);
  for (const d of mine) await deleteDoc(doc(db, "incidents", incidentId, "assignments", d.id));
  const drop = list => { if (list && Array.isArray(list.res)) list.res = list.res.filter(r => r[0] !== responderId); };
  drop(local);
  const cached = assignCache.get(incidentId);
  if (cached) drop(cached);
  return { ok: true, removed: mine.length };
}
// The live positions of the responders on one incident (the app writes them while it is DISPATCHED).
// Read-only; the rules let administrators read them. Returns the unsubscribe function.
export function listenResponderLocations(incidentId, onChange, onError) {
  return onSnapshot(collection(db, "incidents", incidentId, "responderLocations"),
    snap => onChange(snap.docs.map(d => ({ id: d.id, data: d.data() }))), onError);
}
// Newest 60 entries, kept up to date. Returns the unsubscribe function.
export function listenActivity(onChange, onError) {
  return onSnapshot(query(collection(db, "activity"), orderBy("createdAt", "desc"), limit(60)), snap => {
    onChange(snap.docs.map(d => {
      const x = d.data();
      return { ms: toMs(x.createdAt) ?? Date.now(), kind: x.type, who: esc(x.actorName || "Admin"), verb: "", what: esc(x.text || ""), id: x.incidentId || "" };
    }));
  }, onError);
}
