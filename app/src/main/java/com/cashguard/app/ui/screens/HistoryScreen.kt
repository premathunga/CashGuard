package com.cashguard.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cashguard.app.data.TransactionEntity
import com.cashguard.app.data.TxType
import com.cashguard.app.ui.i18n.LocalStrings
import com.cashguard.app.ui.i18n.Strings
import com.cashguard.app.ui.theme.*
import com.cashguard.app.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class Filter { ALL, SPENDING, INCOME }

@Composable
fun HistoryScreen(viewModel: MainViewModel) {
    val s = LocalStrings.current
    val all by viewModel.allTransactions.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(Filter.ALL) }
    var bankFilter by remember { mutableStateOf<String?>(null) }
    var selectedTx by remember { mutableStateOf<TransactionEntity?>(null) }

    val banks = all.map { it.source }.distinct().sorted()

    val filtered = all.filter { tx ->
        val matchesQuery = query.isBlank() ||
            tx.merchant.contains(query, ignoreCase = true) ||
            tx.source.contains(query, ignoreCase = true) ||
            tx.rawMessage.contains(query, ignoreCase = true)
        val matchesFilter = when (filter) {
            Filter.ALL -> true
            Filter.SPENDING -> tx.type == TxType.DEBIT
            Filter.INCOME -> tx.type == TxType.CREDIT
        }
        val matchesBank = bankFilter == null || tx.source == bankFilter
        matchesQuery && matchesFilter && matchesBank
    }

    val totalIn = filtered.filter { it.type == TxType.CREDIT }.sumOf { it.amount }
    val totalOut = filtered.filter { it.type == TxType.DEBIT }.sumOf { it.amount }
    val grouped = filtered.groupBy { dayLabel(it.timestamp, s) }

    selectedTx?.let { tx ->
        TransactionDetailDialog(tx = tx, onDismiss = { selectedTx = null })
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp)
    ) {
        item {
            Text(s.historyTitle, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 26.sp)
            Text(
                s.transactionsCountFmt.format(filtered.size),
                color = TextSecondary,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(16.dp))

            SearchField(query = query, onQueryChange = { query = it })
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Filter.entries.forEach { f ->
                    HistoryFilterChip(
                        label = when (f) {
                            Filter.ALL -> s.filterAll
                            Filter.SPENDING -> s.filterSpending
                            Filter.INCOME -> s.filterIncome
                        },
                        selected = filter == f,
                        onClick = { filter = f }
                    )
                }
            }
            if (banks.size > 1) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HistoryFilterChip(
                        label = s.allBanks,
                        selected = bankFilter == null,
                        onClick = { bankFilter = null }
                    )
                    banks.forEach { bank ->
                        HistoryFilterChip(
                            label = bank,
                            selected = bankFilter == bank,
                            onClick = { bankFilter = if (bankFilter == bank) null else bank }
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            InOutSummaryBar(totalIn = totalIn, totalOut = totalOut)
            Spacer(Modifier.height(20.dp))
        }

        if (grouped.isEmpty()) {
            item { EmptyHistoryState(searching = query.isNotBlank() || filter != Filter.ALL) }
        }

        grouped.forEach { (label, txs) ->
            item(key = "header-$label") {
                DayHeader(label = label, transactions = txs)
                Spacer(Modifier.height(8.dp))
            }
            items(txs, key = { it.id }) { tx ->
                HistoryRow(tx = tx, onClick = { selectedTx = tx })
                Spacer(Modifier.height(8.dp))
            }
            item(key = "footer-$label") { Spacer(Modifier.height(10.dp)) }
        }

        item { Spacer(Modifier.height(60.dp)) }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val s = LocalStrings.current
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceDark)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
        cursorBrush = SolidColor(PrimaryPurpleLight),
        decorationBox = { inner ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Search, contentDescription = null,
                    tint = TextSecondary, modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(s.searchHint, color = TextSecondary, fontSize = 14.sp)
                    }
                    inner()
                }
                if (query.isNotEmpty()) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Clear search",
                        tint = TextSecondary,
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .clickable { onQueryChange("") }
                    )
                }
            }
        }
    )
}

@Composable
private fun HistoryFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) PrimaryPurple else SurfaceDark)
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 9.dp)
    ) {
        Text(
            label,
            color = if (selected) Color.White else TextSecondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun InOutSummaryBar(totalIn: Double, totalOut: Double) {
    val s = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceDark)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.ArrowUpward, contentDescription = null,
                tint = SuccessGreen, modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(6.dp))
            Column {
                Text(s.inLabel, color = TextSecondary, fontSize = 11.sp)
                Text(
                    "Rs %,.0f".format(totalIn),
                    color = SuccessGreen, fontWeight = FontWeight.SemiBold, fontSize = 14.sp
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier
                .height(32.dp)
                .width(1.dp),
            color = DividerColor
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.ArrowDownward, contentDescription = null,
                tint = DangerRedSoft, modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(6.dp))
            Column {
                Text(s.outLabel, color = TextSecondary, fontSize = 11.sp)
                Text(
                    "Rs %,.0f".format(totalOut),
                    color = DangerRedSoft, fontWeight = FontWeight.SemiBold, fontSize = 14.sp
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier
                .height(32.dp)
                .width(1.dp),
            color = DividerColor
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(s.netLabel, color = TextSecondary, fontSize = 11.sp)
            val net = totalIn - totalOut
            Text(
                (if (net >= 0) "+" else "−") + " Rs %,.0f".format(kotlin.math.abs(net)),
                color = if (net >= 0) SuccessGreen else DangerRedSoft,
                fontWeight = FontWeight.SemiBold, fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun DayHeader(label: String, transactions: List<TransactionEntity>) {
    val dayNet = transactions.sumOf { if (it.type == TxType.CREDIT) it.amount else -it.amount }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label.uppercase(),
            color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
        )
        Text(
            (if (dayNet >= 0) "+" else "−") + " Rs %,.0f".format(kotlin.math.abs(dayNet)),
            color = if (dayNet >= 0) SuccessGreen else TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun HistoryRow(tx: TransactionEntity, onClick: () -> Unit) {
    val isDebit = tx.type == TxType.DEBIT
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceDark)
            .clickable { onClick() }
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background((if (isDebit) DangerRed else SuccessGreen).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isDebit) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                    contentDescription = null,
                    tint = if (isDebit) DangerRedSoft else SuccessGreen,
                    modifier = Modifier.size(17.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    tx.merchant,
                    color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    maxLines = 1
                )
                Text(
                    "${tx.source} • " +
                        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(tx.timestamp)),
                    color = TextSecondary, fontSize = 12.sp
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                (if (isDebit) "− " else "+ ") + "Rs %,.2f".format(tx.amount),
                color = if (isDebit) DangerRedSoft else SuccessGreen,
                fontWeight = FontWeight.SemiBold, fontSize = 14.sp
            )
            Text(
                LocalStrings.current.balPrefixFmt.format("%,.0f".format(tx.balanceAfter)),
                color = TextSecondary, fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun TransactionDetailDialog(tx: TransactionEntity, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    val isDebit = tx.type == TxType.DEBIT
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = {
            Column {
                Text(
                    (if (isDebit) "− " else "+ ") + "Rs %,.2f".format(tx.amount),
                    color = if (isDebit) DangerRedSoft else SuccessGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                )
                Text(
                    if (isDebit) s.spentWord else s.receivedWord,
                    color = TextSecondary, fontSize = 13.sp
                )
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                DetailRow(s.bankLabel, tx.source)
                DetailRow(s.merchantLabel, tx.merchant)
                DetailRow(
                    s.dateTimeLabel,
                    SimpleDateFormat("EEE, MMM d yyyy • h:mm a", Locale.getDefault())
                        .format(Date(tx.timestamp))
                )
                DetailRow(s.balanceAfterLabel, "Rs %,.2f".format(tx.balanceAfter))
                if (tx.matchedBudgetItemId != null) {
                    DetailRow(s.navBudget, s.autoMatched)
                }
                if (tx.rawMessage.isNotBlank() && !tx.rawMessage.startsWith("SIMULATED")) {
                    Spacer(Modifier.height(10.dp))
                    Text(s.originalMessage, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceLight)
                            .padding(12.dp)
                    ) {
                        Text(tx.rawMessage, color = TextPrimary, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(s.close, color = PrimaryPurpleLight) }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.width(16.dp))
        Text(
            value, color = TextPrimary, fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

@Composable
private fun EmptyHistoryState(searching: Boolean) {
    val s = LocalStrings.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceDark)
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ReceiptLong,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(40.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            if (searching) s.noMatching else s.noTransactionsYet,
            color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (searching) s.noMatchingHint else s.emptyHistoryHint,
            color = TextSecondary, fontSize = 13.sp
        )
    }
}

private fun dayLabel(timestamp: Long, s: Strings): String {
    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val weekAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }
    return when {
        isSameDay(cal, today) -> s.today
        isSameDay(cal, yesterday) -> s.yesterday
        cal.after(weekAgo) -> s.thisWeek
        cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) ->
            SimpleDateFormat("MMM d", Locale.getDefault()).format(cal.time)
        else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(cal.time)
    }
}

private fun isSameDay(a: Calendar, b: Calendar): Boolean =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
