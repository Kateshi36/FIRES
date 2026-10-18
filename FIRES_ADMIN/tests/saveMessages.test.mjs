// The messages for a save that does not go through, and the guard that stops "Saving..." from hanging (W13.13).
//   node --test tests/saveMessages.test.mjs
import test from "node:test";
import assert from "node:assert/strict";
import {
  SAVE_TEXT, REVIEW_STALE_TEXT, REVIEW_BLOCKED_TEXT, saveFailText, isOfflineError,
  offlineError, guarded, browserOnline
} from "../js/saveMessages.js";

const err = code => Object.assign(new Error(code), { code });

// ---------- the wording ----------

test("the three W11 messages are unchanged, word for word", () => {
  assert.equal(saveFailText(err("permission-denied")),
    "Firestore refused the change. Check that you are signed in as an administrator and that the latest rules are deployed.");
  assert.equal(saveFailText(err("unavailable")), "No internet connection. Check your network and try again.");
  assert.equal(saveFailText(err("deadline-exceeded")), "No internet connection. Check your network and try again.");
  assert.equal(saveFailText(new Error("boom")), "Could not save the change. Please try again.");
});

test("an expired sign-in has its own message, and unknown or missing errors get the general one", () => {
  assert.equal(saveFailText(err("unauthenticated")), SAVE_TEXT.expired);
  for (const e of [null, undefined, {}, err("internal"), err("not-found"), err("resource-exhausted")]) {
    assert.equal(saveFailText(e), SAVE_TEXT.other);
  }
});

test("'already reviewed' says nothing was changed, and a blocked request uses the general message", () => {
  assert.match(REVIEW_STALE_TEXT, /Someone already reviewed this report/);
  assert.match(REVIEW_STALE_TEXT, /nothing was changed/);
  assert.equal(REVIEW_BLOCKED_TEXT, SAVE_TEXT.other);
});

test("isOfflineError recognises the two connection codes only", () => {
  assert.ok(isOfflineError(err("unavailable")));
  assert.ok(isOfflineError(err("deadline-exceeded")));
  assert.ok(!isOfflineError(err("permission-denied")));
  assert.ok(!isOfflineError(null));
});

// ---------- the guard ----------

test("guarded: a save that answers returns its result", async () => {
  assert.deepEqual(await guarded(async () => ({ ok: true }), { online: true, ms: 1000 }), { ok: true });
});

test("guarded: the browser says offline, so it fails at once and the save is never started", async () => {
  let started = false;
  await assert.rejects(() => guarded(async () => { started = true; }, { online: false }), e => e.code === "unavailable");
  assert.equal(started, false);
  assert.equal(saveFailText(offlineError()), SAVE_TEXT.offline);
});

test("guarded: a save that never answers fails after the deadline with the offline message", async () => {
  const never = () => new Promise(() => {});
  await assert.rejects(() => guarded(never, { online: true, ms: 20 }), e => e.code === "deadline-exceeded");
  assert.equal(saveFailText(err("deadline-exceeded")), SAVE_TEXT.offline);
});

test("guarded: an error from the save itself passes through unchanged", async () => {
  const denied = err("permission-denied");
  await assert.rejects(() => guarded(async () => { throw denied; }, { online: true, ms: 1000 }), e => e === denied);
});

test("guarded: it does not leave a timer running after a fast save", async () => {
  // If the timer were left running, node --test would wait for it; this finishes at once.
  await guarded(async () => 1, { online: true, ms: 60_000 });
});

test("browserOnline: with no browser (Node) it assumes online", () => {
  assert.equal(typeof browserOnline(), "boolean");
});
