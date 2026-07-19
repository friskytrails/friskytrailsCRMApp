# PRD — Call-Log Reporting Data Contract (Android)

**Status:** Implemented (data layer only — no UI)
**Date:** 2026-07-24
**Owner:** Android app (`com.crmapplication`)
**Scope:** What the Android app POSTs to `POST /api/calls`, and how that feeds the three
server-computed reports (`/historical`, `/live-status`, `/live-activity`).

---

## 1. Problem

The three call-analytics reports are computed **entirely server-side** from the `CallLog`
records the app posts (see `call_log_api_docs.md` and `historical_reports_logic.md`). The Android
app does **not** render these reports — its only responsibility is to POST correct, well-formed
call records. Reported symptoms:

1. Live Activity audit didn't show an agent's first/last call after they logged in and called.
2. Live Status idle looked wrong / agents appeared idle when they weren't.
3. Historical report values looked off.

All three are downstream of **one field**: every report is derived from the posted records, and
the two live reports bucket/measure time from the `timestamp` field specifically.

---

## 2. Decisions (locked with product)

| # | Decision | Rationale |
|---|----------|-----------|
| **a** | **Count both incoming and outgoing calls as dials.** | Product wants `totalDials` to reflect all handled calls, not just outbound. No code change needed — `callStatusFor` already maps both directions. |
| **b** | **Keep the assigned-lead filter.** | Only calls to an assigned lead (at/after assignment) are posted. Preserves agent privacy (personal calls never leave the device) and keeps reports lead-scoped. No code change needed. |

Consequence of (b): a call to a **non-lead** number is intentionally **never** posted, so it will
not appear in any report. This is by design, not a bug.

---

## 3. What changed

**Root cause:** the posted `timestamp` used `formatIso8601`, which emits a **local UTC offset**
(e.g. `2026-07-24T14:30+05:30`) and — via `OffsetDateTime.toString()` — **drops the seconds**
when they are `:00`. The backend buckets "today" (live-activity) and computes idle (live-status)
from this string, and the documented contract is canonical UTC (`2026-07-21T17:00:00.000Z`).
A local-offset, variable-precision string is fragile for both.

**Fix (surgical, POST-only):**
- Added `formatIso8601Utc(epochMs)` in `utils/Helpers.kt` → always
  `yyyy-MM-dd'T'HH:mm:ss.SSS'Z'` in UTC (seconds + millis + `Z`, never dropped, never a local
  offset). Backed by a thread-safe `DateTimeFormatter`.
- `CallLogSyncRepository.syncNewCalls` now sends `timestamp = formatIso8601Utc(entry.dateMillis)`.
- `bookingFromCalls` (a different endpoint, `LeadsApi.updateBooking`) was intentionally left on
  the existing formatter — out of scope, contract unverified.

**Unchanged (already correct):** direction mapping, assigned-lead filter, `clientCallId`
idempotency, and the `Mutex` that serializes `syncNewCalls`.

---

## 4. POST /api/calls — request contract (as sent by the app)

| Field | Source | Notes |
|-------|--------|-------|
| `status` | `callStatusFor(entry)` | `Connected` (duration>0), `Missed`, `Failed`, `Voicemail`. Incoming & outgoing both mapped. |
| `duration` | `entry.durationSeconds` | Seconds. |
| `contactNumber` | `entry.number` | Omitted when blank/"Unknown". |
| `timestamp` | `formatIso8601Utc(entry.dateMillis)` | **Canonical UTC** `…SSS'Z'`. ← the fix. |
| `clientCallId` | `"$installId-${entry.id}"` | Idempotency key; backend upserts on `(agentId, clientCallId)`. |
| `leadId` | `leadIdByKey[key]` | Set when the number matches an assigned lead; else null. |

Posting gate (a call is posted only if **all** hold): status is non-null · call is **today** ·
number matches an **assigned lead** · call is **at/after** that lead's assignment stamp. The
watermark advances even for skipped calls so they're never reprocessed.

---

## 5. How each report is derived (server-side, for reference)

### Historical — `GET /api/calls/historical`
Aggregated per agent over `[startDate, endDate]`:

| Field | Formula | Depends on |
|-------|---------|-----------|
| `talkTime` | `SUM(duration)` | `duration` |
| `totalDials` | `COUNT(*)` | one record per posted call |
| `uniqueCalls` | `COUNT(DISTINCT contactNumber)` | `contactNumber` |
| `connectedCalls` | count `status == "Connected"` | `status` |
| `longCalls` | count `duration >= 300` | `duration` |
| `tenure`, `name` | from `users` (account age / name) | not app-supplied |

### Live Status — `GET /api/calls/live-status`
`lastCallAt` = most recent posted `timestamp`; `idleMs` = server-now − `lastCallAt`.
→ Correct `timestamp` is what makes idle accurate. **Fixed.**

### Live Activity — `GET /api/calls/live-activity`
`firstCall` / `lastCall` = earliest / latest posted `timestamp` **today** (server day from 00:00).
→ Correct UTC `timestamp` is what makes today-bucketing reliable. **Fixed.**

---

## 6. Known limitations / non-goals

- **Timezone bucketing is server-side.** The fix sends the correct absolute instant in UTC; if the
  device and server disagree on "today", the day boundary is the server's call. The app can't
  resolve that alone.
- **Non-lead calls are not reported** (decision b). Agents whose activity is entirely non-lead
  calls will still show empty live-activity — expected.
- **No app UI** for these reports; they are consumed by the admin/backend surface.
- **`updateBooking` timestamps** were not touched (different endpoint, out of scope).

---

## 7. Verification

- `assembleDebug` + `testDebugUnitTest`: **BUILD SUCCESSFUL**, all unit tests pass.
- `CallTimestampTest` locks the wire format: canonical UTC with millis + `Z`, seconds never
  dropped on an exact minute.
- The three GET methods (`getHistoricalReport`, `getLiveStatus`, `getLiveActivity`) and their DTOs
  were audited field-by-field against `call_log_api_docs.md` — they match; no change required.
