# W19: sentences for the paper

Written to paste. Check each against your chapter's tense and numbering.

## Chapter 3, tools and technologies

- **Kotlin and Jetpack Compose** for the Android app (citizens and authorized responders), minimum Android 8.0 (API 26).
- **Firebase Authentication** (e-mail and password) for sign-in. One account type serves everyone; the role (citizen,
  responder, admin) is stored in the user's document and checked by the app, the website and the Firestore security rules.
- **Cloud Firestore** as the single shared database for the app and the website, protected by security rules that
  decide who may read and write each collection.
- **OpenStreetMap** with **osmdroid** in the app and **Leaflet** on the website for maps, and the public **OSRM**
  routing service for responder routes and estimated arrival times. No paid map service is used.
- **Firebase Hosting** (free Spark plan) for the administrator website, written in plain HTML, CSS and JavaScript with the
  **Firebase JavaScript SDK**, with no build step.
- Incident photos are compressed and stored in Firestore as Base64 text, so no paid storage service is needed.

## Data flow (Chapter 3)

"The mobile app and the website share one Firestore database. A report sent from a citizen's phone is written to the
incidents collection and appears on the administrator's dashboard within seconds, because the website listens to the
collection. A status change made by a responder in the app, or a review (verify, false report, duplicate) and an
assignment made by the administrator on the website, is written to the same documents and appears on every
connected screen. The security rules check the signed-in user's role on every read and write."

## Roles on the website (Chapter 1 scope, Chapter 3)

"Authorized Personnel use the website with the admin role. They verify reported incidents, assign responder accounts with
a unit and resources, create responder accounts, make or remove responder access, and read records and statistical
reports. Dispatching, marking a responder on scene, resolving an incident, changing severity and the chat are done by
responders in the mobile app."

## Limitations (Chapter 1 or 5)

- The free plan has no push notification server (Cloud Functions need a paid plan). Responder alerts and citizen
  updates use a foreground service on the phone, so delivery can be delayed when the phone's battery saver stops
  background apps, and a notification stays visible while a responder shares a location.
- Estimated arrival times are estimates from a public routing server meant for light use; position accuracy depends on the
  phone's GPS and signal.
- Firebase Hosting and Firestore are used on the free Spark plan, which has daily limits. Check the current limits on
  Firebase's pricing page before the barangay relies on the system for real operations.
- A browser cannot disable or delete other people's sign-in accounts, so the administrator does it by hand in the
  Firebase Console. "Remove access" only changes the person's role.
- An assignment made on the website cannot be removed yet, and it does not by itself start a response: a responder must
  mark the incident as dispatched in the app first.
- The system covers fire incidents only and depends on GPS accuracy, internet connectivity and battery power.

## Edits to existing text (from the handoff)

- Java to Kotlin (Chapter 1 objectives and scope, Chapter 2 conceptual model, Chapter 3 tools).
- Google Maps to OpenStreetMap.
- Responders use the mobile app as well as the website; Chapters 1 and 2 say the app is for citizens only.
- Android 8.0 or higher in the conceptual model matches minSdk 26.
- Pick one ISO term: "Portability" in the conceptual model or "Flexibility" in the text.
- Add the W18 test rows to the test tables (`docs/W18_TEST_RECORD.md`).
