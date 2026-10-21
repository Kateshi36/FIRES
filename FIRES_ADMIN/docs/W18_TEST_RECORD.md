# W18 test record (Chapter 4): the app and the website together

Setup: one phone with a **citizen** account, one phone with a **responder** account, the website on a computer
(`https://fires-a81c0.web.app`) signed in as an **admin**. Fill in Actual, Result (Pass / Fail), Tester and Date.
Keep screenshots of the failures.

| # | Test | Expected result | Actual | Result | Tester / date | Paper table row |
|---|---|---|---|---|---|---|
| 1 | Sign in to the website with the admin account. | The panel opens and the sidebar shows the admin's name. | | | | User Registration and Log-in |
| 2 | Try a responder account, a citizen account, then a wrong password. | Responder and citizen are signed out at once with the admin-only message. A wrong password shows "Incorrect email or password." | | | | User Registration and Log-in |
| 3 | Open index.html while signed out. | It redirects to login.html and shows no data. | | | | User Registration and Log-in (access for authorized users only) |
| 4 | A citizen sends a report in the app. | It appears on the Overview and the Live map within seconds without a refresh. Location, severity, flags and photo match the app. | | | | Centralized Web-Based Dashboard |
| 5 | Filter the map by status, then select a marker. | The filter works and the selected marker shows the correct incident details. | | | | Centralized Web-Based Dashboard |
| 6 | A responder verifies and dispatches in the app, and sends a chat message. | The status changes on the website live. The assignment and the message appear in the drawer. | | | | Incident Verification and Status Monitoring |
| 7 | On the website, verify a reported incident, then mark another one as a false report. | The first becomes Verified and the second becomes Dismissed and leaves the active list. The app shows the same statuses. Dispatch, Resolve and severity are not offered on the website. | | | | Incident Verification and Status Monitoring |
| 8 | On the website, assign a responder to a dispatched incident. Then open that incident in the responder's app. | The assignment shows in the drawer and the app shows the Start response button. | | | | Centralized Web-Based Dashboard |
| 9 | A responder resolves with remarks in the app. | The record shows date resolved and remarks. The Reports counts and average resolve time update. | | | | Historical Incident Records and Reports |
| 10 | Search records by date and status, then print the report. | Results match the stored records, and the summary equals a manual count. | | | | Historical Incident Records and Reports |
| 11 | Add a responder on the website. | The admin stays signed in. The new account signs in to the app and opens the responder screens. The reset e-mail arrives. | | | | User Registration and Log-in |
| 12 | Remove that responder's access. | After a new login the account opens the citizen screens, and the rules refuse staff actions at once. | | | | User Registration and Log-in |
| 13 | Switch off the internet on the website computer and reload. | A clear "could not load" message appears instead of a blank page, and data returns when the internet does. | | | | Table 2 (Reliability) |
| 14 | In the Rules Playground (or `npm test` in `FIRES_APP/rules-test`), read users and other people's incidents as a citizen. | Both are denied. | | | | Security check (not a paper row) |

## Extra checks from the website work (W13)

| # | Test | Expected result | Actual | Result | Tester / date |
|---|---|---|---|---|---|
| 15 | On the website, assign a responder to a **verified but not dispatched** incident. Open it in that responder's app. | The Assigned panel shows the unit and resources. There is no Start response button yet. After someone presses "Mark as dispatched" in the app, the assigned responder sees Start response and a different responder sees "Only a responder assigned to this incident can share a live location." | | | |
| 16 | Open the Assign dialog again for the same incident. | The responder already assigned is not in the list. | | | |
| 17 | Give a test responder the name `O'Brien & Sons` and assign them. | Firestore holds the name exactly as typed. The website and the app show it once, with no `&amp;` or `&#39;`. | | | |
| 18 | Turn off the website computer's Wi-Fi and press Save in the Assign dialog, then turn Wi-Fi on and press Try again. | A "No internet connection" message, then success, and only one assignment document in the Console. | | | |
| 19 | Two administrators open the same reported incident; one verifies it, the other then presses Verify. | The second one is told someone already reviewed it, and nothing is changed. | | | |
| 20 | Run `node --test tests/*.test.mjs` and `node tests/browser/w13.browser.mjs` in the website folder, and `npm test` in `rules-test`. | 208 Node tests, 11 browser checks and the 20 rules tests pass. | | | |

Known results to write in the limitations: there is no way to remove an assignment yet (docs/LIMITS.md), and an
assignment alone does not start a response (the app dispatches).
