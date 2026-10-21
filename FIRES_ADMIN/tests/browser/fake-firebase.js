// A fake of the Firebase web SDK that runs INSIDE the page (served in place of the gstatic.com files, so
// js/firebase.js, data.js and app.js run unchanged). Documents live in window.__fake.store (path -> data).
// Dates are written as { __ts: milliseconds } and come back as objects with toMillis(), like Timestamps.
// Every write is recorded in window.__fake.calls, and every change tells the live listeners, as a real
// Firestore does, so the website rebuilds its incident objects exactly as it does in production.
const store = {}, calls = [], listeners = new Set(), reads = [];
const fake = { store, calls, reads, emit: () => listeners.forEach(f => f()), failNext: null };
window.__fake = fake;
const SEED = window.__seed || {};
Object.assign(store, SEED);

const out = v => {                                       // stored value -> what the SDK hands out
  if (v && typeof v === "object") {
    if (v.__ts !== undefined) return { toMillis: () => v.__ts, __ts: v.__ts };
    if (Array.isArray(v)) return v.map(out);
    return Object.fromEntries(Object.entries(v).map(([k, x]) => [k, out(x)]));
  }
  return v;
};
const inn = v => {                                       // written value -> stored value
  if (v && typeof v === "object") {
    if (v.__serverTimestamp) return { __ts: Date.now() };
    if (Array.isArray(v)) return v.map(inn);
    return Object.fromEntries(Object.entries(v).map(([k, x]) => [k, inn(x)]));
  }
  return v;
};
const clone = v => JSON.parse(JSON.stringify(v));

export const initializeApp = () => ({});
export const getAuth = () => ({ currentUser: { uid: "admin1", email: "admin@fires.test" } });
export const getFirestore = () => ({});
export const initializeAuth = () => ({});
export const inMemoryPersistence = {}, browserSessionPersistence = {};
export const setPersistence = async () => {};
export const signInWithEmailAndPassword = async () => ({});
export const signOut = async () => {};
export const onAuthStateChanged = (a, cb) => { setTimeout(() => cb(a.currentUser), 0); return () => {}; };
export const createUserWithEmailAndPassword = async () => ({ user: { uid: "new" } });
export const sendPasswordResetEmail = async () => {};

let auto = 0;
export const doc = (first, ...parts) =>
  first && first.path !== undefined && parts.length === 0
    ? (() => { const id = "auto" + (++auto); return { path: first.path + "/" + id, id }; })()
    : { path: parts.join("/"), id: parts[parts.length - 1] };
export const collection = (_d, ...p) => ({ path: p.join("/") });
export const orderBy = (f, dir) => ({ __orderBy: f, dir });
export const where = () => ({}); export const limit = n => ({ __limit: n });
export const query = (c, ...cs) => [c, ...cs];

const list = ref => {
  const col = Array.isArray(ref) ? ref[0] : ref, cs = Array.isArray(ref) ? ref.slice(1) : [];
  const pre = col.path + "/";
  let keys = Object.keys(store).filter(k => k.startsWith(pre) && !k.slice(pre.length).includes("/"));
  const ob = cs.find(c => c.__orderBy);
  if (ob) keys.sort((a, b) => ((store[b][ob.__orderBy] || {}).__ts || 0) - ((store[a][ob.__orderBy] || {}).__ts || 0));
  return keys.map(k => ({ id: k.slice(pre.length), data: () => out(clone(store[k])) }));
};
export const getDocs = async ref => { reads.push({ op: "getDocs", path: (Array.isArray(ref) ? ref[0] : ref).path }); return { docs: list(ref) }; };
export const onSnapshot = (ref, cb) => {
  const f = () => cb({ docs: list(ref) });
  listeners.add(f); setTimeout(f, 0);
  return () => listeners.delete(f);
};
export const getCountFromServer = async () => ({ data: () => ({ count: 0 }) });
export const Timestamp = { fromMillis: ms => ({ __ts: ms, toMillis: () => ms }) };
export const serverTimestamp = () => ({ __serverTimestamp: true });

export const getDoc = async ref => {
  reads.push({ op: "getDoc", path: ref.path });
  const f = store[ref.path];
  return { exists: () => f !== undefined, data: () => out(clone(f)) };
};
const guard = () => { if (fake.failNext) { const e = fake.failNext; fake.failNext = null; throw e; } };
const apply = (op, path, data) => {
  calls.push({ op, path, data: clone(data) });
  store[path] = op === "set" ? inn(data) : { ...store[path], ...inn(data) };
};
export const setDoc = async (ref, data) => { guard(); apply("set", ref.path, data); fake.emit(); };
export const updateDoc = async (ref, data) => { guard(); apply("update", ref.path, data); fake.emit(); };
export const deleteDoc = async ref => { guard(); calls.push({ op: "delete", path: ref.path }); delete store[ref.path]; fake.emit(); };
export const runTransaction = async (_d, fn) => {
  const pending = [];
  const tx = { get: ref => getDoc(ref), update: (ref, data) => { pending.push([ref.path, data]); return tx; } };
  const r = await fn(tx);
  guard();
  pending.forEach(([p, d]) => apply("update", p, d));
  if (pending.length) fake.emit();
  return r;
};
