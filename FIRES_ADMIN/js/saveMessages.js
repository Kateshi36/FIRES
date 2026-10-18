// What the administrator is told when a save does not go through (W11 and W13). Pure functions with
// no Firebase imports, so this file can be tested on its own.
//
// The wording is the one W11 (make / remove responder) already uses, so every save on the website
// fails in the same way. W13 (Verify / False report / Duplicate) uses the same functions.

export const SAVE_TEXT = {
  denied: "Firestore refused the change. Check that you are signed in as an administrator and that the latest rules are deployed.",
  offline: "No internet connection. Check your network and try again.",
  expired: "Your sign-in has expired. Log in again, then try the change again.",
  other: "Could not save the change. Please try again."
};

// A report that someone else reviewed first (the app, or another administrator), or that is gone.
// Nothing was written.
export const REVIEW_STALE_TEXT = "Someone already reviewed this report, so nothing was changed. Open it again to see where it stands.";

// A request that was not valid (unknown kind, no id). Nothing was written.
export const REVIEW_BLOCKED_TEXT = SAVE_TEXT.other;

export const isOfflineError = e => !!e && (e.code === "unavailable" || e.code === "deadline-exceeded");

// The message for an error thrown by a save. Unknown errors get the general message.
export function saveFailText(e) {
  switch (e && e.code) {
    case "permission-denied": return SAVE_TEXT.denied;
    case "unavailable": case "deadline-exceeded": return SAVE_TEXT.offline;
    case "unauthenticated": return SAVE_TEXT.expired;
    default: return SAVE_TEXT.other;
  }
}

// The same error the Firebase SDK gives when the connection is down, for the cases we detect ourselves.
export const offlineError = () => Object.assign(new Error("No internet connection"), { code: "unavailable" });
const deadlineError = () => Object.assign(new Error("No answer from the server"), { code: "deadline-exceeded" });

// The browser's own idea of the connection. Where it has no idea (Node, old browsers), assume online.
export const browserOnline = () =>
  typeof navigator !== "undefined" && typeof navigator.onLine === "boolean" ? navigator.onLine : true;

// Runs a save but never leaves the screen on "Saving..." for good:
//  - the browser says it is offline: fails at once with the offline error, nothing is sent;
//  - the save has not answered after `ms` milliseconds (default 20 s): fails with the same offline error.
// The save itself cannot be cancelled. If it finishes after the deadline it is still safe: the review
// write checks the report inside a transaction, so a second try just finds it already reviewed.
export async function guarded(run, { online = browserOnline(), ms = 20000 } = {}) {
  if (online === false) throw offlineError();
  let timer;
  const deadline = new Promise((_, reject) => { timer = setTimeout(() => reject(deadlineError()), ms); });
  try { return await Promise.race([run(), deadline]); }
  finally { clearTimeout(timer); }
}
