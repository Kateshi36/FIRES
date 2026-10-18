W13.13  Saving, offline, permission-denied and "already reviewed" for Verify / False report / Duplicate
Copy these files over the same paths in FIRES_ADMIN (nothing else changes):

  js/saveMessages.js                 NEW   the messages (W11 wording) + the guard against a hanging save
  js/app.js                          saveFail uses saveMessages.js; review writes go through guarded()
  js/data.js                         verifyIncident: check + write in ONE transaction
  js/firebase.js                     re-exports runTransaction
  tests/saveMessages.test.mjs        NEW
  tests/verifyIncident.test.mjs      3 new tests
  tests/helpers/firebase-stub.mjs    fake runTransaction, failNextRead

Run all tests:   node --test tests/
