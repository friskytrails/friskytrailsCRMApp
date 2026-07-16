package com.crmapplication.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.crmapplication.LeadDetailVM.repository.DashboardStats
import com.crmapplication.LeadDetailVM.repository.MonthlyStats
import com.crmapplication.ui.theme.*
import com.crmapplication.viewModel.DashboardViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    agentName: String,
    onNavigateToLeads: () -> Unit,
    onNavigateToAddLead: () -> Unit,
    onNavigateToProfile: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val now = remember { Date() }
    val todayDate = remember { SimpleDateFormat("EEEE, d MMM", Locale.getDefault()).format(now) }
    val todayTime = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()).format(now) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val callLogPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onPermissionResult(granted)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "db_bubble")
    val bubbleAnim by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(7000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "db_b"
    )

    Scaffold(
        topBar = {

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(CrmPrimary, CrmSecondary)
                        )
                    )
            ) {

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (20 + bubbleAnim * 10).dp, y = (-20 + bubbleAnim * 10).dp)
                        .size(130.dp)
                        .blur(30.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f))
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Good day 👋", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                        Text(
                            agentName.ifBlank { "Agent" },
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                        )
                    }
                    Surface(
                        color = Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Column(
                            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.End,
                        ) {
                            Text(
                                todayDate,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                todayTime,
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = true,
                    onClick = {  },
                    icon = { Text("📊", fontSize = 20.sp) },
                    label = { Text("Dashboard") },
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateToAddLead,
                    icon = { AddLeadNavIcon() },
                    label = { Text("Add Lead") },
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateToLeads,
                    icon = { Text("👥", fontSize = 20.sp) },
                    label = { Text("Leads") },
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateToProfile,
                    icon = { Text("👤", fontSize = 20.sp) },
                    label = { Text("Profile") },
                )
            }
        }
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {

            if (state.isLoading) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CrmPrimary)
                }
            } else if (state.needsPermission) {
                CallLogPermissionCard(
                    onGrant = { callLogPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG) }
                )
            } else {
                val data = state.data
                if (data != null) {
                    PerformanceCard(stats = data.daily)

                    Spacer(Modifier.height(16.dp))

                    MonthlyCard(stats = data.monthly)

                    Spacer(Modifier.height(20.dp))
                }

                state.error?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun CallLogPermissionCard(onGrant: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("📞", fontSize = 40.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                "Call-log access needed",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Your performance stats are calculated from the calls you've made " +
                    "to your leads. Allow access to see real talk time and dials.",
                textAlign = TextAlign.Center,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onGrant) { Text("Grant call-log access") }
        }
    }
}

@Composable
private fun AddLeadNavIcon() {
    Box(
        modifier = Modifier
            .size(28.dp)
            .border(width = 1.5.dp, color = CrmPrimary, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text("+", color = CrmPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PerformanceCard(stats: DashboardStats) {

    val metrics = listOf(
        StatMetric("📞", "Total Dial", stats.totalDials.toString()),
        StatMetric("⏱️", "Total Talktime", stats.totalTalktime),
        StatMetric("✅", "Connected Calls", stats.connectedCalls.toString()),
        StatMetric("👤", "Unique Calls", stats.uniqueCalls.toString()),
        StatMetric("🔁", "Call more than…", stats.callMoreThan.toString()),
        StatMetric("🕛", "First Call", stats.firstCall ?: "—"),
        StatMetric("🕔", "Last Call", stats.lastCall ?: "—"),
        StatMetric("😴", "Idle Time", stats.idleTime),
        StatMetric("🗓️", "Attendance", stats.attendance, attendanceColor(stats.attendance)),
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column {

            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(CrmPrimary, CrmSecondary)))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Performance on", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(stats.date, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                metrics.chunked(3).forEach { rowItems ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        rowItems.forEach { StatTile(it) }

                        repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

private data class StatMetric(
    val icon: String,
    val label: String,
    val value: String,

    val valueColor: Color? = null,
)

@Composable
private fun RowScope.StatTile(metric: StatMetric) {
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .heightIn(min = 96.dp)
            .padding(horizontal = 6.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(metric.icon, fontSize = 20.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            metric.value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = metric.valueColor ?: MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            metric.label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            lineHeight = 13.sp,
        )
    }
}

@Composable
private fun PerformanceRow(
    icon: String,
    label: String,
    value: String,
    showDivider: Boolean = true,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, fontSize = 18.sp)
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            value,
            color = valueColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
    if (showDivider) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
    }
}

private fun attendanceColor(attendance: String): Color? = when (attendance) {
    "Present" -> Color(0xFF16A34A)
    "Absent" -> Color(0xFFDC2626)

    else -> null
}

@Composable
private fun MonthlyCard(stats: MonthlyStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(CrmPrimary, CrmSecondary)))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Monthly", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(stats.month, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            PerformanceRow("🎯", "Monthly Target", stats.monthlyTarget)
            PerformanceRow("📘", "Booking Count", stats.bookingCount)
            PerformanceRow("🗓️", "Attendance", stats.attendance, showDivider = false)
        }
    }
}
