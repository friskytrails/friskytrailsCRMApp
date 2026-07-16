# Project Memory — Sales CRM (Android / Kotlin)

Auto-loaded by Claude Code each session. Keep it short and factual — these are "rules,"
not documentation. Modeled on the generic Android/Kotlin template, corrected to match what
this repo actually does.

## What this app is
An Android CRM for sales agents: authenticate, view/filter/search assigned leads, add leads,
open a lead's detail, add notes, upload documents, and derive booking/call metrics from the
device call log. Backend is a remote REST API.

## Project docs — read before exploring (saves tokens, avoids guessing)
- `.claude/ARCHITECTURE.md` — where everything lives, screen→VM→repo map, nav routes, traps.
  Read this instead of Glob-ing the tree.
- `.claude/reference/api-and-models.md` — exact field names, endpoints, DTOs, error contract.
  Check here before writing anything that names a model field or endpoint. Do NOT guess these.
- For "where is X / everywhere Y happens" questions, delegate to the **crm-explorer** subagent
  rather than reading files into this context.
- To scaffold a new screen, use the **/add-screen** command.

## Stack (actual)
- Kotlin 2.0.21, Jetpack Compose (Material 3, no XML layouts)
- Pattern: MVVM. One `data class *UiState` + one `*ViewModel` per screen. Not strict Clean
  Architecture — there is no separate `domain/` layer; repositories are the boundary.
- DI: Hilt (`@HiltViewModel`, `SingletonComponent` modules in `di/`)
- Async: Coroutines + `StateFlow` (`MutableStateFlow` + `.asStateFlow()`). No LiveData / RxJava.
- Networking: Retrofit 2.11 + OkHttp 4.12 + **Gson** (not Moshi). One shared Retrofit for
  leads/auth/upload, base URL from `ApiConfig`. OkHttp `BODY` logging on debug only.
- Local storage: Room 2.6.1 (`CrmDatabase`, offline-first) + DataStore for prefs
- Background: WorkManager (+ `hilt-work`)
- Navigation: Jetpack Navigation Compose (`ui/NavGraph.kt`)
- Images: Coil
- Build: Gradle Kotlin DSL + version catalog (`gradle/libs.versions.toml`). **KSP**, not kapt.
- SDK: compileSdk/targetSdk 35, minSdk 26, Java 17

## Project quirks — read before assuming
- **Package vs applicationId differ.** Source package is `com.crmapplication`; the
  `namespace`/`applicationId` is `com.salescrm`. So `BuildConfig` is `com.salescrm.BuildConfig`
  — import it from there, not `com.crmapplication`.
- **Data layer lives under `LeadDetailVM/`** (historical name, not just lead-detail):
  `LeadDetailVM/remote` (Retrofit APIs, DTOs), `LeadDetailVM/local` (Room), `LeadDetailVM/repository`
  (repositories + domain models like `Lead`, `Note`).
- **All ViewModels are in one file:** `viewModel/ViewModels.kt`. UI state classes live beside them.
- UI: `ui/screens`, `ui/component`, `ui/theme`, `ui/NavGraph.kt`. Misc: `utils/`, `calllog/`, `di/`.
- Secrets: `LEADS_AUTH_TOKEN` is read from `local.properties` / env in `app/build.gradle.kts`
  and exposed via `BuildConfig`. Never hardcode or commit it.

## Conventions (how this repo actually does it)
- **UI state is a single `data class`**, not a sealed Loading/Success/Error interface.
  Shape: `isLoading`, the data (nullable), `error: String?`, plus one-shot boolean signals
  (e.g. `createSuccess`, `verifySuccess`) that the UI consumes then clears via `clearX()`.
- **Errors flow as `Result<T>`.** Repositories return `Result`; ViewModels use
  `.onSuccess/.onFailure` and map failures to `error: String?` in state (with a sensible default
  message). Domain-specific cases use typed exceptions (e.g. `PendingVerificationException`).
  Don't surface raw exceptions to Composables.
- One ViewModel per screen exposes `val state: StateFlow<UiState>`. No business logic in Composables.
- Composables stateless where possible — hoist state, pass lambdas down.
- Repositories return domain models (`Lead`, `Note`), not DTOs — map at the data-layer boundary.
- Offline-first for leads/notes: write to Room first (UI updates via `observeLeads`/`observeNotes`
  Flow), then push to the server in the same call. A failed push surfaces via `error` but the
  local change stays until the next sync reconciles.
- No hardcoded user-facing strings in Composables — use `stringResource`.

## What NOT to do
- Don't add third-party libraries without asking first. Add via the version catalog
  (`libs.versions.toml`), then reference `libs.*` — don't inline `"group:artifact:version"`.
- Don't convert the single-`data class` UiState to sealed interfaces, or scatter ViewModels
  into per-screen files, unless explicitly asked — match the existing shape.
- Don't put `LaunchedEffect`/side-effect APIs inside scoped content lambdas — hoist them out.
- Don't add ViewModel logic directly in Composables "just to make it work."
- Don't import `BuildConfig` from `com.crmapplication` (see quirk above).

## Before you write code
For anything touching more than one file or one layer (UI ↔ ViewModel ↔ repository ↔ remote/local),
state your plan (files, approach, assumptions) and wait for confirmation before editing.

## Build & test
- **JDK:** `java` is not on PATH. Set `JAVA_HOME=D:/android/jbr` before `./gradlew`.
- Build: `./gradlew assembleDebug` (Windows: `gradlew.bat`).
- **Testing is minimal today:** only JUnit 4.13.2 is on the classpath — **no MockK, no Turbine,
  no Compose UI test**. Don't assume those exist. If a change needs them, add them to the version
  catalog first (and ask, per the rule above). After changing a ViewModel or Repository, run the
  relevant test if one exists; don't claim tests pass without running them.
