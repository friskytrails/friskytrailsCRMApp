# API & Data Model Reference — Sales CRM

The **source of truth for field names, endpoints, and payload shapes.** Check here before
writing any code that touches a model or endpoint — do NOT invent fields. Generated from the
code; if the code changes, update this file. File pointers in parentheses.

Base URL: `https://friskytrails-crm-pdte.vercel.app/` (`remote/ApiConfig.kt`)
All three real APIs share one Retrofit instance (`di/NetworkModule.kt`).
Auth header: `"Bearer <jwt>"`, JWT saved at login by `SessionManager`.

---

## Domain models (`repository/Models.kt`) — what ViewModels/UI use

```kotlin
Lead(
  id: String, name: String, phone: String,
  totalDial: Int = 0, connected: Int = 0, talkTime: String = "",
  firstCall: String? = null, lastCall: String? = null,
  labels: List<String> = emptyList(),
  status: String = "New",            // one of LEAD_STATUSES
  statusChangedAt: Long? = null,     // LOCAL-ONLY, epoch millis
  createdAt: Long, dueDate: Long?,   // dueDate LOCAL-ONLY, epoch millis
  notes: List<Note> = emptyList(),
)
Note(id, leadId, text, timestamp: Long,          // timestamp = epoch millis (sort key)
     authorName?, authorId?, imageUrl?, timeLabel?)  // author + attachment + server clock ("10:30 AM")
// id: server ObjectId (no '-') for synced notes; local UUID (has '-') for un-pushed optimistic ones.
// hasAttachment / isDocument are derived from imageUrl. timestamp for server notes is decoded
// from the ObjectId's embedded creation time (the API's "timestamp" is display-only, no date).

LEAD_STATUSES = ["Fresh Leads", "Interested Leads", "Pre Prospect Leads", "Prospect Leads", "Booked", "Rejected Leads"]
              // EXACT backend-accepted values; DEFAULT_LEAD_STATUS = "Fresh Leads". Anything else → 400 on PUT status.
CALL_OUTCOME_LABELS = ["Dialed", "Connected"]     // app-managed labels, replaced on each push
```

Dashboard (mostly computed on-device from call log; monthly target + daily attendance
come from the agent metrics API — see Agents API below):
```kotlin
DashboardData(daily: DashboardStats, monthly: MonthlyStats)
DashboardStats(date, totalDials, totalTalktime, connectedCalls, uniqueCalls,
               callMoreThan, firstCall?, lastCall?, idleTime, attendance)   // strings/ints
MonthlyStats(month, monthlyTarget, bookingCount, totalSaleAmount, attendance)  // all String
// daily.attendance   : admin-set "P"→"Present"(green) / "A"→"Absent"(red) from the metrics
//                      API; falls back to on-device call-activity ("Present"/"—") if absent.
// monthly.monthlyTarget: "<targetCompleted> - <monthlyTarget>" from the metrics API (e.g.
//                      "12 - 50"); "0 - 0" placeholder when the API has no value.
// totalSaleAmount "0 / 0" is still a placeholder — no backend field yet.
```

---

## Leads API (`remote/LeadsApi.kt`) — all require Authorization

| Method | Path | Body | Returns |
|---|---|---|---|
| GET | `api/leads` (via `@Url`, `ApiConfig.LEADS_ENDPOINT`) | — | `List<ApiLeadDto>` |
| POST | `api/leads` | `CreateLeadRequest` | `ApiLeadDto` |
| PUT | `api/leads/{id}/booking` | `UpdateBookingRequest` | `ApiLeadDto` |
| PUT | `api/leads/{id}/dates` | `UpdateDatesRequest` | `ApiLeadDto` |
| PUT | `api/leads/{id}/labels` | `UpdateLabelsRequest` | `ApiLeadDto` |
| PUT | `api/leads/{id}/status` | `UpdateStatusRequest` | `ApiLeadDto` |
| GET | `api/leads/{id}` | — | `ApiLeadDto` (incl. `notes`) |
| POST | `api/leads/{id}/notes` | `AddLeadNoteRequest(text, imageUrl?)` | `ApiLeadDto` |
| DELETE | `api/leads/{id}/notes/{noteId}` | — | `ApiLeadDto` |

> Backend scopes `GET /api/leads` to the caller's JWT — returns only that agent's leads
> (no client-side filtering). Lead creation is admin-scoped, so a created lead may not appear
> in this agent's list until assigned.

DTOs:
```kotlin
ApiLeadDto(id?, _id(mongoId)?, leadId: Long?, name?, phone?, labels: List<String>?,
           status?, booking: ApiBookingDto?, notes: List<ApiNoteDto>?, createdAt?, updatedAt?)   // stable id = id ?: _id ?: leadId ?: phone
ApiBookingDto(totalDial: Int?, dailyDial: Int?, connected: Int?, talkTime?, dailyTalkTime?, firstCall?, lastCall?)
ApiNoteDto(id?, _id(mongoId)?, text?, timestamp?, author?, authorId?, imageUrl?)  // timestamp = display clock string "10:30 AM"
AddLeadNoteRequest(text, imageUrl?)   // imageUrl omitted when null (Gson); attachment URL from POST /api/upload
CreateLeadRequest(name, phone, age: Int?, origin?, destination?, leadSource?, mailId?, product?)  // nulls omitted (Gson)
UpdateBookingRequest(totalDial: Int, dailyDial: Int, connected: Int, talkTime="M:SS", dailyTalkTime="M:SS", firstCall?="yyyy-MM-dd", lastCall?="yyyy-MM-dd")  // daily* = today only
UpdateDatesRequest(startDate?="yyyy-MM-dd", dueDate?="yyyy-MM-dd")
UpdateLabelsRequest(labels: List<String>)
UpdateStatusRequest(status: String)   // one of LEAD_STATUSES (backend-enforced); invalid → 400
```

---

## Auth API (`remote/AuthApi.kt`) — public + protected (FriskyTrails auth guide)

Public (no Authorization header):

| Path | Body | Returns |
|---|---|---|
| `api/auth/register` | `RegisterRequest(name,email,password,role="agent",inviteCode?)` | `RegisterResponse` |
| `api/auth/login` | `AuthLoginRequest(email,password)` | `AuthResponse` |
| `api/auth/verify-email` | `VerifyEmailRequest(email,otp)` | `StatusResponse` |
| `api/auth/resend-otp` | `ResendOtpRequest(email)` | `StatusResponse` |
| `api/auth/forgot-password` | `ForgotPasswordRequest(email)` | `StatusResponse` |
| `api/auth/reset-password` | `ResetPasswordRequest(email,otp,newPassword)` | `StatusResponse` |

Protected (`@Header("Authorization")` per-call, no interceptor — like `LeadsApi`):

| Method | Path | Body | Returns |
|---|---|---|---|
| GET | `api/auth/me` | — | `MeResponse` |
| PUT | `api/auth/me` | `UpdateProfileRequest(name,email)` | `UpdateProfileResponse` |

> The backend also exposes `PUT /api/auth/password`, but this app does **not** wire it — there
> is no change-password UI (deliberately removed). Don't re-add the endpoint without a screen.

```kotlin
RegisterRequest(name, email, password, role="agent", inviteCode: String? = null)  // app always registers agents; inviteCode omitted (Gson)
RegisterResponse(message?, emailFailed: Boolean = false, error?)   // register no longer returns a token
AuthResponse(token: String?, user: AuthUser?)
AuthUser(id?, name?, email?, isAdmin=false, isVerified=false, status?)  // status: "Pending"/"Active"/"Inactive"
MeResponse(id?, name?, email?, isAdmin=false, isVerified=false)
UpdateProfileResponse(message?, user: AuthUser?, error?)
StatusResponse(message: String?, error: String?)   // error populated on 4xx
```

Domain: `Profile(id, name, email, isAdmin, isVerified)` (`repository/Models.kt`) — repo returns
this from `getProfile`/`updateProfile`, not the DTO.

Auth quirks (`repository/Repositories.kt`, `AuthRepository`):
- Register is **agent-only** (`role="agent"`, no invite code). Returns `Result<Boolean>` where
  the boolean is `emailFailed` (account created but OTP email bounced → VM flags Resend).
- Register on an **unverified-existing** email → backend 400 "…pending…" → surfaced as
  `PendingVerificationException` (NOT an error; VM resumes OTP flow). A *verified* duplicate,
  or a **rejected/blocklisted** email, stays an ordinary error.
- Login enforces two gates beyond bad credentials: **email-not-verified** → typed
  `EmailNotVerifiedException` (VM resends OTP + routes to the OTP screen, then back to login);
  **pending admin approval** / **rejected by admin** / **invalid credentials** → ordinary
  `error` carrying the server's own message. Agents can't log in until an admin flips
  `status` Pending→Active.
- `login` saves token/name/email/agentId to `SessionManager`; `updateProfile` re-saves
  name/email on success so the dashboard greeting updates without re-login.
- There is **no "verify reset OTP" endpoint** — `verifyResetOtp` just checks non-blank locally;
  the OTP is really validated by `reset-password`.
- `forgot/reset-password` are rate-limited (~5 req / 15 min per IP) → 429 handled with a
  friendly message.
- 4xx error text is read from the server's `{ "error": "..." }` body (`errorBody().string()`
  is single-use — read once).

Auth ViewModels (`viewModel/ViewModels.kt`): `AuthViewModel` (shared across auth screens) gains
`needsEmailVerification`, `registrationEmailFailed` + `awaitingApproval` one-shot signals (+
clearers) and `refreshAgentInfo()`. New `ProfileViewModel` + `ProfileUiState` back the profile
screen (load/updateProfile only). `ProfileScreen` edits name/email — no change-password.

**Admin-approval waiting flow** (`ApprovalWaitingScreen.kt`, route `approvalWaiting`): after the
register OTP verify, nav goes to the waiting screen (NOT straight to login). The API has **no
status endpoint**, so approval is detected by silently retrying login: `AuthViewModel.checkApproval()`
re-calls `repo.login()` every 15s using an **in-memory-only** `pendingPassword` (set in
`register`/`login`, wiped on full login success + logout; never in UiState or on disk). State:
`isCheckingApproval` / `approvalGranted` / `approvalCheckError`. A "pending"/"approval"/429 login
error is swallowed (keep waiting); success flips `approvalGranted` + `isLoggedIn` (token saved by
repo) → celebratory message → auto-enters dashboard. Rejected/invalid-credentials → surfaced via
`approvalCheckError`. If the process is killed mid-wait the password is lost → user restarts at
login (no token was saved). `awaitingApproval`/the Login banner remain as a fallback path.

---

## Upload API (`remote/UploadApi.kt`) — requires Authorization

`POST api/upload` — `@Multipart`, single `@Part file: MultipartBody.Part` → `Response<UploadResponse>`
```kotlin
UploadResponse(message?, fileUrl?, fileData: UploadFileData?, error?)
UploadFileData(originalname?, mimetype?, path?, size: Long?)
```
No "attach file to lead" endpoint — `LeadsRepository.uploadDocument` uploads, then calls
`addNote(leadId, text=fileName, imageUrl=fileUrl)` so the file becomes a real note attachment
(the URL lives in the note's `imageUrl`, not the text). `NoteItem` renders it as a Coil image
thumbnail or a tappable doc card. Error bodies parsed via `parseUploadError`.

---

## Agents API (`remote/AgentsApi.kt`) — requires Authorization

| Method | Path | Returns |
|---|---|---|
| GET | `api/agents/{id}/metrics` | `AgentMetricsDto` |

```kotlin
AgentMetricsDto(monthlyTarget: Int?, targetCompleted: Int?, attendance: String?)
// attendance: "P" (Present) | "A" (Absent) | "" (unset). All fields nullable.
```
An agent may read only their **own** `:id`. `DashboardRepository.fetchAgentMetrics()` calls
this **best-effort** (`runCatching{…}.getOrNull()`) so a failure never breaks the dashboard —
it feeds `monthly.monthlyTarget` ("completed - target") and `daily.attendance` (P/A). The
`:id` is `SessionManager.getAgentId()` (saved at login), falling back to the `userId` claim
decoded from the JWT so pre-existing sessions work without re-login.

---

## Legacy `ApiService` (`remote/ApiService.kt`) — MOSTLY FAKE, beware
`AppModule` binds it to `FakeApiService()`. `LeadsRepository` now uses it only for `setDueDate`
(**fire-and-forget, errors swallowed** → due dates are effectively **local-only**, Room is source
of truth). **Notes no longer go through it** — they use the real `LeadsApi` (add/delete/get).
Its `login`/`getDashboard`/`getLeads`/`addNote` are unused (real flows go through
`AuthApi`/on-device/`LeadsApi`). `LeadDto`/`NoteDto` here are the legacy/fake DTOs — not the
real API shape (that's `ApiLeadDto`/`ApiNoteDto`).

---

## Room (`local/`)
```kotlin
LeadEntity  @Entity("leads")  PK id: String   // mirrors Lead's persisted fields
NoteEntity  @Entity("notes")  PK id: String, FK leadId → leads(id) ON DELETE CASCADE
            // + authorName?, authorId?, imageUrl?, timeLabel? (real note sync + attachments)
```
DAO highlights (`local/Daos.kt`): `getAllLeads(): Flow` (ORDER BY createdAt DESC),
`getNotesForLead(leadId): Flow` (ORDER BY timestamp DESC), `upsertLeads`, `updateDueDate`,
`updateStatus(id,status,changedAt)`, `deleteLeadsNotIn(keepIds)`, `deleteAll`.
Notes sync: `insertNotes`, `deleteNoteById`, `replaceServerNotes(leadId, notes)` (drops
server notes — ids without `-` — and keeps un-pushed local UUID notes).
DB is at **v9** and uses `fallbackToDestructiveMigration()` — schema changes wipe local data (re-syncs on launch).

## Error contract (repo → VM → UI)
Repositories return `Result<T>` (`runCatching { ... }.mapApiError()`). ViewModels use
`.onSuccess/.onFailure`, mapping failure → `error: String?` in UiState. Never surface raw
exceptions to Composables. Typed cases: `PendingVerificationException`.
