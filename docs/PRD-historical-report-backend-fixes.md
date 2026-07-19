# PRD — Historical Report & Call-Log Backend Fixes

**Status:** Draft for review — backend changes; app side already patched separately.
**Owner:** Backend team (Node/Express + MongoDB/Mongoose).
**Reviewed files:** `callRoutes.js`, `callController.js`, `callService.js`, `models/CallLog.js`, `historical_reports_logic.md`.
**Last updated:** 2026-07-22.

---

## 1. Context & problem

The mobile app posts device call records to `POST /api/calls`; the backend aggregates them for the
Historical Performance Report (`GET /api/calls/historical`) and the live panels. Agents report that
`totalDials` is **inflated** — e.g. one call shows as 2 dials — and a **new agent with no calls shows
non-zero data**. A code review of the backend found the causes below.

The app-side contributor (overlapping syncs double-posting one call) has been fixed in
`CallLogSyncRepository.syncNewCalls` (a `Mutex` now serializes runs). This PRD covers the **backend**
defects, which remain even after the app fix — including duplicates already stored and device-level
double-logging the app cannot prevent.

---

## 2. Findings

Severity: **P0** = wrong numbers users see now; **P1** = wrong under common conditions; **P2** = correctness/robustness.

### F1 (P0) — No duplicate protection when logging a call
**Where:** `callService.js:11-19`, `models/CallLog.js`.
```js
const callLog = new CallLog({ agentId, duration, timestamp, status, contactNumber });
await callLog.save();               // always inserts a new document
```
`logCall` inserts unconditionally, and `CallLogSchema` declares **no unique index**. Any repeated POST
of the same call (client retry, observer double-fire, dual-SIM/VoLTE double-logging) creates another
document. Because `totalDials = { $sum: 1 }` counts documents, duplicates directly inflate the count.
**This is the primary cause of "1 call → 2 dials."**

### F2 (P0) — Historical date filter is all-or-nothing → counts all-time
**Where:** `callService.js:28-33`.
```js
if (startDate && endDate) { matchQuery.timestamp = { $gte: ..., $lte: ... }; }
```
If **either** bound is missing, no time filter is applied and the aggregation counts **every record ever
logged**. A "new" agent then shows historical/seed/old data instead of `0`. "Historical" without a range
should not silently mean "all time."

### F3 (P1) — End-date boundary excludes the final day
**Where:** `callService.js:31`.
```js
$lte: new Date(endDate)   // "2024-06-15" → 2024-06-15T00:00:00Z (midnight)
```
A date-only `endDate` parses to midnight UTC, so all calls *during* the end day are excluded, and the
range is timezone-sensitive. Users lose the last day of any range.

### F4 (P1) — `totalDials` counts all statuses, and the model can't distinguish direction
**Where:** `callService.js:41`, `models/CallLog.js:20-24`.
`$sum: 1` counts `Connected + Missed + Failed + Voicemail`, i.e. **incoming calls too**, not just
outgoing dials. The schema `status` enum has **no direction field**, so the backend cannot currently
tell an outgoing dial from a received call. Aligning `totalDials` with "outgoing attempts" needs a
schema addition (a `direction` field), not just a query tweak.

### F5 (P1) — Live activity uses server-local midnight
**Where:** `callService.js:119-120`.
```js
const startOfDay = new Date(); startOfDay.setHours(0,0,0,0);   // SERVER timezone
```
"Today's" first/last call is bounded by the **server's** midnight, not the agent's. Wrong day window
whenever server and agent timezones differ.

### F6 (P2) — Robustness / doc mismatches
- **`getLiveStatus` has no time scope** (`callService.js:79-83`): idle time is computed from the all-time
  last call, so a long-idle or returning agent reads incorrectly.
- **`$unwind: "$agent"`** (lines 59, 101, 144): an inner unwind **silently drops** call logs whose
  `agentId` no longer resolves to a user (deleted/renamed account). Use
  `preserveNullAndEmptyArrays: true` if those rows should still surface.
- **Route comments say "Admin only"** (`callRoutes.js:11-19`) but the controller correctly self-scopes
  non-admins to their own `userId`. Comment is misleading; behavior is fine.
- **`logCall` accepts client `timestamp` verbatim** (`callService.js:6,16`): no validation/clamping, so a
  bad client clock can place calls outside any range or in the future.

---

## 3. Goals / non-goals

**Goals**
- One real call = exactly one counted dial (idempotent logging + de-dup of existing data).
- A new agent with no calls returns `0` across all report fields.
- Date ranges are inclusive and timezone-correct; a missing range has clearly defined behavior.
- `totalDials` reflects a defined, agreed meaning of "dial."

**Non-goals**
- No change to the app's posting contract beyond the already-shipped mutex (payload shape unchanged).
- No new analytics fields or endpoints.
- No historical back-fill of a `direction` value for records already stored without one.

---

## 4. Proposed changes

### C1 — Idempotent logging + unique index (fixes F1)
1. Add a stable idempotency key so re-posting the same call cannot create a second row. Preferred:
   the app sends a `clientCallId` (device call-log `_id` + a device/install id); backend upserts on it.
   ```js
   // CallLog.js
   clientCallId: { type: String, required: false, index: true }
   CallLogSchema.index({ agentId: 1, clientCallId: 1 }, { unique: true, sparse: true });
   ```
   ```js
   // callService.logCall — upsert instead of blind insert
   await CallLog.updateOne(
     { agentId, clientCallId },
     { $setOnInsert: { agentId, clientCallId, duration, timestamp, status, contactNumber } },
     { upsert: true }
   );
   ```
2. If a `clientCallId` cannot be added yet, use an interim natural-key unique index to reject exact
   repeats: `{ agentId, timestamp, contactNumber, status }` unique.
3. **One-time cleanup migration:** de-duplicate existing `call_logs` on the chosen key, keeping the
   earliest document, before the unique index is built.

### C2 — Explicit, inclusive, timezone-safe date handling (fixes F2, F3)
- Define missing-range behavior deliberately. Recommended: default to a bounded window (e.g. last 30
  days) rather than all-time; document it. If all-time is intended, make it an explicit flag.
- Make `endDate` inclusive of the whole day and honor a timezone: add one day (or set to end-of-day) and
  interpret dates in the requested `tz`, e.g.:
  ```js
  matchQuery.timestamp = { $gte: startOf(startDate, tz), $lt: startOf(nextDay(endDate), tz) };
  ```

### C3 — Define and correctly compute `totalDials` (fixes F4)
Pick one, product-approved:
- **Option A (recommended):** add `direction: { enum: ['outgoing','incoming'] }` to the schema; app sends
  it; `totalDials` counts `direction === 'outgoing'`. Most correct.
- **Option B:** count by status set (e.g. exclude pure-incoming statuses) — approximation only, since the
  current enum cannot fully separate direction.
Document the chosen definition in `historical_reports_logic.md` (§4) so app and backend agree.

### C4 — Timezone-correct live activity + robustness (fixes F5, F6)
- Compute `startOfDay` in the agent's timezone (pass `tz` or store per-user).
- Add a time scope to `getLiveStatus` (e.g. today) if idle should reset daily.
- Use `$unwind: { path: "$agent", preserveNullAndEmptyArrays: true }` where orphaned logs must still count.
- Validate/clamp incoming `timestamp` in `logCall` (reject future-dated or absurd values).
- Fix the "Admin only" route comments to "self-scoped; admins see all."

---

## 5. Acceptance criteria

- [ ] Posting the same call twice results in **one** stored document and `totalDials` counts it once.
- [ ] A brand-new agent with zero calls returns `0` for every field in `/historical`.
- [ ] A range `startDate=D … endDate=D` includes calls throughout day `D` (inclusive, correct tz).
- [ ] Missing `startDate`/`endDate` follows the documented default (not silent all-time), verified by test.
- [ ] `totalDials` matches the agreed definition (outgoing-only if Option A) and matches the app's
      own on-device dial count for the same window.
- [ ] Existing duplicate `call_logs` removed by the cleanup migration; unique index present.
- [ ] `historical_reports_logic.md` updated to reflect the final `totalDials` definition and date semantics.

---

## 6. Rollout & risk

1. Ship app mutex fix (done) to stop new duplicates at the source.
2. Add `clientCallId` to the app payload (small, additive) and to the schema.
3. Run the de-dup migration in a maintenance window (**destructive** — take a DB backup first; deletes
   duplicate rows). Then build the unique index.
4. Deploy C2–C4. Re-run the report and compare against a known agent's device call count to confirm parity.

**Risks:** the de-dup migration is irreversible without a backup; a too-narrow interim natural key could
merge genuinely-distinct rapid calls (mitigated by moving to `clientCallId`). Changing the default date
window is user-visible — announce it.

---

## 7. Open questions

- What should a missing date range mean — last 30 days, current month, or explicit all-time flag?
- Final definition of a "dial": outgoing attempts only (needs `direction`), or all logged calls?
- Can the app reliably provide a stable `clientCallId` per call across reinstalls, or is the natural-key
  index the pragmatic path?
- Which timezone source is authoritative for daily windows — per-user setting, request param, or fixed?
