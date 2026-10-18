W13.18  Assign dialog: Save, a message under each field, Try again, and one assignment id for every retry
Copy these files over the same paths in FIRES_ADMIN (nothing else changes):

  js/assignRules.js                  Save + Cancel buttons; createAssignSession, submitAssignment, resultView
  js/data.js                         NEW refreshAssignments (used only when an earlier try was already saved)
  js/app.js                          askAssign: Save, field errors, Try again
  tests/assignSubmit.test.mjs        NEW
  tests/assignList.test.mjs          one test updated (Cancel + Save instead of Close when there is a list)
  tests/helpers/firebase-stub.mjs    fake getDocs can now fail (failNextList)

Run all tests:   node --test tests/*.test.mjs
(On Node 22 or newer "node --test tests/" no longer works; use the pattern above.)

Not in this step (W13.19): after saving, the drawer and the Overview / User accounts screens are not redrawn
yet. The assignment IS saved and the report's data is already updated; reopen the report to see it.
