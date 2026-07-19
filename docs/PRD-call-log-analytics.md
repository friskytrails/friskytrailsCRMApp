# PRD — Call Log Analytics Sync & Panels

**Status:** Draft for review — no code will be written until approved.
**Owner:** (agent app team)
**Source of truth:** `call_log_api_docs.md` (`/api/calls` service).
**Last updated:** 2026-07-22

---

## 1. Context & problem

The device call log is the *ground truth* for what an agent dialed. The backend needs that
data to power team monitoring and analytics. Today:

- The app already POSTs new calls to `/api/calls` (`CallLogSyncRepository.syncNewCalls`), driven
  by the call-log `ContentObserver` (debounced 500 ms) plus dashboard load.
- The three analytics endpoints (`/historical`, `/live-status`, `/live-activity`) now exist in the
  Retrofit + repository layer, but **nothing in the UI reads them yet**.
- Dashboard tiles are still computed **client-side from the device call log**, independent of the server.

The goal: keep the server's analytics **continuously in sync with the phone**, and surface the
three server-computed views as panels in the app.

## 2. Critical clarification on data flow (read before scoping)

Only **one** of the four endpoints is a write. The other three are read-only aggregations the
**backend computes** from the calls already POSTed. There is **no separate "update endpoint"** for
the panels — they cannot be pushed to.

| Endpoint | Direction | Who computes the numbers | How it stays fresh |
| :--- | :--- | :--- | :--- |
| `POST /api/calls` | App **writes** | App sends each call | Fires on every call-log change |
| `GET /api/calls/historical` | App **reads** | Backend aggregates | App **re-fetches** |
| `GET /api/calls/live-status` | App **reads** | Backend (idle since last call) | App **re-fetches / polls** |
| `GET /api/calls/live-activity` | App **reads** | Backend (today's first/last) | App **re-fetches** |

**So "keep updating as the phone changes" = two independent guarantees:**
1. **Write reliability** — every relevant device call reaches `POST /api/calls` (the panels are only
   as correct as the POSTs behind them).
2. **Read freshness** — each panel re-fetches its GET at the right time (on open, on resume, and for
   live-status on a short poll).

## 3. Goals / non-goals

**Goals (in priority order)**
1. **Empty-state correctness (top priority).** A freshly signed-up agent, or any agent with no calls,
   must show **nothing real** — `—` (or a true `0`), never fabricated Total Dials / Unique Calls /
   talk time. This is currently WRONG on the dashboard (see §5A) and must be verified and fixed first,
   because the same numbers feed the daily → historical reporting.
2. **Historical report kept in sync with the call log.** As the device call log changes, the calls
   are POSTed and the Historical Performance Report reflects them; when there are no calls, it shows
   nothing.
3. Guarantee call-log changes are reliably POSTed to `/api/calls` (correct status enum, ISO timestamp).
4. Add the read panels backed by the GET endpoints, kept fresh on open/resume (+ poll for idle).

**Non-goals**
- No new backend endpoints; no attempt to "push" analytics (the reports are read-only — see §2).
- No admin cross-agent views — the agent JWT scopes every response to self.

## 4. Endpoint contract summary (per doc)

- **`POST /api/calls`** — body `{status, duration?, contactNumber?, timestamp?}`; `status` enum =
  `Connected | Missed | Failed | Voicemail`; `→ 201` with the created `CallLog`. `agentId` from JWT.
- **`GET /historical?startDate&endDate&team`** — `[{agentId, name, tenure, talkTime, totalDials,
  uniqueCalls, connectedCalls, longCalls}]`. `longCalls` = duration ≥ 300 s; `talkTime` in seconds.
- **`GET /live-status`** — `[{agentId, name, lastCallAt, idleMs}]`. `idleMs` = now − lastCallAt.
- **`GET /live-activity`** — `[{agentId, name, firstCall, lastCall}]` for **today** (server tz).

## 5A. Known issue to verify & fix first (empty state)

**Symptom (reported):** A freshly signed-up agent with **no calls** still sees non-zero
**Total Dials** and **Unique Calls** on the dashboard. Because the dashboard's daily numbers are the
basis for daily → historical reporting, any wrong value here propagates into the server-side report.

**Why it matters:** The whole point of this work is that "no calls" must read as `—`/`0`. If the
dashboard invents numbers, the Historical Performance Report will too.

**Verification task (before writing any panel):** trace and confirm the daily computation actually
returns zero/empty for a no-call agent. Concretely check:
- `DashboardRepository.buildDailyStats` — `leadCalls = allCalls.filter { key in leadKeys }`. Confirm
  an **empty `leadKeys`** (no leads) or **no matching calls** yields `totalDials = 0`, `uniqueCalls = 0`.
- `normalizedPhoneKey()` on blank/short numbers must not collapse to a shared key that accidentally
  matches unrelated device calls (a lead with a blank/`""` phone must not match every call).
- Whether the dashboard counts **all device calls** anywhere vs only **lead-matched** calls — decide
  and document which is intended (this is likely the root of the phantom counts).
- Confirm `firstCall`/`lastCall`/`idleTime` already render `—` when empty (they were changed to do so).

**Requirement FR-0 (blocking):** No-call / new-user state shows `—` (or true `0`) for every metric on
the dashboard AND every panel. This must be reproduced and fixed/confirmed before FR-2..FR-4 are built.

## 5. Functional requirements

**FR-1 Write sync (foundation).** On every call-log change and on app open, POST all new device
calls not yet sent. Map status via existing `callStatusFor`. Timestamp as ISO 8601. When there are
no calls, nothing is sent (no zero-value writes).

**FR-2 Historical report (primary).** Fetch `/historical` (default range per D3) on open, on manual
refresh, and after a sync completes so it tracks call-log changes. Render the self row: tenure, talk
time, dials, unique, connected, long calls. **Empty/new agent → all `—`, never fabricated numbers**
(this is the same class of bug as §5A — verify the report renders empty for a no-call agent).

**FR-3 Live-status panel.** Fetch `/live-status` on open, then poll while visible (interval per D4).
Render last-call time + a human idle duration derived from `idleMs`.

**FR-4 Daily-activity panel.** Fetch `/live-activity` on open/resume. Render today's first & last call.

**FR-5 States.** Every panel shows loading, error (friendly, via existing `mapApiError`), and an
empty state (`—`) when the agent has no calls yet — never fabricated/zero-as-real values.

## 6. Open decisions (need your answer before build)

- **D1 — Baseline backfill.** First-ever sync sets the watermark to the newest call and posts
  **nothing older**, so pre-existing history never reaches the server → `/historical` under-counts.
  Options: (a) leave as-is; (b) one-time backfill of the last N days; (c) full backfill.
- **D2 — Dashboard relationship.** Keep the existing device-computed daily tiles as-is and add the
  server panels **separately**, or **switch** dashboard tiles to server data (risk: server has less
  history → numbers drop)?
- **D3 — Historical default range.** Today / this month / last 30 days / user-selectable?
- **D4 — Live-status poll cadence.** e.g. 30 s while the panel is visible; stop when backgrounded.
- **D5 — Panel placement.** New "Activity" screen with 3 sections, three cards on the dashboard, or
  a tab? (Affects NavGraph + which ViewModel owns the fetches.)
- **D6 — Duplicate protection.** The watermark prevents re-sends within a session; confirm whether a
  reinstall (DataStore cleared) re-baselining is acceptable, or if server de-dupes.

## 7. Proposed data flow (for reference, pending decisions)

```
Device call log ──observe(500ms debounce)──► syncNewCalls() ──POST /api/calls──► Backend store
                                                                                     │
Panel screen ──on open / resume / poll──► GET historical | live-status | live-activity
```

## 8. Success metrics

- 100% of new device calls (matching the enum-mappable types) POSTed within one refresh cycle.
- Panels reflect a just-made call after its POST + the next fetch, with no phantom values.
- No crash / no misleading zeros when the agent has zero server-side calls.

## 9. Risks

- **Under-counting** from the baseline skip (D1) — the single biggest correctness risk.
- **Server vs device divergence** if dashboard is switched to server data (D2).
- **Polling battery cost** for live-status (D4) — must stop when not visible.
- **Timezone**: `/live-activity` uses server tz for "today"; device day may differ near midnight.
