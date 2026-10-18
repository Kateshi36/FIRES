// Firebase setup for the FIRES admin website (project fires-a81c0).
// One shared project with the Android app: same users, data and rules.
// No build step, so the config lives here. These web values are not secrets;
// access is protected by firestore.rules, not by hiding the key.
//
// SDK version is pinned. Keep all three import URLs on the SAME version, and use
// the version shown in the Firebase Console web snippet.
import { initializeApp } from "https://www.gstatic.com/firebasejs/11.10.0/firebase-app.js";
import { getAuth } from "https://www.gstatic.com/firebasejs/11.10.0/firebase-auth.js";
import { getFirestore } from "https://www.gstatic.com/firebasejs/11.10.0/firebase-firestore.js";

export const firebaseConfig = {
  apiKey: "AIzaSyC-AEq9MXFMiXQWiViO2gqAX-2Fc1tMdxI",
  authDomain: "fires-a81c0.firebaseapp.com",
  projectId: "fires-a81c0",
  appId: "1:169547183772:web:9a8065fbdbc3013fea5f59"
};

const app = initializeApp(firebaseConfig);

export const auth = getAuth(app);
export const db = getFirestore(app);

// Re-exported so every other file imports from here and the pinned SDK version stays in one place.
export {
  signInWithEmailAndPassword, signOut, onAuthStateChanged,
  setPersistence, browserSessionPersistence,
  initializeAuth, inMemoryPersistence, createUserWithEmailAndPassword, sendPasswordResetEmail
} from "https://www.gstatic.com/firebasejs/11.10.0/firebase-auth.js";
export {
  doc, getDoc, getDocs, collection, query, where, orderBy, limit, onSnapshot,
  getCountFromServer, setDoc, updateDoc, runTransaction, serverTimestamp, Timestamp
} from "https://www.gstatic.com/firebasejs/11.10.0/firebase-firestore.js";

export { initializeApp };
