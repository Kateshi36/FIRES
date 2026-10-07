# F.I.R.E.S. live tracking: known limits (H7)

These limits are by design for the capstone prototype. They are not bugs. Each one says what to do
for a real deployment.

## 1. The ETA is an estimate

The citizen's banner ("Responder is on the way, arriving in about 6 min") comes from a road route
worked out by OSRM on OpenStreetMap data.

- It follows roads and uses typical driving speeds. It has **no live traffic**, road closures,
  flooding, or the responder's real speed.
- It is refreshed about every 30 seconds or every 200 m, so it can lag a moving responder.
- When no route can be fetched, the responder's map shows a dashed straight line marked
  "straight line (no route available)", and the citizen sees "Responder is on the way" with
  **no ETA**. A straight line is never presented as an arrival time.
- A location older than 60 seconds is shown as "Location last updated N min ago", not as an ETA.

Treat the ETA as guidance for the citizen, not as a promise. Responders still decide by radio and
local knowledge.

## 2. The public OSRM server is for light use only

The app asks `router.project-osrm.org` for routes. That is the OSRM project's public demo server.

- Its own policy limits it to reasonable, **non-commercial** use, asks clients not to exceed
  **1 request per second**, and gives **no guarantee of uptime, latency or data updates**. Access can
  be withdrawn at any time.
- Fine for a capstone demo: the app asks for at most one route every 10 seconds per sharing
  responder (normally every 30 seconds), and it identifies itself with a User-Agent.
- If the server is down or slow, sharing still works: the position keeps being sent and the
  straight-line fallback is drawn. Only the road route and ETA are missing.

**For real deployment, pick one:**

| Option | What changes in the app |
|---|---|
| Host your own OSRM instance (open source, runs on one small server with an OpenStreetMap extract for the Philippines) | Change `BASE_URL` in `RouteRepository.kt`. Same API, so nothing else changes. |
| OpenRouteService with a free API key (free plan has daily and per-minute limits, check the current ones on openrouteservice.org/plans) | Change `RouteRepository.kt` **and** write a parser for its different answer format (`OsrmParser` only reads OSRM). The key must not be hard-coded where it can be extracted; a small proxy or Cloud Function is safer. |

Also display the map and route credit "© OpenStreetMap contributors" (ODbL) where maps are shown.
The prototype does not show this yet.

## 3. A notification must be visible while a responder shares a location

Sharing runs as an Android **foreground service** of type `location`. Android requires such a
service to show an ongoing notification for as long as it runs, and this is also the responder's
sign that their position is being shared.

- The notification reads "Sharing your location", says who can see it, and has a **Stop sharing**
  button. It must not be hidden or removed in code.
- The service can only be started while the app is on screen (Android 12+), which is why Start
  response starts it.
- On Android 13+ the responder can switch notifications off for the app, or swipe the notice away.
  Sharing then still runs, but without a visible notice. Android still lists the app in its
  "active apps" panel. Tell responders to keep notifications on during a response.
- Sharing stops by itself when the incident leaves "Dispatched", when the responder taps Stop, or on
  sign-out.

## Other limits worth stating

- Position accuracy depends on the phone's GPS. Indoors or in tall areas it can be off by tens of
  metres or stop for a while (the citizen then sees the grey "last updated" state).
- "Last updated" ages are counted on the citizen's phone clock, so a badly wrong clock shows a wrong
  age.
- Phone makers' battery savers (Xiaomi, Oppo, Vivo and others) can stop background services even
  after Android's own exemption is allowed. The app asks, but cannot force it.
- If the responder's app is killed, their last position stays on the citizen's map (grey) because
  nothing was left running to remove it.
