package com.crmapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.crmapplication.LeadDetailVM.repository.Lead
import com.crmapplication.ui.component.LeadCard
import com.crmapplication.ui.theme.CrmOnBackground
import com.crmapplication.ui.theme.CrmPrimary
import com.crmapplication.ui.theme.CrmSecondary
import com.crmapplication.viewModel.LeadFilter
import com.crmapplication.viewModel.LeadsUiState
import com.crmapplication.viewModel.LeadsViewModel
import com.crmapplication.viewModel.SortOrder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeadsListScreen(
    onLeadClick: (Lead) -> Unit,
    onBack: () -> Unit,
    viewModel: LeadsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showSortMenu by remember { mutableStateOf(false) }

    // Booking can fail (403 if the lead was reassigned, 400 on a rejected payload) and the form stays
    // open on failure, so this screen needs somewhere to surface the message.
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.error) {
        state.error?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }
    LaunchedEffect(state.bookingSuccess) {
        if (state.bookingSuccess) {
            snackbarHostState.showSnackbar("Lead booked. Status is now locked.")
            viewModel.clearBookingSuccess()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(CrmPrimary, CrmSecondary)
                            )
                        )
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                            )
                        }
                        Text(
                            "Leads (${state.leads.size})",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { viewModel.sync(force = true) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
                        }
                        Box {
                            TextButton(onClick = { showSortMenu = true }) {
                                Text("Sort ▾", color = Color.White, fontSize = 13.sp)
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("By due date") },
                                    onClick = { viewModel.setSortOrder(SortOrder.BY_DUE_DATE); showSortMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("By age") },
                                    onClick = { viewModel.setSortOrder(SortOrder.BY_AGE); showSortMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("By name") },
                                    onClick = { viewModel.setSortOrder(SortOrder.BY_NAME); showSortMenu = false }
                                )
                            }
                        }
                    }
                }
                if (state.isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.White,
                        trackColor = CrmPrimary.copy(alpha = 0.3f)
                    )
                }
            }
        }
    ) { innerPadding ->
        run {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(innerPadding)
            ) {
                if (state.leads.isEmpty()) {
                    // Only the settled empty state ("No leads yet") once a sync has actually
                    // completed. Before that, stay blank — the top-bar progress bar signals the
                    // first load, so we never flash "No leads yet" at a user who does have leads.
                    if (state.hasSynced) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("👥", fontSize = 48.sp)
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "No leads yet",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 18.sp,
                                    color = CrmOnBackground,
                                )
                                Text(
                                    "Pull to refresh or tap Sync",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                } else {

                    FilterChips(
                        state = state,
                        onFilterClick = { viewModel.toggleFilter(it) },
                    )

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SearchBar(
                            query = state.searchQuery,
                            onQueryChange = { viewModel.setSearchQuery(it) },
                            modifier = Modifier.weight(1f),
                        )

                        if (state.availableProducts.isNotEmpty()) {
                            ProductFilter(
                                products = state.availableProducts,
                                selected = state.activeProduct,
                                onSelect = { viewModel.setProductFilter(it) },
                            )
                        }
                    }

                    val visible = state.visibleLeads
                    if (visible.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🔍", fontSize = 40.sp)
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    "No matching leads",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp,
                                    color = CrmOnBackground,
                                )
                                Text(
                                    "Try another filter or search term",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp),
                        ) {
                            items(visible, key = { it.id }) { lead ->
                                LeadCard(
                                    lead = lead,
                                    statuses = state.statusOptions,
                                    onClick = { onLeadClick(lead) },
                                    onStatusChange = { newStatus -> viewModel.updateStatus(lead.id, newStatus) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Hoisted out of the Scaffold content lambda deliberately: the dialog belongs to the screen, not to
    // the scrolling list, and it must survive the list recomposing under it.
    state.bookingFor?.let { lead ->
        BookingDetailsDialog(
            lead = lead,
            products = state.products,
            isSubmitting = state.isBooking,
            onSubmit = { viewModel.submitBooking(it) },
            onDismiss = { viewModel.cancelBooking() },
        )
    }
}

private val ProspectColor = Color(0xFFE05260)
private val PreProspectColor = Color(0xFFE0A020)
private val InterestedColor = Color(0xFF1E9E7E)

/**
 * Extra accents for statuses the app has never seen. Picked per status name so a backend-added
 * status gets a stable colour instead of the default blue every time.
 */
private val ExtraFilterAccents = listOf(
    Color(0xFF7C3AED),
    Color(0xFF0891B2),
    Color(0xFFD97706),
    Color(0xFF16A34A),
    Color(0xFFDC2626),
    Color(0xFF2563EB),
)

/**
 * Chip accent for a filter. The original six statuses keep the exact colours they had when this list
 * was hardcoded; anything new is assigned deterministically from [ExtraFilterAccents] by name hash,
 * so the colour is stable across launches and devices rather than shifting per session.
 */
private fun filterAccent(filter: LeadFilter): Color {
    val status = filter.statusMatch ?: return CrmPrimary
    return when (status.trim().lowercase()) {
        "fresh leads" -> CrmPrimary
        "interested leads" -> InterestedColor
        "pre prospect leads" -> PreProspectColor
        "prospect leads" -> ProspectColor
        "booked" -> CrmPrimary
        "rejected leads" -> CrmPrimary
        else -> ExtraFilterAccents[status.lowercase().hashCode().mod(ExtraFilterAccents.size)]
    }
}

/**
 * Status filter chips, built from the server's status list rather than a fixed set — a status added
 * on the backend shows up here without an app release.
 *
 * Split across two scrollable rows: with the default six statuses this reproduces the original 4/3
 * layout exactly, and a longer list stays reachable because each row scrolls horizontally.
 */
@Composable
private fun FilterChips(
    state: LeadsUiState,
    onFilterClick: (LeadFilter) -> Unit,
) {
    val chips = state.filters
    // Ceiling division, so any odd chip lands on the first row: with the default six statuses that
    // reproduces the previous 4/3 split. coerceAtLeast guards chunked(), which rejects a size of 0.
    val rows = chips.chunked(((chips.size + 1) / 2).coerceAtLeast(1))

    Column(Modifier.padding(top = 8.dp, bottom = 4.dp)) {
        rows.forEachIndexed { index, rowChips ->
            if (index > 0) Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowChips.forEach { filter ->
                    FilterChip(filter, state, filterAccent(filter), onFilterClick)
                }
            }
        }
    }
}

@Composable
private fun FilterChip(
    filter: LeadFilter,
    state: LeadsUiState,
    accent: Color,
    onClick: (LeadFilter) -> Unit,
) {

    val selected = state.activeFilter == filter ||
        (filter == LeadFilter.All && state.activeFilter == null)
    Surface(
        color = if (selected) accent else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
        modifier = Modifier.clickable { onClick(filter) },
    ) {
        Text(
            "${filter.label} (${state.countFor(filter)})",
            color = if (selected) Color.White else accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun ProductFilter(
    products: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val active = selected != null
    Box {
        Surface(
            color = if (active) CrmPrimary else MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp),
            border = if (active) null
                else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shadowElevation = if (active) 2.dp else 0.dp,
            modifier = Modifier
                .height(52.dp)
                .clickable { expanded = true },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 14.dp, end = 8.dp),
            ) {
                Icon(
                    Icons.Default.FilterList,
                    contentDescription = "Filter by product",
                    tint = if (active) Color.White else CrmPrimary,
                    modifier = Modifier.size(20.dp),
                )

                if (active) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        selected!!,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 90.dp),
                    )
                }
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = if (active) Color.White else CrmPrimary,
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("All Products", fontWeight = if (!active) FontWeight.SemiBold else FontWeight.Normal) },
                leadingIcon = {
                    if (!active) Icon(Icons.Default.Check, contentDescription = null, tint = CrmPrimary)
                },
                onClick = { onSelect(null); expanded = false },
            )
            products.forEach { product ->
                val isSelected = product == selected
                DropdownMenuItem(
                    text = { Text(product, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal) },
                    leadingIcon = {
                        if (isSelected) Icon(Icons.Default.Check, contentDescription = null, tint = CrmPrimary)
                    },
                    onClick = { onSelect(product); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search by name or number", fontSize = 14.sp) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
        singleLine = true,
        shape = RoundedCornerShape(24.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedBorderColor = CrmPrimary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        ),
        modifier = modifier,
    )
}
