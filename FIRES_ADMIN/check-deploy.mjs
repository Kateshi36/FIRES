// W16: checks the LIVE site after a deploy.   node check-deploy.mjs https://fires-a81c0.web.app
// Needs Node 18 or newer. Reads only; sends no credentials.
const base = (process.argv[2] || "").replace(/\/+$/, "");
if (!/^https?:\/\//.test(base)) { console.log("Usage: node check-deploy.mjs https://fires-a81c0.web.app"); process.exit(2); }

let bad = 0;
const ok = (c, m) => { console.log((c ? "ok   " : "FAIL ") + m); if (!c) bad++; };
const get = p => fetch(base + p, { redirect: "manual" });

const login = await get("/login.html");
const loginText = await login.text();
ok(login.status === 200, "/login.html loads (HTTP " + login.status + ")");
ok(/<title>/i.test(loginText) && /login\.js/.test(loginText), "/login.html is the F.I.R.E.S. sign-in page");
const h = login.headers;
ok(h.get("x-content-type-options") === "nosniff", "X-Content-Type-Options: nosniff");
ok(h.get("x-frame-options") === "DENY", "X-Frame-Options: DENY");
ok(h.get("referrer-policy") === "strict-origin-when-cross-origin", "Referrer-Policy: strict-origin-when-cross-origin");
ok(/noindex/i.test(h.get("x-robots-tag") || "") && /nofollow/i.test(h.get("x-robots-tag") || ""), "X-Robots-Tag: noindex, nofollow");

const idx = await get("/index.html");
ok(idx.status === 200, "/index.html loads (HTTP " + idx.status + "); it sends you to login.html in the browser when signed out");
const root = await get("/");
ok(root.status === 200, "/ loads (HTTP " + root.status + ")");
const js = await get("/js/app.js");
ok(js.status === 200 && /javascript/.test(js.headers.get("content-type") || ""), "/js/app.js is served as JavaScript");
ok(js.headers.get("x-content-type-options") === "nosniff", "the header is on the js files too");

// Not part of the site (the ignore list in firebase.json): these must NOT be reachable.
for (const p of ["/tests/scope.test.mjs", "/docs/LIMITS.md", "/README.txt", "/firebase.json", "/.firebaserc", "/check-deploy.mjs"]) {
  const r = await get(p);
  ok(r.status === 404, p + " is not published (HTTP " + r.status + ")");
}
console.log(bad ? "\n" + bad + " check(s) failed" : "\nlive site looks right");
process.exit(bad ? 1 : 0);
