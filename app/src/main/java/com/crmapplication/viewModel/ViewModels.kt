package com.crmapplication.viewModel

import android.net.Uri
import com.crmapplication.LeadDetailVM.repository.DashboardRepository
import com.crmapplication.LeadDetailVM.repository.DashboardData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crmapplication.LeadDetailVM.repository.AuthRepository
import com.crmapplication.LeadDetailVM.repository.CallLogSyncRepository
import com.crmapplication.LeadDetailVM.repository.EmailNotVerifiedException
import com.crmapplication.LeadDetailVM.repository.Lead
import com.crmapplication.LeadDetailVM.repository.LeadsRepository
import com.crmapplication.LeadDetailVM.repository.Note
import com.crmapplication.LeadDetailVM.repository.PendingVerificationException
import com.crmapplication.LeadDetailVM.repository.Profile
import com.crmapplication.LeadDetailVM.repository.StatusChange
import com.crmapplication.LeadDetailVM.repository.bookingFromCalls
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

data class ProfileUiState(
    val isLoading: Boolean = true,
    val profile: Profile? = null,
    val name: String = "",
    val email: String = "",
    val isSaving: Boolean = false,
    val profileUpdated: Boolean = false,
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

    fun updateProfile(name: String, email: String) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            repo.updateProfile(name, email)
                .onSuccess { updated ->
                    _state.update {
                        it.copy(
                            isSaving = false,
                            profileUpdated = true,

                            profile = updated ?: it.profile,
                            name = updated?.name?.ifBlank { name.trim() } ?: name.trim(),
                            email = updated?.email?.ifBlank { email.trim() } ?: email.trim(),
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(isSaving = false, error = e.message ?: "Could not update profile") }
                }
        }
    }

    fun clearProfileUpdated() = _state.update { it.copy(profileUpdated = false) }
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
        load()

        viewModelScope.launch {
            repo.observeCallLogChanges()
                .debounce(500)
                .collect {
                    logNewCalls()
                    load(silent = true)
                }
        }
    }

    private fun logNewCalls() {
        viewModelScope.launch { runCatching { callLogSyncRepo.syncNewCalls() } }
    }

    fun load(silent: Boolean = false) {

        if (!repo.hasCallLogPermission()) {
            _state.update { it.copy(isLoading = false, needsPermission = true) }
            return
        }
        viewModelScope.launch {
            _state.update {

                it.copy(isLoading = it.data == null && !silent, needsPermission = false, error = null)
            }

            leadsRepo.syncLeads()
            repo.getDashboard()
                .onSuccess { data -> _state.update { it.copy(isLoading = false, data = data) } }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message) } }
        }

        logNewCalls()
    }

    fun refresh() = load(silent = true)

    fun onPermissionResult(granted: Boolean) {
        if (granted) load()
        else _state.update { it.copy(isLoading = false, needsPermission = true) }
    }
}

enum class SortOrder { BY_DUE_DATE, BY_AGE, BY_NAME }

enum class LeadFilter(val label: String, val statusMatch: String?) {

    ALL("All", null),
    FRESH("Fresh", "Fresh Leads"),
    INTERESTED("Interested", "Interested Leads"),
    PRE_PROSPECT("Pre-Prospect", "Pre Prospect Leads"),
    PROSPECT("Prospect", "Prospect Leads"),
    BOOKED("Booked", "Booked"),
    REJECTED("Rejected", "Rejected Leads"),
}

data class LeadsUiState(
    val isLoading: Boolean = false,
    val leads: List<Lead> = emptyList(),
    val sortOrder: SortOrder = SortOrder.BY_DUE_DATE,

    val activeFilter: LeadFilter? = null,

    val activeProduct: String? = null,
    val searchQuery: String = "",

    val isCreating: Boolean = false,
    val createSuccess: Boolean = false,
    val error: String? = null,
) {

    val visibleLeads: List<Lead>
        get() = leads
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

    fun countFor(filter: LeadFilter): Int =
        filter.statusMatch?.let { s -> leads.count { it.status.equals(s, ignoreCase = true) } }
            ?: leads.size

    val availableProducts: List<String>
        get() = leads.mapNotNull { it.product?.takeIf { p -> p.isNotBlank() } }
            .distinct()
            .sorted()
}

@HiltViewModel
class LeadsViewModel @Inject constructor(
    private val repo: LeadsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LeadsUiState(isLoading = true))
    val state: StateFlow<LeadsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.observeLeads().collect { leads ->
                _state.update { it.copy(isLoading = false, leads = sortedLeads(leads, it.sortOrder)) }
            }
        }
        sync()
    }

    fun sync() {
        viewModelScope.launch {
            repo.syncLeads().onFailure { e ->
                _state.update { it.copy(error = e.message) }
            }
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

    fun updateStatus(leadId: String, status: String) {
        viewModelScope.launch {
            repo.updateStatus(leadId, status).onFailure { e ->
                _state.update { it.copy(error = e.message ?: "Could not update status") }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    private fun sortedLeads(leads: List<Lead>, order: SortOrder) = when (order) {
        SortOrder.BY_DUE_DATE -> leads.sortedWith(compareBy(nullsLast()) { it.dueDate })
        SortOrder.BY_AGE      -> leads.sortedByDescending { it.createdAt }
        SortOrder.BY_NAME     -> leads.sortedBy { it.name }
    }
}

data class LeadDetailUiState(
    val lead: Lead? = null,
    val notes: List<Note> = emptyList(),
    val noteSaveSuccess: Boolean = false,
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

    fun refresh() {
        _state.value.lead?.phone?.let { refreshCallLogMatch(it) }
    }

    private fun refreshCallLogMatch(number: String) {
        if (!callLogReader.hasPermission()) {
            _state.update { it.copy(hasCallLogMatch = false) }
            return
        }
        viewModelScope.launch {
            val calls = callLogReader.callsForNumber(number)
            val booking = bookingFromCalls(calls)
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
            val calls = callLogReader.callsForNumber(number)
            _state.update {

                if (it.callHistory.number != number) it
                else it.copy(callHistory = it.callHistory.copy(isLoading = false, calls = calls))
            }
        }
    }

    fun addNote(text: String) {
        val leadId = _state.value.lead?.id ?: return
        if (text.isBlank()) return
        viewModelScope.launch {
            repo.addNote(leadId, text)
                .onSuccess { _state.update { it.copy(noteSaveSuccess = true) } }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
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

    fun setDueDate(epochMillis: Long?) {
        val lead = _state.value.lead ?: return
        val leadId = lead.id
        viewModelScope.launch {
            repo.setDueDate(leadId, epochMillis)
                .onSuccess {
                    _state.update { s ->
                        s.copy(lead = s.lead?.copy(dueDate = epochMillis))
                    }

                    if (epochMillis != null) {
                        reminderScheduler.schedule(leadId, lead.name, epochMillis)
                    } else {
                        reminderScheduler.cancel(leadId)
                    }
                }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun clearNoteSuccess() = _state.update { it.copy(noteSaveSuccess = false) }
    fun clearUploadSuccess() = _state.update { it.copy(uploadSuccess = null) }
    fun clearError() = _state.update { it.copy(error = null) }
}
