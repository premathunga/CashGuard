package com.cashguard.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cashguard.app.data.TransactionEntity
import com.cashguard.app.data.TxType
import com.cashguard.app.ui.i18n.LocalStrings
import com.cashguard.app.ui.theme.*
import com.cashguard.app.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onAddClick: () -> Unit,
    onViewAll: () -> Unit = {},
    onAdjustLimits: () -> Unit = {}
) {
    val s = LocalStrings.current
    val recent by viewModel.recentTransactions.collectAsStateWithLifecycle()
    val spent by viewModel.totalSpentThisMonth.collectAsStateWithLifecycle()
    val income by viewModel.totalIncomeThisMonth.collectAsStateWithLifecycle()
    val budgetItems by viewModel.budgetItems.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val allTransactions by viewModel.allTransactions.collectAsStateWithLifecycle()

    // One latest-known balance per bank — a user can hold accounts in several
    // banks, and each alert only carries that bank's own running balance.
    val latestPerBank = allTransactions
        .groupBy { it.source }
        .mapNotNull { (_, txs) -> txs.maxByOrNull { it.timestamp } }
        .sortedByDescending { it.timestamp }
    val currentBalance = latestPerBank.sumOf { it.balanceAfter }
    val totalBudget = budgetItems.sumOf { it.amount }.let { if (it <= 0.0) 1.0 else it }
    val usagePercent = min(100, ((spent / totalBudget) * 100).toInt())

    val partySessionSpent = if (settings.partyGuardArmed) {
        allTransactions
            .filter { it.type == TxType.DEBIT && it.timestamp >= settings.partyGuardSessionStart }
            .sumOf { it.amount }
    } else 0.0

    var showArmDialog by remember { mutableStateOf(false) }
    var showSnoozeDialog by remember { mutableStateOf(false) }

    if (showArmDialog) {
        ArmPartyGuardDialog(
            defaultCap = settings.partyGuardCap,
            onArm = { cap ->
                viewModel.armPartyGuard(cap)
                showArmDialog = false
            },
            onDismiss = { showArmDialog = false }
        )
    }
    if (showSnoozeDialog) {
        SnoozeCooldownDialog(
            onConfirm = {
                viewModel.snoozePartyGuard(60 * 60 * 1000L) // 1 hour
                showSnoozeDialog = false
            },
            onDismiss = { showSnoozeDialog = false }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("CashGuard", color = PrimaryPurpleLight, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Text(
                        SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date()),
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
                Spacer(Modifier.height(20.dp))
                BalanceHeroCard(
                    balance = currentBalance,
                    banks = latestPerBank
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.TrendingUp,
                        iconColor = SuccessGreen,
                        label = s.income,
                        value = "Rs %,.0f".format(income)
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.TrendingDown,
                        iconColor = DangerRedSoft,
                        label = s.spent,
                        value = "Rs %,.0f".format(spent)
                    )
                }
                Spacer(Modifier.height(16.dp))
                BudgetUsageCard(
                    usagePercent = usagePercent,
                    remaining = totalBudget - spent,
                    onAdjustLimits = onAdjustLimits
                )
                Spacer(Modifier.height(16.dp))
                PartyGuardCard(
                    armed = settings.partyGuardArmed,
                    snoozedUntil = settings.partyGuardSnoozeUntil,
                    cap = settings.partyGuardCap,
                    sessionSpent = partySessionSpent,
                    onArm = { showArmDialog = true },
                    onDisarm = { viewModel.disarmPartyGuard() },
                    onSnooze = { showSnoozeDialog = true }
                )
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(s.recentTransactions, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    Text(
                        s.viewAll,
                        color = PrimaryPurpleLight,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onViewAll() }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            if (recent.isEmpty()) {
                item { EmptyStateHint() }
            }

            items(recent) { tx ->
                TransactionRow(tx)
                Spacer(Modifier.height(10.dp))
            }

            item { Spacer(Modifier.height(70.dp)) }
        }

        FloatingActionButton(
            onClick = onAddClick,
            containerColor = PrimaryPurple,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add budget item", tint = Color.White)
        }
    }
}

@Composable
private fun EmptyStateHint() {
    val s = LocalStrings.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceDark)
            .padding(20.dp)
    ) {
        Text(s.noTransactionsYet, color = TextPrimary, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            s.enableAccessHint,
            color = TextSecondary,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun StatCard(modifier: Modifier = Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, iconColor: Color, label: String, value: String) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceDark)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = iconColor, fontSize = 13.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(value, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
    }
}

@Composable
private fun BalanceHeroCard(balance: Double, banks: List<TransactionEntity>) {
    val s = LocalStrings.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(PrimaryPurple, AccentBlue)))
            .padding(22.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (banks.size > 1) s.totalBalanceFmt.format(banks.size) else s.currentBalance,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 14.sp
            )
            Icon(
                Icons.Filled.AccountBalanceWallet,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Rs %,.2f".format(balance),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 34.sp
        )
        if (banks.size == 1) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.18f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(banks[0].source, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    s.updatedFmt.format(
                        SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                            .format(Date(banks[0].timestamp))
                    ),
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 11.sp
                )
            }
        } else if (banks.size > 1) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                banks.forEach { tx ->
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.16f))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            tx.source,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Rs %,.0f".format(tx.balanceAfter),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetUsageCard(usagePercent: Int, remaining: Double, onAdjustLimits: () -> Unit) {
    val s = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceDark)
            .padding(20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(s.budgetUsage, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                s.leftThisCycleFmt.format("%,.0f".format(remaining.coerceAtLeast(0.0))),
                color = TextSecondary,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(PrimaryPurple)
                    .clickable { onAdjustLimits() }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(s.adjustLimits, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp)) {
            CircularProgressIndicator(
                progress = { usagePercent / 100f },
                modifier = Modifier.size(72.dp),
                color = PrimaryPurpleLight,
                trackColor = DividerColor,
                strokeWidth = 6.dp
            )
            Text("$usagePercent%", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

@Composable
private fun PartyGuardCard(
    armed: Boolean,
    snoozedUntil: Long,
    cap: Double,
    sessionSpent: Double,
    onArm: () -> Unit,
    onDisarm: () -> Unit,
    onSnooze: () -> Unit
) {
    val s = LocalStrings.current
    val snoozed = armed && System.currentTimeMillis() < snoozedUntil
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (armed) Brush.horizontalGradient(listOf(PrimaryPurple, AccentBlue))
                else Brush.horizontalGradient(listOf(SurfaceDark, SurfaceDark))
            )
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (armed) s.partyGuardArmed else s.partyGuard,
                    color = if (armed) Color.White else TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    when {
                        snoozed -> s.partySnoozedDesc
                        armed -> s.partyCapUsedFmt.format("%,.0f".format(sessionSpent), "%,.0f".format(cap))
                        else -> s.partyGuardIdleDesc
                    },
                    color = if (armed) Color.White.copy(alpha = 0.85f) else TextSecondary,
                    fontSize = 12.sp
                )
            }
            if (!armed) {
                Button(
                    onClick = onArm,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(s.arm, color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (armed) {
            Spacer(Modifier.height(12.dp))
            val progress = (sessionSpent / cap).toFloat().coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = if (progress >= 0.8f) DangerRed else SuccessGreen,
                trackColor = Color.White.copy(alpha = 0.25f)
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onSnooze,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.6f))
                ) {
                    Text(s.snooze1h, color = Color.White, fontSize = 13.sp)
                }
                Button(
                    onClick = onDisarm,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(s.disarm, color = PrimaryPurple, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun ArmPartyGuardDialog(
    defaultCap: Double,
    onArm: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    val s = LocalStrings.current
    var capText by remember { mutableStateOf("%.0f".format(defaultCap)) }
    val cap = capText.toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = { Text(s.armDialogTitle, color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    s.armDialogDesc,
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = capText,
                    onValueChange = { capText = it.filter { c -> c.isDigit() } },
                    label = { Text(s.capLabel, color = TextSecondary) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = PrimaryPurpleLight,
                        unfocusedBorderColor = DividerColor
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { cap?.let(onArm) },
                enabled = cap != null && cap > 0
            ) { Text(s.armConfirm, color = PrimaryPurpleLight, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(s.cancel, color = TextSecondary) }
        }
    )
}

/**
 * Deliberate friction: the snooze button only becomes tappable after a
 * 30-second countdown, so an impulsive (or drunk) override takes patience.
 */
@Composable
private fun SnoozeCooldownDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    var secondsLeft by remember { mutableIntStateOf(30) }
    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1000)
            secondsLeft--
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = { Text(s.pauseDialogTitle, color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    s.pauseDialogDesc,
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    if (secondsLeft > 0) s.confirmInFmt.format(secondsLeft) else s.yourCall,
                    color = if (secondsLeft > 0) DangerRedSoft else SuccessGreen,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = secondsLeft <= 0) {
                Text(
                    s.snoozeConfirm,
                    color = if (secondsLeft <= 0) PrimaryPurpleLight else TextSecondary
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(s.keepGuarding, color = SuccessGreen) }
        }
    )
}

@Composable
fun TransactionRow(tx: TransactionEntity) {
    val isDebit = tx.type == TxType.DEBIT
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceDark)
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background((if (isDebit) DangerRed else SuccessGreen).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isDebit) Icons.Filled.ShoppingCart else Icons.Filled.AccountBalanceWallet,
                    contentDescription = null,
                    tint = if (isDebit) DangerRedSoft else SuccessGreen,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(tx.merchant, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(
                    SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(tx.timestamp)),
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                (if (isDebit) "- " else "+ ") + "Rs %,.2f".format(tx.amount),
                color = if (isDebit) DangerRedSoft else SuccessGreen,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
        }
    }
}
