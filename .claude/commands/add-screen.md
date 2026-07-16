---
description: Scaffold a new Compose screen wired to a ViewModel, following this repo's exact conventions.
argument-hint: <ScreenName> [one-line purpose]
---

Add a new screen named **$1** to the Sales CRM app, matching the patterns already in this repo.
Purpose (if given): $2

Read `.claude/ARCHITECTURE.md` and `.claude/reference/api-and-models.md` first if not already
in context. Then follow the existing conventions **exactly** — do not invent new patterns.

## Steps (state the plan, then implement)
1. **UiState + ViewModel** in `app/src/main/java/com/crmapplication/viewModel/ViewModels.kt`
   (all VMs live in this one file — add to it, don't create a new file):
   - `data class ${1}UiState(val isLoading: Boolean = false, /* data, nullable */, val error: String? = null, /* one-shot success flags */)`
   - `@HiltViewModel class ${1}ViewModel @Inject constructor(private val repo: ...) : ViewModel()`
   - `private val _state = MutableStateFlow(${1}UiState()); val state = _state.asStateFlow()`
   - Do work in `viewModelScope.launch`, call a repository that returns `Result<T>`, use
     `.onSuccess/.onFailure`, map failures to `error` with a sensible default message.
   - Add `clearError()` / `clearXSuccess()` for any one-shot signals.
2. **Screen** in `app/src/main/java/com/crmapplication/ui/screens/${1}Screen.kt`:
   - `@Composable fun ${1}Screen(viewModel: ${1}ViewModel = hiltViewModel(), onBack: () -> Unit, /* other nav lambdas */)`
   - `val state by viewModel.state.collectAsState()`. Stateless children; hoist state, pass lambdas.
   - No business logic in the Composable. No `LaunchedEffect` inside scoped content lambdas.
   - User-facing text via `stringResource` — no hardcoded strings.
   - Reuse Composables from `ui/component/Components.kt` where they fit.
3. **Route** in `app/src/main/java/com/crmapplication/ui/NavGraph.kt`:
   - Add `const val ${1_UPPER} = "..."` to `object Routes` (+ a builder fn if it takes args).
   - Add a `composable(Routes.${1_UPPER}) { ${1}Screen(...) }` block; wire nav lambdas
     (`navController.navigate(...)` / `popBackStack()`), following the existing blocks.
4. **Repository/API** only if new data is needed: add the method to the correct `remote/`
   interface + DTO, call it from the matching repository returning `Result<T>`. All three
   real APIs share one Retrofit — no new instance. Watch the `com.salescrm.BuildConfig` trap.
5. **Verify**: `JAVA_HOME=D:/android/jbr ./gradlew :app:compileDebugKotlin -q`, then
   `assembleDebug` if it compiles. Report real output; if it fails, fix before finishing.

## Guardrails
- Match the single-`data class` UiState shape — do NOT introduce sealed Loading/Success/Error.
- If this touches more than the four files above (e.g. new API + Room field), state the full
  plan and wait for confirmation before editing (per CLAUDE.md).
