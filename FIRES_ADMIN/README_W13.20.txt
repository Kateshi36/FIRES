W13.20  Review: no Dispatch / On scene / Resolve / severity override / chat; verify write = exactly 3 fields
Result: nothing to fix in the website code. Copy the one new file:
  tests/scope.test.mjs    NEW (5 tests). Fails if a later edit adds a forbidden action or a new kind of write.
Run all tests:   node --test tests/*.test.mjs      (208 pass)
