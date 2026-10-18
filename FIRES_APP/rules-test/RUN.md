# Rules tests (W14)

These tests check `../firestore.rules` on the **Firestore emulator** on your own computer. They never touch the
live project `fires-a81c0` (the project id `demo-fires` is local only).

## You need (once)

- Node 20 or newer (`node -v`)
- Java 21 recommended (`java -version`). The emulator is a Java program; a very old Java will not start it.

## Run

```
cd FIRES_APP/rules-test
npm install
npm test
```

The first run downloads the emulator (about 60 MB), so it needs internet. Later runs are quick.

## What you should see

20 tests, all passing. The test names start with an ID (R-01 to R-13) that matches the W14 test record:

| ID | What the rules must do |
|----|------------------------|
| R-01 to R-03 | Only an admin can list users. |
| R-04, R-05 | A citizen cannot read other people's incidents. |
| R-06 | An admin can create an assignment for another responder. |
| R-07 | A responder cannot create an assignment for someone else. |
| R-08 | A responder can still assign themselves. |
| R-09 | An admin cannot assign a citizen, another admin, or a missing account. Extra fields are refused. |
| R-10 | A signed-out visitor can read nothing. |
| R-11 | A citizen cannot make themselves admin or responder. |
| R-12 | An admin can verify a reported incident; a citizen cannot change one. |
| R-13 | A collection the rules do not name is refused. |

If a test fails, the message names the check. Do not deploy the rules until it passes, and send the failing
test name and message so the rule or the test can be fixed.
