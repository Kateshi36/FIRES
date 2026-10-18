W13.21  Headless-browser test of the real website against a fake Firestore
Two NEW files (nothing existing changes). Copy to the same paths in FIRES_ADMIN:

  tests/browser/fake-firebase.js     the fake Firebase SDK, runs inside the page (store, writes, live listeners)
  tests/browser/w13.browser.mjs      11 checks in real Chromium

Run (once: npm i --no-save playwright, then npx playwright install chromium):
  node tests/browser/w13.browser.mjs
It starts its own local web server and needs no internet and no real Firebase project.
It is NOT picked up by "node --test tests/*.test.mjs" (that stays at 208 passing).

What it covers
  Buttons     Verify / False report / Duplicate only on a reported incident (not verified, dismissed, resolved);
              Assign responder only on open reports.
  Dialogs     False report and Duplicate ask first; Cancel is focused and writes nothing; Verify does not ask.
  Payloads    exactly verification + status + updatedAt; assignment exactly responderId, responderName, unit,
              assignedResources, assignedAt.
  Stale       the report is reviewed elsewhere before the click (and before Confirm): nothing written, drawer closes,
              "Someone already reviewed this report" toast.
  Duplicates  an assigned responder disappears from the list; when all are assigned the dialog explains and has no Save.
  Names       "O'Brien & Sons" is stored raw in Firestore and shown once-escaped in the drawer and Overview.
  W13.19      User accounts "Assigned" goes 0 -> 1 after Save with no reload.
Checked by breaking the code on purpose: canVerify always true -> 6 checks fail; escaped raw name -> 2 fail.
