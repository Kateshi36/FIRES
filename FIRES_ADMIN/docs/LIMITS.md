# F.I.R.E.S. admin website: known limits (W13.23)

The website reviews reports (Verify, False report, Duplicate) and assigns responder accounts. Everything
else about a response (dispatch, on scene, resolve, severity, chat, live location) is done in the app.

## 1. There is no way to remove an assignment yet

Once "Assign responder" is saved, the assignment stays. The website has no Remove button, and
`firestore.rules` refuses every delete on `incidents/{id}/assignments/{assignmentId}`. Nobody in the app can remove
one either. A wrong assignment therefore stays on the report, and that responder stays "assigned" (they will
get "Start response" once the report is dispatched).

**Until it is added, the workaround is the Firebase Console:** Firestore Database > incidents > the report >
assignments > delete the document. The Console is not affected by the rules. The website shows the change after the
report is opened again (the assignment list is cached for the report until its `updatedAt` changes).

### If the team wants a Remove button

It is three small changes, in this order. Do the rule first and deploy it, or the button will only produce
"permission denied".

1. **Rule** (`firestore.rules`, inside `match /assignments/{assignmentId}`, replacing the "No deletes yet" comment):

       allow delete: if isAdmin();

   Only an administrator can delete, from the website or the Console. Responders still cannot.
   Deploy with `firebase deploy --only firestore:rules`, then check in the Rules Playground that an admin delete is
   allowed and a responder and a citizen delete are refused.
2. **Data layer** (`js/data.js`): a `removeAssignment(incidentId, assignmentId)` that calls `deleteDoc`, then removes the
   row from the report's `res` list and from the assignment cache (the same two places `saveAssignment` updates).
   `mapAssignment` must also carry the document id, because the list only keeps the responder id today.
   `deleteDoc` has to be added to the re-exports in `js/firebase.js` and to the fake in `tests/helpers`.
3. **Drawer**: a "Remove" button next to each row, behind the same confirmation dialog as False report, only on an open
   report. After a delete the Assign list offers that responder again.

Also update `tests/scope.test.mjs`, which lists the website's only four writes: it will (correctly) fail until
"deleteDoc" is added to what it allows.

Things to decide first: should removing be allowed after the report is dispatched, and should a responder who is
already sharing their live location be stopped? (Their sharing ends when the report leaves "Dispatched", not when the
assignment is removed.)

## 2. Dispatch is done in the app, so an assignment alone does not start a response

Assigning on the website leaves the report "Verified". The assigned responder gets "Start response" only after
someone presses "Mark as dispatched" in the app (any responder can).

## 3. The app does not tell a responder they were assigned

There is no notification for it. The responder finds out by opening the report. (A later option, without Cloud
Functions: have the app's alert service watch `assignments` for the signed-in responder.)

## 4. "Assigned by" wording in the app

The app's Assigned panel says "Assigned by <name>". For an assignment made on the website that name is the person
assigned, not the administrator. "Assigned to <name>" is correct for both. One-word change in the app.

## 5. Accounts cannot be deleted from the website

"Remove access" only changes `users/{uid}.role` to citizen. The sign-in account stays (a browser cannot delete other
people's accounts). Delete it in Firebase Console > Authentication if needed. Nobody, including the signed-in
admin, can be demoted from the website, so the last administrator cannot be removed by mistake.

## 6. Other limits

- Only the newest 200 incidents are live (Overview, Live map). Older history is loaded on request from Incident records
  and Reports, up to 500 per range.
- Every screen read counts against the free daily Firestore quota; the site avoids repeat reads, but many admins
  with many open tabs will use more.
- Photos are stored in Firestore as Base64 (no Cloud Storage on the free plan), so they are small and compressed.
