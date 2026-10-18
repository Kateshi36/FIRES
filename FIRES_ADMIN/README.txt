F.I.R.E.S. Admin Panel (HTML, CSS, JavaScript)

HOW TO RUN
Open login.html in a browser. No install needed.
Sign in with an administrator account (users/{uid} in Firestore with role = admin).
Accounts with any other role are signed out and refused.

FILES
login.html      Admin-only sign-in page (css/login.css, js/login.js)
index.html      Admin panel page structure (redirects to login.html if not signed in) (sidebar, main area, drawer, photo viewer)
css/styles.css  All styling, with light and dark themes
js/data.js      Sample data matching the ERD (users, incident reports, assignments, messages)
js/app.js       Screens and behaviour: Overview, Live map, User accounts, Incident records, Reports

CONNECTING THE REAL SYSTEM (per Chapter 3)
1. Firebase: replace the arrays in js/data.js (U, I) with reads from Firestore.
   Photos: set the image src to the photoUrl stored in Firebase Storage (see ph() in js/data.js).
   Accounts: Add responder should call Firebase Authentication and write the User record with role "responder".
2. Google Maps: the map() function in js/app.js draws a simple SVG. Replace it with a Google Maps map
   and place one marker per incident using latitude and longitude. Keep the pick(id) click behaviour.
3. Add your Google Fonts / Maps API keys in index.html as needed.

NOTE: Sample data is placeholder only. Changes (add/remove users) are not saved after reload.

LOGIN: js/login.js uses a demo account list. Replace it with Firebase Authentication and check role === "administrator" in the User table.
