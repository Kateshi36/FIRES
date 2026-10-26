// Responder locations on the website (W14).   node --test tests/responderRules.test.mjs
import test from "node:test";
import assert from "node:assert/strict";
import { panelModel, panelHtml, mapResponderLocation, summarize, responderHtml, sinceText, etaText, distText, isTracked, STALE_MS } from "../js/responderRules.js";

const NOW = Date.UTC(2026, 9, 26, 3, 0, 0);
const T = ms => ({ toMillis: () => ms });
const doc = (o = {}) => ({ latitude: 14.52, longitude: 121.05, updatedAt: T(NOW - 5000), ...o });
const nameOf = id => ({ r1: "Ana Cruz", r2: "Ben <b>Lee</b>" }[id] || "A responder");

test("a stored location becomes a clean object, and bad data is dropped", () => {
  const l = mapResponderLocation("r1", doc({ etaSeconds: 360, distanceMeters: 1200, heading: 90, routePath: [14.5, 121.0, 14.51, 121.02, 7] }), NOW);
  assert.equal(l.lat, 14.52); assert.equal(l.fresh, true); assert.equal(l.etaSeconds, 360);
  assert.deepEqual(l.route, [[14.5, 121.0], [14.51, 121.02]]);
  assert.deepEqual(mapResponderLocation("r1", doc({ trail: [14.5, 121.0, 14.51, 121.01] }), NOW).trail, [[14.5, 121.0], [14.51, 121.01]]);          // an odd last value is ignored
  assert.equal(mapResponderLocation("x", { latitude: "14", longitude: 121 }, NOW), null);
  assert.equal(mapResponderLocation("x", { latitude: 95, longitude: 121 }, NOW), null);
  assert.equal(mapResponderLocation("x", null, NOW), null);
});

test("fresh means updated within the last 60 seconds; a write waiting for the server counts as new", () => {
  assert.equal(mapResponderLocation("r1", doc({ updatedAt: T(NOW - (STALE_MS - 1)) }), NOW).fresh, true);
  assert.equal(mapResponderLocation("r1", doc({ updatedAt: T(NOW - STALE_MS) }), NOW).fresh, false);
  assert.equal(mapResponderLocation("r1", doc({ updatedAt: null }), NOW).fresh, true);
});

test("wording: waiting, live, stale, arrived, and nothing for other statuses", () => {
  assert.equal(summarize("dispatched", [], NOW, nameOf).kind, "waiting");
  const live = summarize("dispatched", [mapResponderLocation("r1", doc({ etaSeconds: 400, distanceMeters: 2500 }), NOW)], NOW, nameOf);
  assert.equal(live.kind, "live");
  assert.equal(live.text, "Ana Cruz is on the way, updated just now");
  const stale = summarize("dispatched", [mapResponderLocation("r1", doc({ updatedAt: T(NOW - 120000), etaSeconds: 400 }), NOW)], NOW, nameOf);
  assert.equal(stale.kind, "stale");
  assert.equal(stale.text, "Ana Cruz's location was last updated 2 min ago");     // never a stale ETA
  assert.equal(summarize("on_scene", [], NOW, nameOf).kind, "arrived");
  for (const st of ["reported", "verified", "resolved", "dismissed"]) assert.equal(summarize(st, [mapResponderLocation("r1", doc(), NOW)], NOW, nameOf).kind, "none");
});

test("two responders: one line each, and live wins when one is fresh", () => {
  const s = summarize("dispatched", [mapResponderLocation("r1", doc(), NOW), mapResponderLocation("r2", doc({ updatedAt: T(NOW - 300000) }), NOW)], NOW, nameOf);
  assert.equal(s.kind, "live"); assert.equal(s.items.length, 2); assert.equal(s.text.split("\n").length, 2);
});

test("names are escaped in HTML", () => {
  const s = summarize("dispatched", [mapResponderLocation("r2", doc(), NOW)], NOW, nameOf);
  const h = responderHtml(s);
  assert.ok(!h.includes("<b>")); assert.ok(h.includes("&lt;b&gt;"));
});

test("small helpers", () => {
  assert.equal(sinceText(NOW - 30000, NOW), "30 s ago"); assert.equal(sinceText(NOW - 3 * 3600e3, NOW), "3 h ago");
  assert.equal(etaText(20), "about 1 min"); assert.equal(etaText(null), "");
  assert.equal(distText(450), "450 m"); assert.equal(distText(1250), "1.3 km");
  assert.equal(isTracked("dispatched"), true); assert.equal(isTracked("on_scene"), false);
});

test("the Responder box: only assigned responders, waiting text, ETA and distance only while live", () => {
  const asg = [{ id: "r1", name: "Ana Cruz" }, { id: "r2", name: "Ben <b>Lee</b>" }];
  const live = mapResponderLocation("r1", doc({ etaSeconds: 400, distanceMeters: 2500 }), NOW);
  const stray = mapResponderLocation("r9", doc(), NOW);                       // shares a location but is not assigned
  const m = panelModel("dispatched", asg, [live, stray], NOW);
  assert.equal(m.kind, "live"); assert.equal(m.rows.length, 2);
  assert.deepEqual([m.rows[0].state, m.rows[0].eta, m.rows[0].dist], ["live", "about 7 min", "2.5 km"]);
  assert.deepEqual([m.rows[1].state, m.rows[1].text], ["waiting", "Waiting for the responder's location"]);
  const h = panelHtml(m);
  assert.ok(h.includes("ETA") && h.includes("2.5 km") && h.includes("Waiting for the responder&#39;s location"));
  assert.ok(!h.includes("<b>Lee"), "names are escaped"); assert.ok(!h.includes("r9"));
  const stale = panelModel("dispatched", asg, [mapResponderLocation("r1", doc({ updatedAt: T(NOW - 120000), etaSeconds: 400 }), NOW)], NOW);
  assert.equal(stale.rows[0].state, "stale"); assert.equal(stale.rows[0].eta, "");        // never a stale ETA
  assert.equal(panelModel("dispatched", [], [], NOW).note, "Waiting for the responder's location.");
  assert.equal(panelModel("on_scene", asg, [], NOW).kind, "arrived");
  assert.equal(panelModel("verified", asg, [live], NOW).kind, "none");
  assert.equal(panelHtml(panelModel("verified", asg, [], NOW)), "");
});
