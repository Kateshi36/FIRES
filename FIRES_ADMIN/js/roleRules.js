// Role changes on the User accounts screen (W11). Pure functions, no Firebase imports, so this file
// can be tested on its own.
//
// Two actions only, and only between responder and citizen:
//   remove  responder -> citizen   (the confirm dialog's "Remove access")
//   make    citizen   -> responder (the optional "Make responder")
// Administrators are never changed from the website. That is what keeps the last administrator
// from being demoted: nobody, including that person, can demote an admin here. Admin accounts are
// created and changed in the Firebase Console.

export const ROLE_CHANGES = {
  remove: { from: "responder", to: "citizen" },
  make: { from: "citizen", to: "responder" }
};

// What the button on a user row looks like. `note` is the short visible hint under a disabled
// button; `reason` is the longer text for the tooltip and screen readers.
export function rowAction(user, myUid) {
  if (user.role === "responder") return { kind: "remove", label: "Remove access", enabled: true };
  if (user.role === "citizen") return { kind: "make", label: "Make responder", enabled: true };
  const self = user.id === myUid;
  return {
    kind: "remove", label: "Remove access", enabled: false,
    note: self ? "This is you." : "Managed in the Console.",
    reason: self
      ? "You cannot remove your own administrator access, so the last administrator can never be demoted."
      : "Administrator access can only be changed in the Firebase Console."
  };
}

// The same check again at the moment of saving, in case the screen was stale.
export function canChange(kind, user, myUid) {
  const c = ROLE_CHANGES[kind];
  return !!c && !!user && user.id !== myUid && user.role === c.from;
}
