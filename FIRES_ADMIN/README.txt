F.I.R.E.S. Admin Panel (HTML, CSS, JavaScript) - connected to the app's Firebase project (fires-a81c0)

HOW TO RUN
Live site: https://fires-a81c0.web.app  (Firebase Hosting). Locally: serve this folder over http (for example
"npx serve ." here) and open http://localhost:3000/login.html. Opening the files straight from disk does not work,
because the pages load JavaScript modules.
Sign in with an administrator account: a Firebase Authentication user whose users/{uid} document has role = "admin".
Any other account is signed out and refused.

FIRST ADMINISTRATOR (do this once, in the Firebase Console)
1. Authentication > Users > Add user: e-mail and password.
2. Firestore Database > users > Add document, Document ID = that user's UID (copy it from Authentication), fields:
   fullName (string), email (string), contactNo (string), address (string), role (string) = admin.
   The app and the website both read the role from this document. The website never makes an administrator.

FILES
login.html, js/login.js   Admin-only sign-in (Firebase Authentication, role checked in users/{uid})
index.html                Page structure (redirects to login.html when not signed in)
css/                      Styling, light and dark themes
js/firebase.js            Firebase web config; the one place the SDK version is pinned
js/data.js                All Firestore reads and writes (live incidents, details, users, review, assign, roles)
js/mapping.js             Firestore documents -> what the screens show (HTML-escaped once, here)
js/app.js                 Screens: Overview, Live map (Leaflet + OpenStreetMap), User accounts, Incident records, Reports
js/*Rules.js, validators.js, saveMessages.js, dialogHtml.js   Plain logic with no Firebase, tested in Node
tests/                    node --test tests/*.test.mjs                 (208 Node tests)
tests/browser/            node tests/browser/w13.browser.mjs           (real Chromium against a fake Firestore)
firebase.json, .firebaserc, check-deploy.mjs   Hosting config (headers, ignore list), project name, live-site check
docs/LIMITS.md            Known limits, including "no way to remove an assignment yet" and the delete rule to add
docs/W_STEPS_TO_FINISH.md What is left of the W steps and how to do each
docs/W18_TEST_RECORD.md   Test record to fill in for Chapter 4
docs/W19_PAPER_EDITS.md   Sentences to paste into the paper

WHAT THE WEBSITE CAN DO
Review a new report (Verify, False report, Duplicate), assign a responder account with a unit and resources, create
responder accounts, make / remove responder access, read records and reports (print, CSV).
It does NOT dispatch, mark on scene, resolve, change severity or chat: those are done in the app.

DEPLOY AGAIN AFTER A CHANGE
From this folder:   firebase deploy --only hosting
Then:               node check-deploy.mjs https://fires-a81c0.web.app
Browsers may keep old js/css for up to an hour: press Ctrl+F5.
firestore.rules lives in the Android project (FIRES_APP); deploy it from there:
                    firebase deploy --only firestore:rules,firestore:indexes --project fires-a81c0

SECURITY NOTES
The Firebase web config in js/firebase.js is public by design. Data is protected by Authentication and firestore.rules,
not by hiding this page. Never put service-account JSON, passwords or other secrets in this folder.
