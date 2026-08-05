package com.crmapplication.viewModel

import android.net.Uri
import com.crmapplication.LeadDetailVM.repository.DashboardRepository
import com.crmapplication.LeadDetailVM.repository.DashboardData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crmapplication.LeadDetailVM.repository.AuthRepository
import com.crmapplication.LeadDetailVM.repository.BOOKED_STATUS
import com.crmapplication.LeadDetailVM.repository.BookingForm
import com.crmapplication.LeadDetailVM.repository.BugReport
import com.crmapplication.LeadDetailVM.repository.BugReportRepository
import com.crmapplication.LeadDetailVM.repository.CallLogSyncRepository
import com.crmapplication.LeadDetailVM.repository.ConfigRepository
import com.crmapplication.LeadDetailVM.repository.DEFAULT_LEAD_STATUSES
import com.crmapplication.LeadDetailVM.repository.DEFAULT_PRODUCTS
import com.crmapplication.LeadDetailVM.repository.EmailNotVerifiedException
import com.crmapplication.LeadDetailVM.repository.Lead
import com.crmapplication.LeadDetailVM.repository.LeadsRepository
import com.crmapplication.LeadDetailVM.repository.Note
import com.crmapplication.LeadDetailVM.repository.PendingVerificationException
import com.crmapplication.LeadDetailVM.repository.Profile
import com.crmapplication.LeadDetailVM.repository.StatusChange
import com.crmapplication.LeadDetailVM.repository.bookingFromCalls
import com.crmapplication.LeadDetailVM.repository.isBooked
import com.crmapplication.LeadDetailVM.repository.callLabelFor
import com.crmapplication.LeadDetailVM.repository.mergeLabels
import com.crmapplication.LeadDetailVM.remote.CreateLeadRequest
import com.crmapplication.calllog.CallLogEntry
import com.crmapplication.calllog.CallLogReader
import com.crmapplication.notification.ReminderScheduler
import com.crmapplication.utils.ThemeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val agentName: String = "",
    val agentEmail: String = "",
    val error: String? = null,

    val pendingEmail: String = "",

    val registerSuccess: Boolean = false,
    val verifySuccess: Boolean = false,
    val otpResent: Boolean = false,
    val forgotOtpSent: Boolean = false,
    val forgotVerified: Boolean = false,
    val resetOtp: String = "",
    val resetSuccess: Boolean = false,

    val needsEmailVerification: Boolean = false,

    val registrationEmailFailed: Boolean = false,

    val awaitingApproval: Boolean = false,

    val isCheckingApproval: Boolean = false,

    val approvalGranted: Boolean = false,

    val approvalCheckError: String? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repo: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(
        AuthUiState(
            isLoggedIn = repo.isLoggedIn(),
            agentName = repo.getAgentName(),
            agentEmail = repo.getAgentEmail(),
        )
    )
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    private var pendingPassword: String? = null

    fun register(name: String, email: String, password: String) {

        pendingPassword = password
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            repo.register(name, email, password)
                .onSuccess { emailFailed ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            registerSuccess = true,
                            pendingEmail = email.trim(),

                            registrationEmailFailed = emailFailed,
                        )
                    }
                }
                .onFailure { e ->
                    if (e is PendingVerificationException) {

                        resumePendingVerification(email.trim())
                    } else {
                        _state.update { it.copy(isLoading = false, error = e.message ?: "Registration failed") }
                    }
                }
        }
    }

    private suspend fun resumePendingVerification(email: String) {
        repo.resendOtp(email)
            .onSuccess {
                _state.update {
                    it.copy(isLoading = false, registerSuccess = true, pendingEmail = email, otpResent = true)
                }
            }
            .onFailure {
                _state.update {
                    it.copy(isLoading = false, registerSuccess = true, pendingEmail = email)
                }
            }
    }

    fun verifyEmail(otp: String) {
        val email = _state.value.pendingEmail
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            repo.verifyEmail(email, otp)
                .onSuccess {

                    _state.update { it.copy(isLoading = false, verifySuccess = true, awaitingApproval = true) }
                }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message ?: "Verification failed") } }
        }
    }

    fun resendOtp() {
        val email = _state.value.pendingEmail
        if (email.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            repo.resendOtp(email)
                .onSuccess { _state.update { it.copy(isLoading = false, otpResent = true) } }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message ?: "Could not resend OTP") } }
        }
    }

    fun login(email: String, password: String) {

        pendingPassword = password
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            repo.login(email, password)
                .onSuccess { name ->
                    pendingPassword = null
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isLoggedIn = true,
                            agentName = name,
                            agentEmail = repo.getAgentEmail(),
                        )
                    }
                }
                .onFailure { e ->
                    if (e is EmailNotVerifiedException) {

                        resumeEmailVerification(email.trim())
                    } else {

                        _state.update { it.copy(isLoading = false, error = e.message ?: "Login failed") }
                    }
                }
        }
    }

    private suspend fun resumeEmailVerification(email: String) {
        repo.resendOtp(email)
            .onSuccess {
                _state.update {
                    it.copy(isLoading = false, needsEmailVerification = true, pendingEmail = email, otpResent = true)
                }
            }
            .onFailure {
                _state.update {
                    it.copy(isLoading = false, needsEmailVerification = true, pendingEmail = email)
                }
            }
    }

    fun checkApproval() {
        val email = _state.value.pendingEmail
        val password = pendingPassword
        if (password == null || email.isBlank()) {
            _state.update {
                it.copy(approvalCheckError = "Your session expired. Please sign in again to check approval.")
            }
            return
        }
        if (_state.value.isCheckingApproval) return

        viewModelScope.launch {
            _state.update { it.copy(isCheckingApproval = true, approvalCheckError = null) }
            repo.login(email, password)
                .onSuccess { name ->
                    pendingPassword = null
                    _state.update {
                        it.copy(
                            isCheckingApproval = false,
                            approvalGranted = true,
                            isLoggedIn = true,
                            agentName = name,
                            agentEmail = repo.getAgentEmail(),
                        )
                    }
                }
                .onFailure { e ->
                    val msg = e.message.orEmpty()
                    val stillWaiting = e is EmailNotVerifiedException ||
                        msg.contains("pending", ignoreCase = true) ||
                        msg.contains("approval", ignoreCase = true) ||
                        msg.contains("too many", ignoreCase = true)
                    _state.update {
                        if (stillWaiting) {

                            it.copy(isCheckingApproval = false, approvalCheckError = null)
                        } else {

                            it.copy(isCheckingApproval = false, approvalCheckError = msg.ifBlank { "Could not verify approval" })
                        }
                    }
                }
        }
    }

    fun logout() {
        repo.logout()
        pendingPassword = null
        _state.update { it.copy(isLoggedIn = false, agentName = "", agentEmail = "") }
    }

    fun refreshAgentInfo() {
        _state.update { it.copy(agentName = repo.getAgentName(), agentEmail = repo.getAgentEmail()) }
    }

    fun requestPasswordReset(email: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            repo.requestPasswordReset(email)
                .onSuccess {
                    _state.update { it.copy(isLoading = false, forgotOtpSent = true, pendingEmail = email.trim()) }
                }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message ?: "Could not send OTP") } }
        }
    }

    fun verifyResetOtp(otp: String) {
        val email = _state.value.pendingEmail
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            repo.verifyResetOtp(email, otp)
                .onSuccess { _state.update { it.copy(isLoading = false, forgotVerified = true, resetOtp = otp.trim()) } }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message ?: "Invalid OTP") } }
        }
    }

    fun resendResetOtp() {
        val email = _state.value.pendingEmail
        if (email.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            repo.requestPasswordReset(email)
                .onSuccess { _state.update { it.copy(isLoading = false, otpResent = true) } }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message ?: "Could not resend OTP") } }
        }
    }

    fun resetPassword(otp: String, newPassword: String) {
        val email = _state.value.pendingEmail
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            repo.resetPassword(email, otp, newPassword)
                .onSuccess { _state.update { it.copy(isLoading = false, resetSuccess = true) } }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message ?: "Could not reset password") } }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun clearTransient() = _state.update { it.copy(error = null, otpResent = false) }
    fun clearRegisterSuccess() = _state.update { it.copy(registerSuccess = false) }
    fun clearVerifySuccess() = _state.update { it.copy(verifySuccess = false) }
    fun clearOtpResent() = _state.update { it.copy(otpResent = false) }
    fun clearForgotOtpSent() = _state.update { it.copy(forgotOtpSent = false) }
    fun clearForgotVerified() = _state.update { it.copy(forgotVerified = false) }
    fun clearResetSuccess() = _state.update { it.copy(resetSuccess = false) }
    fun clearNeedsEmailVerification() = _state.update { it.copy(needsEmailVerification = false) }
    fun clearRegistrationEmailFailed() = _state.update { it.copy(registrationEmailFailed = false) }
    fun clearAwaitingApproval() = _state.update { it.copy(awaitingApproval = false) }
    fun clearApprovalCheckError() = _state.update { it.copy(approvalCheckError = null) }
}

/**
 * Read-only. Name and email are set at registration and owned by the backend, so Profile displays
 * them rather than editing them — hence no isSaving / profileUpdated signals here.
 */
data class ProfileUiState(
    val isLoading: Boolean = true,
    val profile: Profile? = null,
    val name: String = "",
    val email: String = "",
    val error: String? = null,

    val darkMode: Boolean? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repo: AuthRepository,
    private val themeManager: ThemeManager,
) : ViewModel() {

    private val _state = MutableStateFlow(

        ProfileUiState(name = repo.getAgentName(), email = repo.getAgentEmail())
    )
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        load()

        viewModelScope.launch {
            themeManager.darkMode.collect { pref ->
                _state.update { it.copy(darkMode = pref) }
            }
        }
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch { themeManager.setDarkMode(enabled) }
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            repo.getProfile()
                .onSuccess { profile ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            profile = profile,
                            name = profile.name.ifBlank { it.name },
                            email = profile.email.ifBlank { it.email },
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}

data class DashboardUiState(
    val isLoading: Boolean = false,
    val data: DashboardData? = null,
    val needsPermission: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
@OptIn(FlowPreview::class)
class DashboardViewModel @Inject constructor(
    private val repo: DashboardRepository,
    private val leadsRepo: LeadsRepository,
    private val callLogSyncRepo: CallLogSyncRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState(isLoading = true))
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    init {
        // Paint the last computed dashboard instantly if this VM was just recreated (nav re-entry).
        repo.lastData?.let { cached -> _state.update { it.copy(isLoading = false, data = cached) } }
        load()

        viewModelScope.launch {
            repo.observeCallLogChanges()
                .debounce(500)
                .collect {
                    logNewCalls()
                    load(triggerSync = false)
                }
        }
    }

    private fun logNewCalls() {
        viewModelScope.launch { runCatching { callLogSyncRepo.syncNewCalls() } }
    }

    /**
     * Loads the dashboard without blocking first paint on the network. Seeds from the cached result,
     * then collects the two-stage flow (local-first, then network-refined). Lead sync runs detached;
     * when it lands we recompute once ([triggerSync] = false) so booking counts refresh — never
     * re-triggering sync, which would loop. [isLoading] drives the thin top-bar progress bar; the
     * full-screen loader only shows when there is genuinely no data yet.
     */
    fun load(triggerSync: Boolean = true) {
        if (!repo.hasCallLogPermission()) {
            _state.update { it.copy(isLoading = false, needsPermission = true) }
            return
        }

        _state.update {
            it.copy(data = it.data ?: repo.lastData, needsPermission = false, error = null, isLoading = true)
        }

        viewModelScope.launch {
            runCatching {
                repo.getDashboard().collect { data ->
                    _state.update { it.copy(data = data) }
                }
            }.onFailure { e -> _state.update { it.copy(error = e.message) } }
            _state.update { it.copy(isLoading = false) }
        }

        if (triggerSync) {
            viewModelScope.launch {
                leadsRepo.syncLeads()
                load(triggerSync = false)
            }
        }

        logNewCalls()
    }

    fun refresh() = load()

    fun onPermissionResult(granted: Boolean) {
        if (granted) load()
        else _state.update { it.copy(isLoading = false, needsPermission = true) }
    }
}

enum class SortOrder { BY_DUE_DATE, BY_AGE, BY_NAME }

/**
 * One filter chip on the leads list.
 *
 * A data class built at runtime, not an enum: the status list is server-owned (`GET /api/config`),
 * so a status added on the backend has to be able to become a chip without an app release. Equality
 * is by value, so a config refresh that returns the same statuses leaves the active filter selected.
 *
 * [statusMatch] null means "no status filter" — the [All] chip.
 */
data class LeadFilter(val label: String, val statusMatch: String?) {
    companion object {
        /** Always first, always present, whatever the server sends. */
        val All = LeadFilter(label = "All", statusMatch = null)

        fun forStatus(status: String): LeadFilter =
            LeadFilter(label = shortStatusLabel(status), statusMatch = status.trim())
    }
}

/**
 * Chip label for a status: the trailing "Leads" is dropped ("Fresh Leads" → "Fresh") because the
 * chips already sit on the leads screen, and the row scrolls horizontally — the repeated suffix
 * would cost room that the status names themselves need.
 */
fun shortStatusLabel(status: String): String {
    val trimmed = status.trim()
    val suffix = " leads"
    if (trimmed.length <= suffix.length || !trimmed.lowercase().endsWith(suffix)) return trimmed
    return trimmed.dropLast(suffix.length).trim().ifEmpty { trimmed }
}

/**
 * Terminal statuses. Work on the lead is finished either way, so it drops out of the default
 * "All" list instead of padding it forever. Still reachable by tapping its own chip, or by
 * searching name/number — see [LeadsUiState.visibleLeads].
 *
 * Matched by name rather than read from config: the endpoint returns bare strings with no
 * "is terminal" metadata, so there is nothing to derive this from. [BOOKED_STATUS] is load-bearing
 * beyond this list (it gates the booking form and the status lock), so it stays a constant.
 */
private val CLOSED_STATUS_NAMES: List<String> = listOf(BOOKED_STATUS, "Rejected Leads")

private fun isClosedStatus(status: String): Boolean =
    CLOSED_STATUS_NAMES.any { it.equals(status.trim(), ignoreCase = true) }

data class LeadsUiState(
    val isLoading: Boolean = false,
    val leads: List<Lead> = emptyList(),
    val sortOrder: SortOrder = SortOrder.BY_DUE_DATE,

    val activeFilter: LeadFilter? = null,

    val activeProduct: String? = null,
    val searchQuery: String = "",

    /**
     * Product catalog for the Add Lead dropdown, driven by `GET /api/config`. Server-owned, so a
     * product added on the backend appears here without an app release. Distinct from
     * [availableProducts], which is derived from the leads on hand and drives the list filter.
     */
    val products: List<String> = DEFAULT_PRODUCTS,

    /**
     * Lead statuses, driven by `GET /api/config` alongside [products]. Server-owned: a status added
     * on the backend becomes a filter chip here and an option in every status dropdown, with no app
     * release. Held in the server's order, which is the pipeline's progression.
     */
    val statuses: List<String> = DEFAULT_LEAD_STATUSES,

    val isCreating: Boolean = false,
    val createSuccess: Boolean = false,
    val error: String? = null,

    /** True while an edit to name / travel date / party size is in flight. */
    val isSavingLeadInfo: Boolean = false,

    /** One-shot: a lead-info edit was saved. UI shows the confirmation, then calls `clearLeadInfoSaved()`. */
    val leadInfoSaved: Boolean = false,

    /**
     * The lead whose Booking Details form is open, or null when it's closed. Holding the [Lead] (not
     * just its id) is what lets the form pre-fill name and phone, and it keeps the dialog rendering
     * the lead it was opened for even if a background sync reorders the list.
     *
     * The form's own field values live in the dialog Composable, matching how Add Lead does it — only
     * the "which lead / in flight / done" signals belong in state.
     */
    val bookingFor: Lead? = null,

    val isBooking: Boolean = false,

    /** One-shot: the booking reached the server. UI shows the confirmation, then calls `clearBookingSuccess()`. */
    val bookingSuccess: Boolean = false,

    // True once the first network sync has completed. Distinguishes "still loading, never synced"
    // (show progress, not the empty state) from "synced and genuinely empty" (show "No leads yet").
    val hasSynced: Boolean = false,
) {

    val visibleLeads: List<Lead>
        get() = leads
            .filter { lead ->
                // Closed leads stay out of the default "All" list so it only holds work still open.
                // They come back when explicitly asked for: their own chip, or a name/number search,
                // which should find a lead regardless of how it ended.
                if (!isClosedStatus(lead.status)) return@filter true
                val onClosedChip = activeFilter?.statusMatch?.let(::isClosedStatus) == true
                onClosedChip || searchQuery.isNotBlank()
            }
            .filter { lead ->

                when (val match = activeFilter?.statusMatch) {
                    null -> true
                    else -> lead.status.equals(match, ignoreCase = true)
                }
            }
            .filter { lead ->

                when (val product = activeProduct) {
                    null -> true
                    else -> lead.product.equals(product, ignoreCase = true)
                }
            }
            .filter { lead ->
                val q = searchQuery.trim()
                if (q.isBlank()) return@filter true
                val nameMatch = lead.name.contains(q, ignoreCase = true)
                val digits = q.filter(Char::isDigit)
                val phoneMatch = digits.isNotEmpty() &&
                    lead.phone.filter(Char::isDigit).contains(digits)
                nameMatch || phoneMatch
            }

    // "All" counts only what "All" actually shows — open leads. Counting closed ones here would
    // print a number the list can never reach.
    fun countFor(filter: LeadFilter): Int =
        filter.statusMatch?.let { s -> leads.count { it.status.equals(s, ignoreCase = true) } }
            ?: leads.count { !isClosedStatus(it.status) }

    /**
     * Options for a status dropdown: the server list, with [BOOKED_STATUS] guaranteed present.
     *
     * Picking "Booked" is the only route to the booking form (see [LeadsViewModel.updateStatus]), so
     * a config document that omitted the name would leave agents unable to book a lead at all.
     * Appended rather than inserted, since its position in a server-sent list is the admin's choice.
     */
    val statusOptions: List<String>
        get() = if (statuses.any { it.equals(BOOKED_STATUS, ignoreCase = true) }) statuses
        else statuses + BOOKED_STATUS

    /**
     * The filter chips: "All" then one per server status, in the server's order.
     *
     * An active filter whose status has since left the config is kept on the end rather than
     * dropped. Dropping it would hide the chip while [visibleLeads] still filtered by it — an
     * invisible filter the agent has no way to clear.
     */
    val filters: List<LeadFilter>
        get() {
            val fromConfig = statuses.map(LeadFilter::forStatus)
            val active = activeFilter
            val stale = active != null && active.statusMatch != null &&
                fromConfig.none { it.statusMatch.equals(active.statusMatch, ignoreCase = true) }
            return listOf(LeadFilter.All) + fromConfig + listOfNotNull(active.takeIf { stale })
        }

    val availableProducts: List<String>
        get() = leads.mapNotNull { it.product?.takeIf { p -> p.isNotBlank() } }
            .distinct()
            .sorted()
}

@HiltViewModel
class LeadsViewModel @Inject constructor(
    private val repo: LeadsRepository,
    private val configRepo: ConfigRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LeadsUiState(isLoading = true))
    val state: StateFlow<LeadsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.observeLeads().collect { leads ->
                _state.update { it.copy(isLoading = false, leads = sortedLeads(leads, it.sortOrder)) }
            }
        }
        // Separate collectors: config is server-owned and refreshes on its own schedule, so a fetch
        // pushes new products into state while the Add Lead screen is open, and new statuses into
        // the chips and status dropdowns while the leads list is open.
        viewModelScope.launch {
            configRepo.observeProducts().collect { products ->
                _state.update { it.copy(products = products) }
            }
        }
        viewModelScope.launch {
            configRepo.observeStatuses().collect { statuses ->
                _state.update { it.copy(statuses = statuses) }
            }
        }
        sync()
    }

    /**
     * Pulls the global config (statuses + products). Safe to call on every screen entry — the
     * repository throttles back-to-back requests, and a failure is intentionally swallowed rather
     * than written to [LeadsUiState.error], which drives the Add Lead snackbar: the cached lists
     * still work, so a stale dropdown shouldn't raise an error the user can do nothing about.
     */
    fun refreshConfig(force: Boolean = false) {
        viewModelScope.launch { configRepo.syncConfig(force = force) }
    }

    /**
     * [force] = true for the manual refresh button (bypass the repository sync throttles).
     *
     * Config rides along so statuses stay current wherever leads are already being refreshed: this
     * ViewModel's init and the list's Sync button. The lead detail screen polls its own ViewModel,
     * so it calls [refreshConfig] directly. Leads and config are throttled independently, so
     * pairing them doesn't add real traffic.
     */
    fun sync(force: Boolean = false) {
        refreshConfig(force = force)
        viewModelScope.launch {
            repo.syncLeads(force = force)
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
            // Mark synced regardless of outcome: a failed sync still means we've tried, so the empty
            // state ("No leads yet") is now truthful rather than a premature flash during first load.
            _state.update { it.copy(hasSynced = true) }
        }
    }

    fun setSortOrder(order: SortOrder) {
        _state.update { it.copy(sortOrder = order, leads = sortedLeads(it.leads, order)) }
    }

    fun toggleFilter(filter: LeadFilter) {
        _state.update { it.copy(activeFilter = if (it.activeFilter == filter) null else filter) }
    }

    fun setProductFilter(product: String?) {
        _state.update { it.copy(activeProduct = product) }
    }

    fun setSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun createLead(request: CreateLeadRequest) {
        viewModelScope.launch {
            _state.update { it.copy(isCreating = true, error = null) }
            repo.createLead(request)
                .onSuccess { _state.update { it.copy(isCreating = false, createSuccess = true) } }
                .onFailure { e -> _state.update { it.copy(isCreating = false, error = e.message ?: "Could not create lead") } }
        }
    }

    fun clearCreateSuccess() = _state.update { it.copy(createSuccess = false) }

    /**
     * Applies a status picked from the dropdown — with two cases that don't reach the network:
     *
     * - **Target is `Booked`** → opens the booking form instead of writing. `Booked` is only reachable
     *   through `PUT api/leads/{id}/book`, which needs booking details, so a bare status write here
     *   would produce a booked lead with an empty booking.
     * - **Lead is already booked** → ignored. The dropdown is disabled in both screens that show it,
     *   so this is the backstop rather than the main guard.
     */
    fun updateStatus(leadId: String, status: String) {
        val lead = _state.value.leads.find { it.id == leadId }

        if (lead != null && lead.isBooked()) return

        if (status.equals(BOOKED_STATUS, ignoreCase = true)) {
            // No lead in state means the list hasn't loaded it; opening a form with nothing to
            // pre-fill (and no id to submit) would be worse than doing nothing.
            lead?.let { startBooking(it) }
            return
        }

        viewModelScope.launch {
            repo.updateStatus(leadId, status).onFailure { e ->
                _state.update { it.copy(error = e.message ?: "Could not update status") }
            }
        }
    }

    /**
     * Saves an edit to one of the agent-editable lead fields (`PUT api/leads/{id}`).
     *
     * Pass only the field being edited; the others stay null and are left untouched server-side. To
     * clear a value pass `""` for [travelDate] or `0` for [numberOfPersons] — see
     * [LeadsRepository.updateLeadInfo].
     *
     * Lives here rather than on `LeadDetailViewModel` because the detail screen reads its `lead` from
     * this ViewModel's state, same as `updateStatus`.
     */
    fun updateLeadInfo(
        leadId: String,
        name: String? = null,
        travelDate: String? = null,
        numberOfPersons: Int? = null,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(isSavingLeadInfo = true, error = null) }
            repo.updateLeadInfo(
                leadId = leadId,
                name = name,
                travelDate = travelDate,
                numberOfPersons = numberOfPersons,
            )
                .onSuccess {
                    _state.update { it.copy(isSavingLeadInfo = false, leadInfoSaved = true) }
                }
                .onFailure { e ->
                    // The local write already landed, so the edit is visible — this reports that the
                    // server hasn't got it yet, which the next successful sync will reconcile.
                    _state.update {
                        it.copy(
                            isSavingLeadInfo = false,
                            error = e.message ?: "Saved on this device, but couldn't reach the server",
                        )
                    }
                }
        }
    }

    fun clearLeadInfoSaved() = _state.update { it.copy(leadInfoSaved = false) }

    /** Opens the Booking Details form for [lead]. No-op if it's already booked. */
    fun startBooking(lead: Lead) {
        if (lead.isBooked()) return
        _state.update { it.copy(bookingFor = lead, error = null) }
    }

    /** Dismisses the form without booking. Ignored mid-submit so a stray tap can't orphan the request. */
    fun cancelBooking() {
        if (_state.value.isBooking) return
        _state.update { it.copy(bookingFor = null) }
    }

    /**
     * Sends the completed form. On success the lead is `Booked` and its status locks; on failure the
     * form stays open with the server's message so the agent can fix and retry — closing it would
     * discard everything they typed.
     */
    fun submitBooking(form: BookingForm) {
        val leadId = _state.value.bookingFor?.id ?: return
        if (_state.value.isBooking) return

        viewModelScope.launch {
            _state.update { it.copy(isBooking = true, error = null) }
            repo.bookLead(leadId, form)
                .onSuccess {
                    _state.update {
                        it.copy(isBooking = false, bookingFor = null, bookingSuccess = true)
                    }
                    // The backend rewrites the lead's name/product from the form and recalculates the
                    // agent's booking count, so pull the authoritative row rather than guessing at it.
                    sync(force = true)
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(isBooking = false, error = e.message ?: "Could not book this lead")
                    }
                }
        }
    }

    fun clearBookingSuccess() = _state.update { it.copy(bookingSuccess = false) }

    fun clearError() = _state.update { it.copy(error = null) }

    private fun sortedLeads(leads: List<Lead>, order: SortOrder) = when (order) {
        SortOrder.BY_DUE_DATE -> leads.sortedWith(compareBy(nullsLast()) { it.dueDate })
        SortOrder.BY_AGE      -> leads.sortedByDescending { it.createdAt }
        SortOrder.BY_NAME     -> leads.sortedBy { it.name }
    }
}

data class LeadDetailUiState(
    val lead: Lead? = null,
    val isLoading: Boolean = false,
    val notes: List<Note> = emptyList(),
    val noteSaveSuccess: Boolean = false,
    val isSavingNote: Boolean = false,
    val error: String? = null,

    val isUploading: Boolean = false,
    val uploadSuccess: String? = null,
    val callHistory: CallHistoryState = CallHistoryState(),

    val hasCallLogMatch: Boolean = false,

    val matchedCalls: List<CallLogEntry> = emptyList(),

    val statusHistory: List<StatusChange> = emptyList(),

    val myAgentId: String? = null,
)

data class CallHistoryState(
    val number: String? = null,
    val isLoading: Boolean = false,
    val needsPermission: Boolean = false,
    val calls: List<CallLogEntry> = emptyList(),
) {
    val isOpen: Boolean get() = number != null
}

@HiltViewModel
@OptIn(FlowPreview::class)
class LeadDetailViewModel @Inject constructor(
    private val repo: LeadsRepository,
    private val callLogReader: CallLogReader,
    private val reminderScheduler: ReminderScheduler,
) : ViewModel() {

    private val _state = MutableStateFlow(LeadDetailUiState())
    val state: StateFlow<LeadDetailUiState> = _state.asStateFlow()

    init {

        viewModelScope.launch {
            callLogReader.observeChanges()
                .debounce(500)
                .collect { _state.value.lead?.phone?.let { refreshCallLogMatch(it) } }
        }
    }

    fun loadLead(lead: Lead) {
        _state.update { it.copy(lead = lead, myAgentId = repo.currentAgentId()) }
        viewModelScope.launch {
            repo.observeNotes(lead.id).collect { notes ->
                _state.update { it.copy(notes = notes) }
            }
        }
        viewModelScope.launch {
            repo.observeStatusHistory(lead.id).collect { history ->
                _state.update { it.copy(statusHistory = history) }
            }
        }

        viewModelScope.launch { repo.refreshLeadNotes(lead.id) }
        refreshCallLogMatch(lead.phone)
    }

    /**
     * [showLoading] = false for the background poll, so a tick doesn't flash the loading state.
     * Failures stay silent here on purpose — a poll on a weak connection shouldn't raise a
     * snackbar every interval. Errors the agent triggered (send note, upload) still surface.
     */
    fun refresh(showLoading: Boolean = true) {
        val lead = _state.value.lead
        viewModelScope.launch {
            if (showLoading) _state.update { it.copy(isLoading = true) }
            // force = true: the repository skips an unforced sync within 30s, which made resuming
            // this screen inside that window a no-op and left backend-side changes invisible.
            repo.syncLeads(force = true)
            // syncLeads only reconciles notes when the leads payload embeds them, so ask for this
            // lead's notes directly — otherwise a note added from the web shows up only after
            // navigating away and back.
            lead?.let { repo.refreshLeadNotes(it.id) }
            lead?.phone?.let { refreshCallLogMatch(it) }
            if (showLoading) _state.update { it.copy(isLoading = false) }
        }
    }

    private fun refreshCallLogMatch(number: String) {
        if (!callLogReader.hasPermission()) {
            _state.update { it.copy(hasCallLogMatch = false) }
            return
        }
        viewModelScope.launch {
            // Cutoff: only this lead's post-assignment calls. Count cumulatively from assignment
            // (todayOnly = false), not just today — this is the lead's running history.
            val since = _state.value.lead?.assignedAt
            val calls = callLogReader.callsForNumber(number, since)
            val booking = bookingFromCalls(calls, todayOnly = false)
            val callLabel = callLabelFor(calls)
            _state.update { s ->
                s.copy(
                    hasCallLogMatch = calls.isNotEmpty(),
                    matchedCalls = calls,

                    lead = if (booking != null && s.lead != null) {
                        s.lead.copy(
                            totalDial = booking.totalDial,
                            connected = booking.connected,
                            talkTime = booking.talkTime,
                            firstCall = booking.firstCall,
                            lastCall = booking.lastCall,
                            labels = mergeLabels(s.lead.labels, callLabel),
                        )
                    } else s.lead,
                )
            }

            if (calls.isNotEmpty()) {
                _state.value.lead?.let { lead ->
                    repo.updateBooking(lead.id, calls)
                    repo.updateDates(lead.id, calls)
                    repo.updateLabels(lead.id, lead.labels, calls)
                }
            }
        }
    }

    fun openCallHistory(number: String) {
        _state.update {
            it.copy(callHistory = CallHistoryState(number = number, isLoading = true))
        }
        if (callLogReader.hasPermission()) {
            loadCallsFor(number)
        } else {
            _state.update {
                it.copy(callHistory = it.callHistory.copy(isLoading = false, needsPermission = true))
            }
        }
    }

    fun onCallLogPermissionResult(granted: Boolean) {
        val number = _state.value.callHistory.number ?: return
        if (granted) {
            _state.update { it.copy(callHistory = it.callHistory.copy(needsPermission = false, isLoading = true)) }
            loadCallsFor(number)
            refreshCallLogMatch(number)
        } else {
            _state.update { it.copy(callHistory = it.callHistory.copy(needsPermission = true, isLoading = false)) }
        }
    }

    fun closeCallHistory() {
        _state.update { it.copy(callHistory = CallHistoryState()) }
    }

    private fun loadCallsFor(number: String) {
        viewModelScope.launch {
            // Same assignment cutoff as the count: the history dialog shows only calls made after
            // this lead was assigned to the agent — nothing from a prior owner.
            val since = _state.value.lead?.assignedAt
            val calls = callLogReader.callsForNumber(number, since)
            _state.update {

                if (it.callHistory.number != number) it
                else it.copy(callHistory = it.callHistory.copy(isLoading = false, calls = calls))
            }
        }
    }

    fun addNote(text: String) {
        val leadId = _state.value.lead?.id ?: return
        if (text.isBlank() || _state.value.isSavingNote) return
        viewModelScope.launch {
            _state.update { it.copy(isSavingNote = true, error = null) }
            repo.addNote(leadId, text)
                .onSuccess { _state.update { it.copy(isSavingNote = false, noteSaveSuccess = true) } }
                .onFailure { e -> _state.update { it.copy(isSavingNote = false, error = e.message) } }
        }
    }

    fun deleteNote(note: Note) {
        val leadId = _state.value.lead?.id ?: return
        viewModelScope.launch {
            repo.deleteNote(leadId, note.id)
                .onFailure { e -> _state.update { it.copy(error = e.message ?: "Couldn't delete the note.") } }
        }
    }

    fun uploadDocument(uri: Uri) {
        val leadId = _state.value.lead?.id ?: return
        viewModelScope.launch {
            _state.update { it.copy(isUploading = true, error = null) }
            repo.uploadDocument(leadId, uri)
                .onSuccess { _state.update { it.copy(isUploading = false, uploadSuccess = "Document uploaded") } }
                .onFailure { e -> _state.update { it.copy(isUploading = false, error = e.message ?: "Upload failed") } }
        }
    }

    /**
     * Sets, or with a null [epochMillis] clears, this lead's reminder date and time.
     *
     * The repository writes locally before pushing to `PUT /api/leads/:id/reminder`, so the on-device
     * alarm and the visible state are updated on **both** paths — a failed push means "not synced to
     * the server yet", not "reminder didn't happen". Skipping the alarm on a network error would drop
     * the part the agent actually relies on. The error still surfaces so they know it isn't on the
     * server, and the next sync reconciles.
     */
    fun setDueDate(epochMillis: Long?) {
        val lead = _state.value.lead ?: return
        val leadId = lead.id
        viewModelScope.launch {
            val result = repo.setDueDate(leadId, epochMillis)

            _state.update { s -> s.copy(lead = s.lead?.copy(dueDate = epochMillis)) }
            if (epochMillis != null) {
                reminderScheduler.schedule(leadId, lead.name, epochMillis)
            } else {
                reminderScheduler.cancel(leadId)
            }

            result.onFailure { e ->
                _state.update {
                    it.copy(error = e.message ?: "Reminder saved on this device, but not synced.")
                }
            }
        }
    }

    fun clearNoteSuccess() = _state.update { it.copy(noteSaveSuccess = false) }
    fun clearUploadSuccess() = _state.update { it.copy(uploadSuccess = null) }
    fun clearError() = _state.update { it.copy(error = null) }
}

data class BugReportsUiState(
    val isLoading: Boolean = false,
    val reports: List<BugReport> = emptyList(),
    val isSubmitting: Boolean = false,
    val submitSuccess: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class BugReportsViewModel @Inject constructor(
    private val repo: BugReportRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BugReportsUiState())
    val state: StateFlow<BugReportsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.observeReports().collect { reports ->
                _state.update { it.copy(reports = reports) }
            }
        }
        sync()
    }

    /** Pulls the team-wide list. Safe to call on every screen entry. */
    fun sync() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            repo.syncReports()
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
            _state.update { it.copy(isLoading = false) }
        }
    }

    fun submit(title: String, description: String) {
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, error = null) }
            repo.submitReport(title, description)
                .onSuccess { _state.update { it.copy(isSubmitting = false, submitSuccess = true) } }
                .onFailure { e ->
                    _state.update {
                        it.copy(isSubmitting = false, error = e.message ?: "Could not file the report")
                    }
                }
        }
    }

    fun clearSubmitSuccess() = _state.update { it.copy(submitSuccess = false) }
    fun clearError() = _state.update { it.copy(error = null) }
}
