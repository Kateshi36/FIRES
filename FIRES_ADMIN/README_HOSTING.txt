Hosting the website on Firebase Hosting (replaces the Vercel files and steps from W15 / W16)

NEW or CHANGED files at the root of FIRES_ADMIN (next to index.html):
  firebase.json      hosting only: public folder ".", cleanUrls OFF, the four security headers on every file,
                     and an ignore list so tests/, docs/, README*.txt, firebase.json, .firebaserc and
                     check-deploy.mjs are NOT uploaded.
  .firebaserc        names the project: fires-a81c0 (so you never need --project)
  .gitignore         now also ignores .firebase/ and firebase-debug logs (plus the secret files as before)
  check-deploy.mjs   checks the LIVE site after a deploy
DELETE if you copied them earlier: vercel.json, .vercelignore, README_W15.txt.

What I tested (Firebase's own tooling, run here):
  - Firebase's hosting emulator serves login.html / index.html / js with all four headers; /login gives 404, so the
    .html addresses stay and nothing was turned into "clean URLs".
  - Firebase's own file lister, given your ignore list, would upload exactly 16 files: login.html, index.html, 12 js
    files, 2 css files. No tests, docs, README, firebase.json, .firebaserc or dotfiles.
  Not tested: a real deploy and the live address, because that needs your Google login.

WHERE YOU ARE IN THE PLAN (Phase W)
  Done as files/docs: W13 (the website's review + assign), W14 text, W15 files. Do these now, in this order:
  1. W14  Deploy the rules (steps in FIRES_APP/docs/W14_RULES_AND_INDEXES.md). Do this BEFORE the live site is used.
  2. W15  Put the website in its own private GitHub repository (below, step A).
  3. W16  Deploy on Firebase Hosting (below, step B).
  4. W17  Check authorized domains and restrict the web API key (step C).
  5. W18  Test the phone app and the live website together (use the W13.22 checklist for the assign test).
  6. W19  Update the paper and the README.   (W20 is optional and mostly skipped: delete accounts in the Console.)

A. GitHub (W15)
  1. Make a NEW folder outside your FIRES project, copy the contents of FIRES_ADMIN into it (index.html at its root).
     Do not run git init inside FIRES\FIRES_ADMIN: FIRES already has its own .git.
  2. GitHub: New repository > fires-admin > Private > no README / .gitignore / licence.
  3. In the new folder:  git init  /  git add .  /  git status (nothing secret, no google-services.json,
     local.properties or *-adminsdk-*.json)  /  git commit -m "FIRES admin website"  /  git branch -M main  /
     git remote add origin https://github.com/<user>/fires-admin.git  /  git push -u origin main
     The repository can be under a personal account or an organization: Firebase Hosting does not read it.

B. Deploy (W16), from the same folder
  1. Once:   npm install -g firebase-tools        (needs Node)       then:   firebase login
     Use a Google account that is a member of the Firebase project fires-a81c0.
  2. You do NOT need "firebase init": firebase.json and .firebaserc are already here.
  3. Deploy:   firebase deploy --only hosting
     It ends with  Hosting URL: https://fires-a81c0.web.app  (also https://fires-a81c0.firebaseapp.com).
     If it says the site does not exist: Firebase Console > Build > Hosting > Get started, then run it again.
  4. Check it:   node check-deploy.mjs https://fires-a81c0.web.app      (Node 18+; every line should say ok)
     Then open the address in a browser: / lands on login.html; sign in as admin -> index.html.
  5. Every change later: edit, test, git commit + push, then run  firebase deploy --only hosting  again.
     Browsers may keep old js/css up to an hour (Hosting's default cache): use Ctrl+F5 after a deploy.
  6. Teammates: Firebase Console > Project settings > Users and permissions > Add member. Role Editor (or
     Firebase Hosting Admin). Each runs firebase login with their own Google account. No seat limit.
  7. Optional automatic deploy on every push: firebase init hosting:github (writes a GitHub Actions workflow and
     keeps a deploy key as a GitHub secret). It is optional; the manual deploy above always works. Check it works on
     your Spark plan when you set it up.

C. Domains and key (W17)
  1. Firebase Console > Authentication > Settings > Authorized domains: fires-a81c0.web.app,
     fires-a81c0.firebaseapp.com and localhost should already be listed. Confirm; add a custom domain later if you get one.
  2. Only AFTER sign-in works on the live address: Google Cloud Console > APIs & Services > Credentials > the Web key
     used in js/firebase.js > Application restrictions: HTTP referrers > add
         https://fires-a81c0.web.app/*     https://fires-a81c0.firebaseapp.com/*     http://localhost/*
     Save, wait a minute, and test sign-in again at once. If login breaks, remove the restriction and re-check
     the referrers for typos.

KEEP IN MIND
  - A private repository hides the code, not the site: anyone with the address can open login.html. Only admin
    accounts get in (role check in users/{uid}) and firestore.rules enforce it again on the server.
  - Never put service-account JSON, passwords or API secrets in this repository. The Firebase web config in
    js/firebase.js is the only key here, and it is public by design.
