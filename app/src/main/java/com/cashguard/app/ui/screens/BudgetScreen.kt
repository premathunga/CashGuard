package com.cashguard.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cashguard.app.data.BudgetItemEntity
import com.cashguard.app.ui.i18n.LocalStrings
import com.cashguard.app.ui.i18n.Strings
import com.cashguard.app.ui.theme.*
import com.cashguard.app.viewmodel.MainViewModel
import java.util.*

@Composable
fun BudgetScreen(viewModel: MainViewModel, onAddClick: () -> Unit) {
    val s = LocalStrings.current
    val items by viewModel.budgetItems.collectAsStateWithLifecycle()
    val cycleLabel by viewModel.cycleLabel.collectAsStateWithLifecycle()
    val paidCount = items.count { it.isPaid }
    val totalCount = items.size
    val remaining = items.filter { !it.isPaid }.sumOf { it.amount }
    val progress = if (totalCount == 0) 0f else paidCount / totalCount.toFloat()
    var itemToDelete by remember { mutableStateOf<BudgetItemEntity?>(null) }

    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            containerColor = SurfaceDark,
            title = { Text(s.deleteTitleFmt.format(item.name), color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    s.deleteDesc + " Rs %,.0f".format(item.amount),
                    color = TextSecondary, fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteBudgetItem(item.id)
                    itemToDelete = null
                }) { Text(s.delete, color = DangerRedSoft, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) { Text(s.cancel, color = TextSecondary) }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp)
    ) {
        item {
            Text(s.monthlyChecklist, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(cycleLabel, color = TextSecondary, fontSize = 14.sp)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceLight)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(s.paidOfFmt.format(paidCount, totalCount), color = TextPrimary, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = PrimaryPurple,
                trackColor = SurfaceLight
            )
            Spacer(Modifier.height(6.dp))
            Column {
                Text(s.remainingFmt.format("%,.0f".format(remaining)), color = SuccessGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(s.tapHoldHint, color = TextSecondary, fontSize = 11.sp)
            }
            Spacer(Modifier.height(20.dp))
        }

        if (items.isEmpty()) {
            item {
                Text(
                    s.noBudgetHint,
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
        }

        items(items, key = { it.id }) { item ->
            BudgetItemRow(
                item = item,
                onToggle = { viewModel.toggleBudgetItemPaid(item) },
                onLongPress = { itemToDelete = item }
            )
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
            Icon(Icons.Filled.Add, contentDescription = s.addBudgetItem, tint = Color.White)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BudgetItemRow(item: BudgetItemEntity, onToggle: () -> Unit, onLongPress: () -> Unit) {
    val s = LocalStrings.current
    val icon = when (item.category.lowercase()) {
        "food" -> Icons.Filled.Restaurant
        "transport" -> Icons.Filled.DirectionsCar
        "bills" -> Icons.Filled.Bolt
        "health" -> Icons.Filled.LocalHospital
        "shopping" -> Icons.Filled.ShoppingBag
        else -> Icons.Filled.Home
    }
    val dueLabel = dueDateLabel(item.dueDate, s)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceDark)
            .combinedClickable(onClick = onToggle, onLongClick = onLongPress)
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = if (item.isPaid) TextSecondary else PrimaryPurpleLight)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.name,
                    color = if (item.isPaid) TextSecondary else TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    textDecoration = if (item.isPaid) TextDecoration.LineThrough else TextDecoration.None
                )
                Text("Rs %,.0f".format(item.amount), color = TextSecondary, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.width(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!item.isPaid && dueLabel != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(DangerRed.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(dueLabel, color = DangerRedSoft, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(10.dp))
            }
            Checkbox(
                checked = item.isPaid,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(checkedColor = PrimaryPurple)
            )
        }
    }
}

private fun dueDateLabel(dueDate: Long, s: Strings): String? {
    val diffDays = ((dueDate - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).toInt()
    return when {
        diffDays < 0 -> s.overdueLabel
        diffDays == 0 -> s.dueTodayLabel
        diffDays == 1 -> s.tomorrowLabel
        diffDays in 2..7 -> s.inDaysFmt.format(diffDays)
        else -> null
    }
}
