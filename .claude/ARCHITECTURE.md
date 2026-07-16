# Architecture Map — Sales CRM

Read this first to know **where things live** so you don't have to Glob/read the tree.
Paths are under `app/src/main/java/com/crmapplication/`. Keep this file in sync when the
layout changes.

## One-paragraph mental model
Compose UI → one `*ViewModel` per screen (all in `viewModel/ViewModels.kt`) → repositories
(the boundary) → Retrofit APIs (`remote/`) + Room (`local/`). Offline-first: leads/notes are
read from Room via Flow; the network writes back. Dashboard stats are computed **on-device
from the call log**, not fetched.

## Where things live
| Concern | Path |
|---|---|
| App entry / Compose host | `MainActivity.kt` |
| Navigation graph + `Routes` | `ui/NavGraph.kt` |
| Screens (one file each) | `ui/screens/` |
| Shared Composables | `ui/component/Components.kt` |
| Theme | `ui/theme/` |
| **All ViewModels + UiStates** | `viewModel/ViewModels.kt` |
| Repositories + domain models | `LeadDetailVM/repository/` (`Repositories.kt`, `Models.kt`) |
| Retrofit APIs + DTOs + `ApiConfig` | `LeadDetailVM/remote/` |
| Room (entities, DAOs, DB, converters) | `LeadDetailVM/local/` |
| Call-log reading | `calllog/` |
| Formatters, session, upload helpers | `utils/` |
| Hilt modules | `di/AppModule.kt`, `di/NetworkModule.kt` |

> ⚠️ The data layer is under the misleadingly-named **`LeadDetailVM/`** — it holds ALL data
> code (remote/local/repository), not just lead-detail.

## Screen → ViewModel → what it touches
| Screen (`ui/screens/`) | ViewModel | Repository |
|---|---|---|
| Register / OtpVerify / Login / ForgotPassword / ResetPassword | `AuthViewModel` | `AuthRepository` |
| `DashboardScreen` | `DashboardViewModel` | `DashboardRepository` (+ `LeadsRepository.syncLeads`) |
| `LeadsListScreen`, `AddLeadScreen` | `LeadsViewModel` | `LeadsRepository` |
| `LeadDetailScreen` | `LeadDetailViewModel` | `LeadsRepository` + `CallLogReader` |
| `ProfileScreen` | `AuthViewModel` (name/email/logout) | — |

## Nav routes (`ui/NavGraph.kt` → `object Routes`)
`register` · `verifyOtp` · `login` · `forgotEmail` · `forgotOtp` · `forgotReset` ·
`dashboard` · `leads` · `addLead` · `profile` · `lead/{leadId}`
Start dest = `dashboard` if logged in, else `register`. Lead detail is reached via
`Routes.leadDetail(id)`.

## Where to add a new... (recipe pointers)
- **Screen** → new file in `ui/screens/`, add ViewModel + UiState to `viewModel/ViewModels.kt`,
  register a route in `Routes` + a `composable(...)` in `CrmNavGraph`. See `.claude/commands/add-screen.md`.
- **API call** → add method to the right interface in `remote/` (leads → `LeadsApi`,
  auth → `AuthApi`, upload → `UploadApi`), add request/response DTO in the same file,
  call it from the matching repository. No new Retrofit needed — all three share one instance.
- **Persisted field** → add to `LeadEntity`/`NoteEntity` (`local/Entities.kt`), map in
  `Models.kt`, add a DAO query if needed. Room uses `fallbackToDestructiveMigration()` —
  a schema change wipes the local DB on next launch (fine; it re-syncs).
- **DI binding** → data/DB → `AppModule`; network → `NetworkModule`.

## Traps that cause wrong code (read before editing)
1. **Package ≠ applicationId.** Source is `com.crmapplication`; namespace/appId is
   `com.salescrm`. `BuildConfig` is **`com.salescrm.BuildConfig`** — import from there.
2. **`ApiService` is faked — but notes no longer use it.** `AppModule.provideApiService()`
   returns `FakeApiService()`. It's now used only for `setDueDate` (fire-and-forget, local-only).
   **Notes are real & two-way** via `LeadsApi`: `addNote`/`deleteNote`/`refreshLeadNotes` hit
   `POST|DELETE|GET api/leads/{id}/notes...` and reconcile Room with the server (admin notes sync
   down, agent notes sync up, attachments via note `imageUrl`). Real leads/auth/upload traffic
   goes through `LeadsApi` / `AuthApi` / `UploadApi`.
3. **Dashboard is mostly computed on-device.** `DashboardRepository.getDashboard()` reads the
   call log for most stats. It DOES make one best-effort call — `AgentsApi` GET
   `api/agents/{id}/metrics` — for the monthly target and today's attendance (P/A); a failure
   is swallowed and the cards fall back to placeholders. `FakeApiService.getDashboard` is unused.
4. **Local-only fields** `dueDate` and `statusChangedAt` are re-applied on every `syncLeads()`
   so a refresh doesn't wipe them. Preserve that logic.
5. Exact field names / endpoints live in `.claude/reference/api-and-models.md` — check it
   instead of guessing.
