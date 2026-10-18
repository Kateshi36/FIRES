W13.19  After saving: the new responder shows in the drawer at once; Overview workload and User accounts "Assigned" update
Copy these files over the same paths in FIRES_ADMIN (nothing else changes):

  js/assignRules.js                  NEW assignedCount (one counting rule) and redrawAfterAssign
  js/app.js                          afterAssign(id) runs after Save closes the dialog; workload card and
                                     "Assigned" column now use assignedCount
  tests/afterAssign.test.mjs         NEW (9 tests)

What happens after Save:
  1. The dialog closes and the toast "Responder assigned." shows.
  2. The tab behind the drawer is redrawn: Overview (workload card), Live map (side panel), or User accounts
     (only its filters re-run, so the search text and role filter you typed stay). Records and Reports are not touched.
  3. The open drawer for that report is redrawn by id: the responder is listed under "Assigned responders" at once,
     and the Assign dialog no longer offers them. No new read is made (the data was already stored by W13.10).
  4. Focus goes back to the Assign button (or Close if it is gone).

Run all tests:   node --test tests/*.test.mjs      (203 pass)
Still to do: W13.20 to W13.23.
