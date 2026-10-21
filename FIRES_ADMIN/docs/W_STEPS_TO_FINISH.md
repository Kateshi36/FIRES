# What is left of the W steps

State of the project, as seen from your folder `C:\xampp\htdocs\FIRES\FIRES`:

| Step | State | Evidence |
|---|---|---|
| W1 to W13 (connect the website, live data, review, assign) | Built and tested | 208 Node tests and 11 browser checks pass on your files |
| W14 rules and indexes | Rules file updated, rules tests added | `FIRES_APP/rules-test` (20 tests). **Check below that the rules are deployed.** |
| W16 deploy | **Done on Oct 18** | `FIRES_ADMIN/.firebase/hosting..cache` lists exactly the 16 site files, with the same dates as your current files |
| W15 GitHub repository for the website | **Not done, as far as your computer shows** | `FIRES_ADMIN` has no `.git` of its own; it is only a folder inside the `FIRES` repository |
| W17 domains and API key | You have to check it in the Console | cannot be seen from files |
| W18 test together | You, with two phones | record sheet: `docs/W18_TEST_RECORD.md` |
| W19 documents | Sentences ready | `docs/W19_PAPER_EDITS.md`, and `README.txt` is updated |
| W20 account deletion | Skip | do it by hand in Console > Authentication |

I cannot run git, firebase or open the Console from here, so the steps below are for you. Do them in this order.

## 1. W14: confirm the rules are live (5 minutes)

In PowerShell:

    cd C:\xampp\htdocs\FIRES\FIRES\FIRES_APP\rules-test
    npm install
    npm test                      (20 tests, all must pass; the first run downloads the emulator, about 60 MB)
    cd ..
    firebase login                (once)
    firebase deploy --only firestore:rules,firestore:indexes --project fires-a81c0

Then Firebase Console > Firestore Database > Rules. It must show your `firestore.rules` (it contains
`isAdmin() && roleOf(request.resource.data.responderId) == 'responder'` in the assignments part) and **no**
`allow read, write: if true` and no `request.time < timestamp.date(...)` test-mode rule.
Indexes: none are needed (see `FIRES_APP/docs/W14_RULES_AND_INDEXES.md` if you have it). If the deploy asks to delete
indexes that are only in the Console, answer No.

## 2. W15: the website in its own private GitHub repository

Do not run `git init` inside `FIRES_ADMIN`: it sits inside the `FIRES` repository (remote Kateshi36/FIRES). Copy it out.

1. On GitHub: New repository > `fires-admin` > **Private** > do not add a README, .gitignore or licence.
2. In PowerShell:

       robocopy C:\xampp\htdocs\FIRES\FIRES\FIRES_ADMIN C:\xampp\htdocs\fires-admin /E /XD .firebase node_modules /XF README_W13.*.txt
       cd C:\xampp\htdocs\fires-admin
       git init
       git add .
       git status
       git commit -m "FIRES admin website"
       git branch -M main
       git remote add origin https://github.com/<your-github-user>/fires-admin.git
       git push -u origin main

   (robocopy prints exit codes 0 to 7 for success; only 8 or more is an error.)
3. Read the `git status` list before committing: you must NOT see `google-services.json`, `local.properties` or any
   `*-adminsdk-*.json`. The `.gitignore` already blocks them.
4. From now on edit and deploy from `C:\xampp\htdocs\fires-admin` and stop editing the copy inside `FIRES`, or the two
   will drift apart. Deploy from the new folder with `firebase deploy --only hosting` (the `.firebaserc` is copied).
   One test (`rulesMatch.test.mjs`) needs `FIRES_APP/firestore.rules` next to the site; in the new repository it
   skips itself on purpose. Run it from the old location when you change a website write.
5. Teammates: GitHub > the repository > Settings > Collaborators. They also need Firebase Console > Project settings >
   Users and permissions > Add member (role Editor) to deploy.

## 3. W17: authorized domains and the API key

1. Firebase Console > Authentication > Settings > **Authorized domains**. `localhost`, `fires-a81c0.web.app` and
   `fires-a81c0.firebaseapp.com` should be listed. Add a custom domain here later if you get one.
2. Only after you have signed in to the live site once: Google Cloud Console > APIs & Services > **Credentials** > the
   key whose value is the `apiKey` in `js/firebase.js` (it starts with `AIza`). Application restrictions > **HTTP
   referrers** > add
       https://fires-a81c0.web.app/*
       https://fires-a81c0.firebaseapp.com/*
       http://localhost/*
   Save, wait a minute, then sign in to the live site again. If sign-in breaks, remove the restriction and check the
   three lines for typos. (This key is separate from the Android key: that one is restricted to the app's package name.)
3. Run the live check:   `node check-deploy.mjs https://fires-a81c0.web.app`   (every line should say ok).

## 4. W18: test everything together

Use `docs/W18_TEST_RECORD.md`: fill in Actual, Pass or Fail, your name and the date for each row. The assign test
needs the report to be **dispatched first** (the website cannot dispatch): see `docs/W13.22_PHONE_TEST.txt`.
Send me any row that fails with what you saw.

## 5. W19: documents

Paste from `docs/W19_PAPER_EDITS.md` into Chapter 3 and the limitations, and move the W18 rows into the test tables.

## 6. W20: skip

Delete or disable accounts by hand in Firebase Console > Authentication > Users. "Remove access" on the website
already stops a person acting as a responder.
