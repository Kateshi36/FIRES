// A tiny fake of the Firebase SDK for the tests. Documents live in `store` (path -> data). Every write
// is recorded in `calls`, so a test can check exactly what would have been sent.
export const store = {};
export const calls = [];
let nextUpdateError = null, nextSetError = null, nextReadError = null, nextListError = null, autoId = 0;
export const failNextUpdate = err => { nextUpdateError = err; };   // the next updateDoc throws this
export const failNextSet = err => { nextSetError = err; };         // the next setDoc throws this
export const failNextList = err => { nextListError = err; };       // the next getDocs (a collection read) throws this
export const failNextRead = err => { nextReadError = err; };       // the next getDoc / transaction read throws this (e.g. offline)
// Every read, in order, as { op: "getDoc" | "getDocs", path }, so a test can prove that something was
// shown WITHOUT another read.
export const reads = [];
export const reset = () => { for (const k of Object.keys(store)) delete store[k]; calls.length = 0; reads.length = 0; nextUpdateError = null; nextSetError = null; nextReadError = null; nextListError = null; autoId = 0; };

export const initializeApp = () => ({});
export const getAuth = () => ({ currentUser: { uid: "admin1" } });
export const getFirestore = () => ({});
export const initializeAuth = () => ({});
export const inMemoryPersistence = {};
export const browserSessionPersistence = {};
export const setPersistence = async () => {};
export const signInWithEmailAndPassword = async () => ({});
export const signOut = async () => {};
export const onAuthStateChanged = () => () => {};
export const createUserWithEmailAndPassword = async () => ({ user: { uid: "new" } });
export const sendPasswordResetEmail = async () => {};

// doc(db, ...path) is a reference to that path. doc(collectionRef) makes a new auto id, like the SDK.
export const doc = (first, ...parts) => {
  if (first && first.path !== undefined && parts.length === 0) {
    const id = `auto${++autoId}`;
    return { path: `${first.path}/${id}`, id };
  }
  return { path: parts.join("/"), id: parts[parts.length - 1] };
};
export const collection = (_db, ...parts) => ({ path: parts.join("/") });
export const query = (...a) => a;
export const where = (...a) => a;
export const orderBy = (...a) => a;
export const limit = n => n;
export const onSnapshot = () => () => {};
// A collection read returns the documents in `store` directly under that path (not the ones in
// sub-collections), in path order. query() returns [collectionRef, ...constraints] in this fake, so
// the path is the first element; where / orderBy / limit are not applied.
export const getDocs = async ref => {
  if (nextListError) { const e = nextListError; nextListError = null; throw e; }
  const path = Array.isArray(ref) ? ref[0].path : ref.path;
  reads.push({ op: "getDocs", path });
  const prefix = path + "/";
  const docs = Object.keys(store).sort()
    .filter(k => k.startsWith(prefix) && !k.slice(prefix.length).includes("/"))
    .map(k => ({ id: k.slice(prefix.length), data: () => structuredClone(store[k]) }));
  return { docs };
};
export const getCountFromServer = async () => ({ data: () => ({ count: 0 }) });
export const Timestamp = { fromMillis: ms => ({ ms }) };
export const serverTimestamp = () => ({ __serverTimestamp: true });

export const getDoc = async ref => {
  if (nextReadError) { const e = nextReadError; nextReadError = null; throw e; }
  reads.push({ op: "getDoc", path: ref.path });
  const found = store[ref.path];
  return { exists: () => found !== undefined, data: () => structuredClone(found) };
};
export const updateDoc = async (ref, data) => {
  if (nextUpdateError) { const e = nextUpdateError; nextUpdateError = null; throw e; }
  calls.push({ op: "update", path: ref.path, data });
};
export const deleteDoc = async ref => { calls.push({ op: "delete", path: ref.path }); delete store[ref.path]; };
export const setDoc = async (ref, data) => {
  if (nextSetError) { const e = nextSetError; nextSetError = null; throw e; }
  calls.push({ op: "set", path: ref.path, data });
  store[ref.path] = structuredClone(data);   // so a later getDoc sees what was written
};

// runTransaction(db, fn): fn gets { get, update }. Reads go to the store at once; updates are collected
// and only applied (recorded in `calls`) when fn finishes without throwing, like a real commit. A failure
// set with failNextUpdate happens at that commit, and nothing is recorded.
export const runTransaction = async (_db, fn) => {
  const pending = [];
  const tx = {
    get: ref => getDoc(ref),
    update: (ref, data) => { pending.push({ op: "update", path: ref.path, data }); return tx; }
  };
  const result = await fn(tx);
  if (pending.length && nextUpdateError) { const e = nextUpdateError; nextUpdateError = null; throw e; }
  calls.push(...pending);
  return result;
};
