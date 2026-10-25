F.I.R.E.S. AI helper (admin website)  -  setup, about 20 minutes, all free
================================================================================

WHAT IT DOES
In the admin website, an open incident drawer has an "AI suggestion" box. When the administrator presses
"Get AI suggestion", the website sends the report TEXT (no name, no phone, no photo) to a small Cloudflare
Worker. The Worker checks that the caller is a signed-in administrator, asks Gemini, and sends back:
  - a suggested severity (low / medium / high / critical) with a confidence and up to 3 short reasons,
  - and, if one of the nearby open reports looks like the same fire, a link to it.
It is ADVICE ONLY. It writes nothing, changes no report, and the website works exactly the same without it.

WHY A WORKER
The Gemini key must stay secret. The Firebase free plan (Spark) has no server code, so a free Cloudflare
Worker holds the key. The website never sees it.

STEP 1  Get a Gemini key (free)
  1. Open https://aistudio.google.com and sign in with the team's Google account.
  2. Create an API key. Check the current free limits shown there.
  3. Pick a current model that is NOT marked "preview" (the models page of the Gemini API lists them; older models may have been shut down, which shows as "Gemini 404") and write its name and today's date in the paper
     (the adviser asked which model is used). Put the name in wrangler.toml, line GEMINI_MODEL.

STEP 2  Create the Worker (free Cloudflare account, no card)
  1. Install Node.js if you do not have it, then open a terminal in the FIRES_AI_WORKER folder.
  2. npx wrangler login
  3. npx wrangler secret put GEMINI_API_KEY      (paste the key when asked)
  4. npx wrangler deploy
  5. It prints an address like https://fires-ai.YOURNAME.workers.dev

STEP 3  Tell the website where it is
  1. Open FIRES_ADMIN\js\config.js and set
       export const AI_ENDPOINT = "https://fires-ai.YOURNAME.workers.dev/suggest";
     (keep /suggest at the end).
  2. Deploy the website:  firebase deploy --only hosting --project fires-a81c0
     then press Ctrl+Shift+R in the browser.

STEP 4  Try it
  Sign in as an administrator, open an open incident, press "Get AI suggestion".

IF SOMETHING GOES WRONG
  "Sign in again"            the login expired. Sign out and in.
  "Only administrators"      the account's role in users/{uid} is not "admin".
  "free AI limit is reached" wait a minute; the free Gemini quota is small and shared.
  "not available right now"  check the Worker address, the secret, and the model name.
  Browser console says CORS  ALLOWED_ORIGINS in wrangler.toml must list your website address exactly.

SAFETY AND PRIVACY (put these in the limitations of the paper)
  - Advice only. A person decides. The form-based severity score is unchanged.
  - Only the report text leaves the system, and only when the administrator presses the button.
  - Free quotas are small and can change. If the AI is down, nothing else is affected.
  - The prompt treats the citizen's text as data, and the answer is checked for the fixed shape.
  - Prompt version: v1. Change the prompt only by raising PROMPT_VERSION in src/core.js.

MEASURING IT FOR CHAPTER 4
  eval\sample-reports.json has 18 EXAMPLE reports with example labels. Replace them with the team's own
  reports and labels (checked by B-FLARE or BFP), then run:
    set GEMINI_API_KEY=your-key
    node eval\run-eval.mjs
  It sends each report 3 times and writes eval\results.md: agreement with the labelers, within one level,
  consistency, duplicate hits and misses, a confusion matrix, and the same numbers for the form score.

TESTS
  node --test tests\*.test.mjs        (in FIRES_AI_WORKER)
  node --test tests\*.test.mjs        (in FIRES_ADMIN)


PHOTO CHECK (Phase AI)
----------------------
The same Worker also answers POST /photo. The admin website sends ONE report photo (mimeType + base64),
the Worker asks Gemini with the fixed photo prompt (version v1) and returns
{fireDetected, smokeOnly, severity, confidence, reason, promptVersion, model}. The website saves the six
ai* fields on the incident once. Same rules as /suggest: administrators only, 12 photo checks a minute per
person, nothing is written by the Worker. After changing worker.js or core.js run:  npx wrangler deploy
No new secret is needed (the same GEMINI_API_KEY is used).
