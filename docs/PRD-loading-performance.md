# PRD — Faster screen loads (Dashboard, Leads, Add Lead, Profile)

**Status:** Draft for review
**Date:** 2026-07-23
**Owner:** Krish
**Scope:** Perceived load latency and loading UX on the four main authenticated screens.

---

## 1. Problem

Entering Dashboard, Leads, Add Lead, and Profile feels slow. The app shows a
full-screen spinner and the content only appears once *all* background work
(call-log read, 3–4 network calls, lead sync) has finished.

### Root cause — corrected framing

The expensive work is **already off the main thread**:

- `DashboardRepository.getDashboard()` runs in `withContext(Dispatchers.IO)`.
- `CallLogReader.readAll()` wraps its `ContentResolver` cursor in `Dispatchers.IO`.
- Retrofit `suspend` calls dispatch off-main by themselves.
- Room DAOs are `suspend` / `Flow`.

So this is **not** main-thread jank. The real problems are:

1. **First paint blocks on the network.** The UI gates on a full-screen
   `CircularProgressIndicator` (`state.isLoading && data == null`) and doesn't
   render until everything resolves.
2. **The Dashboard waits on lead network sync it doesn't need.**
   `DashboardViewModel.load()` runs `leadsRepo.syncLeads()` and then calls
   `syncJob.join()` before showing dashboard data — even though the dashboard is
   computed almost entirely from the **local** call log + **local** Room leads.
3. **One network call is accidentally serial.** In `getDashboard()`,
   `fetchAgentMetrics` / `fetchAttendanceLogs` / `leadDao.getAllLeads()` run via
   `async`, but `fetchMonthlyAttendance(...)` is `await`-ed *after* them, adding a
   full extra round-trip to the critical path.
4. **No cache across ViewModel recreation.** Dashboard/Profile ViewModels are
   scoped to their nav entry. Navigating away and back recreates them, so
   `data == null` again → the full-screen spinner returns and everything
   recomputes from scratch.
5. **The progress bar is under-used.** A thin `LinearProgressIndicator` already
   exists below the Dashboard and Leads top bars, but it's gated on *already
   having data* (`data != null` / `leads.isNotEmpty()`), so it never shows during
   the slow first load — the full-screen spinner shows instead.

### Per-screen findings

| Screen | Current behavior | Real bottleneck |
|---|---|---|
| **Dashboard** | Full-screen spinner until call-log read + 3–4 network calls + `syncLeads().join()` all finish. Recomputes on every entry + every `ON_RESUME`. | Blocks first paint on network; serial monthly-attendance call; no cache; unnecessary `syncJob.join()`. |
| **Leads** | Cached leads render fast via Room `Flow`. On a **cold/empty** DB, full-screen spinner until first network sync. | Full-screen spinner on empty; top-bar progress bar gated on `leads.isNotEmpty()`. |
| **Add Lead** | Form renders instantly. But it gets its **own** `LeadsViewModel` instance (separate nav entry), whose `init` fires a redundant `syncLeads()` network call. | Wasted network churn on entry; no visible delay to the form itself. |
| **Profile** | Renders **immediately** from session name/email; `getProfile()` updates in place. | Effectively fine already. `isLoading` initial flag is unused; no progress indicator. |

**Priority order:** Dashboard (high) → Leads cold-start (medium) → Add Lead redundant sync (low) → Profile progress affordance (low).

---

## 2. Goals

- **Instant first paint from local/cached data.** No screen shows a full-screen
  spinner when *any* data (cached, stale, or locally-derivable) can be shown.
- **Network never blocks content.** Render from local sources first; refresh
  network-derived fields in the background.
- **A thin progress bar under the top bar** communicates background refresh on
  all four screens, replacing the blocking full-screen spinner.
- **Re-entry is instant.** Returning to Dashboard shows the last result
  immediately, then refreshes.
- Keep the existing architecture: single `data class` UiState, one ViewModel per
  screen, repositories as the boundary, offline-first. **No** sealed UiState, **no**
  new libraries, **no** layer restructuring.

### Non-goals

- No redesign of the dashboard stat math or the call-log matching logic.
- No new backend endpoints (the metrics endpoints stay best-effort as today).
- No conversion to a shared/activity-scoped ViewModel unless a smaller fix is insufficient.

---

## 3. Proposed changes

### Global principle
> Render from local/cached state on frame 1. Show a `LinearProgressIndicator`
> below the top bar while a background refresh runs. Only ever show a full-screen
> loader when there is genuinely nothing — no cache, no local data, first compute
> not yet done — and prefer a lightweight skeleton even then.

### 3.1 Dashboard (high priority)

**ViewModel / Repository (`DashboardViewModel`, `DashboardRepository`)**

- **Cache last result in the Singleton repository.** `DashboardRepository` is
  `@Singleton`, so an in-memory `@Volatile var lastData: DashboardData?` survives
  ViewModel recreation. On `load()`, if a cache exists, emit it immediately
  (`isLoading` drives only the top-bar bar, not a full-screen spinner) and then
  recompute.
- **Two-stage emit in `getDashboard()`:**
  1. Compute the **local** stage first — call log (`readAll`) + local leads
     (`leadDao.getAllLeads().first()`) → `buildDailyStats` / `buildMonthlyStats`
     with placeholder target/attendance → return/emit. This is the fast path.
  2. Fetch the **network** stage — metrics, attendance logs, monthly attendance —
     and emit an updated `DashboardData` when they arrive.
  (Implementation option: expose `getDashboard()` as a `Flow<DashboardData>` that
  emits local-first then network-updated, or keep two `suspend` calls the VM
  chains. Chosen shape decided at implementation; must not regress the existing
  `Result` error contract.)
- **Parallelize the network stage fully.** Move `fetchMonthlyAttendance(...)` into
  the same `async` batch as `fetchAgentMetrics` / `fetchAttendanceLogs` so it's not
  an extra serial round-trip.
- **Drop the blocking `syncJob.join()`.** Let `leadsRepo.syncLeads()` run detached;
  on its completion trigger a `load(silent = true)` recompute so booking counts
  refresh without blocking first paint.

**Screen (`DashboardScreen`)**

- Replace the full-screen `CircularProgressIndicator` (when `data == null`) with
  either the cached data or a lightweight skeleton of the two cards.
- Show the top-bar `LinearProgressIndicator` whenever `state.isLoading`
  (remove the `&& state.data != null` gate).

### 3.2 Leads (medium priority)

- Distinguish **"loading, never synced"** from **"synced, genuinely empty."** Add a
  flag (e.g. `hasSynced: Boolean`) to `LeadsUiState`, set on first `syncLeads()`
  completion.
- On cold/empty DB: **don't** show the full-screen spinner. Show the top-bar
  progress bar; show the "No leads yet" empty state only once `hasSynced` is true.
- Show the top-bar `LinearProgressIndicator` even when `leads.isEmpty()` (remove the
  `leads.isNotEmpty()` gate) so refresh is visible on an empty list.

### 3.3 Add Lead (low priority)

- Avoid the redundant `syncLeads()` fired by the second `LeadsViewModel` instance on
  entering Add Lead. Preferred minimal fix: **share one `LeadsViewModel` instance**
  between the Leads and Add Lead destinations (scope it to a shared parent nav
  graph route), so entering Add Lead reuses the already-synced instance and fires
  no new network call. Fallback: guard `sync()` so it no-ops when a recent sync is
  in flight/completed.
- The form itself already renders instantly — no loader change needed.

### 3.4 Profile (low priority)

- Already renders from session immediately; keep that.
- Add the top-bar `LinearProgressIndicator` while `state.isLoading` for consistency
  with the other screens (the initial `isLoading = true` flag is currently unused by
  the UI).

### 3.5 Threading (verify only — mostly already correct)

- Confirm every repository IO path stays on `Dispatchers.IO` and there is no
  `runBlocking` / main-thread cursor or network work. This is already the case in
  `getDashboard()`, `CallLogReader`, and Room/Retrofit; the PRD records it as a
  verification step, **not** a rewrite. The perceived win comes from §3.1–3.4, not
  from moving work off-main (it's already off-main).

---

## 4. Success criteria

- Dashboard, Leads, Add Lead, and Profile show content (or cached content) on the
  first frame — no full-screen spinner when data or a cache is available.
- Returning to the Dashboard after navigating away shows the previous result
  instantly, with a top-bar progress bar during refresh.
- Dashboard network calls in the refresh stage run concurrently (no serial
  monthly-attendance round-trip).
- First paint no longer waits on `syncLeads()`.
- A thin progress bar under the top bar is the standard "refreshing" affordance on
  all four screens; the full-screen spinner appears only on a true cold start with
  no local data.
- No regression to the `Result<T>` → `error: String?` contract, offline-first
  behavior, the assignment-cutoff call filtering, or the single-`data class` UiState
  convention.

---

## 5. Affected files

| File | Change |
|---|---|
| `viewModel/ViewModels.kt` | `DashboardViewModel.load()` (drop `join`, two-stage emit, use cache); `LeadsUiState` (+`hasSynced`) & `LeadsViewModel` (empty-state gating); Profile progress flag if needed. |
| `LeadDetailVM/repository/Repositories.kt` | `DashboardRepository`: in-memory `lastData` cache, split local/network stages, parallelize `fetchMonthlyAttendance`. |
| `ui/screens/DashboardScreen.kt` | Replace full-screen spinner with skeleton/cached content; ungate top-bar progress bar. |
| `ui/screens/LeadsListScreen.kt` | Empty-vs-loading gating; ungate top-bar progress bar. |
| `ui/NavGraph.kt` | (Add Lead) share one `LeadsViewModel` instance across Leads/Add Lead, if that option is chosen. |
| `ui/screens/ProfileScreen.kt` | Optional top-bar progress bar. |

---

## 6. Open questions / decisions for confirmation

1. **Dashboard cache lifetime** — in-memory only (simplest, clears on process
   death) vs persisted (Room/DataStore, survives cold start). Recommend
   **in-memory** first; persist later only if cold-start speed still matters.
2. **`getDashboard()` shape** — return a `Flow<DashboardData>` (local-then-network
   emissions) vs two `suspend` calls the VM sequences. Recommend the **Flow** for a
   clean two-stage emit; confirm it's acceptable given the current `suspend`+`Result`
   style.
3. **Add Lead** — share one `LeadsViewModel` across the two destinations (cleaner,
   touches NavGraph) vs guard `sync()` (smaller, stays in the VM). Recommend the
   **shared instance**.
4. **Skeleton vs cached-only for Dashboard first-ever load** — do we want skeleton
   placeholder cards, or is the top-bar bar over a blank background enough?

---

## 7. Test / verification plan

- Only JUnit 4.13.2 is on the classpath today (no MockK/Turbine/Compose UI test),
  per project rules — so automated coverage is limited to pure logic.
- Add/extend JUnit tests for any **pure** extracted logic (e.g. the local-stage
  stat assembly, `hasSynced` transitions) where it can be tested without Android.
- Manual verification: cold start (empty DB), warm re-entry, airplane-mode
  (offline-first still renders local), and `ON_RESUME` refresh — confirm no
  full-screen spinner when data/cache exists and the top-bar bar shows during
  refresh.
- Build with `JAVA_HOME=D:/android/jbr` then `./gradlew assembleDebug`; run any
  existing relevant test before claiming pass.
