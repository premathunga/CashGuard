package com.cashguard.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashguard.app.ui.i18n.LocalStrings
import com.cashguard.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val categories = listOf("General", "Food", "Transport", "Bills", "Health", "Shopping")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBudgetItemSheet(
    onDismiss: () -> Unit,
    onSave: (name: String, amount: Double, dueDate: Long, category: String) -> Unit
) {
    val s = LocalStrings.current
    var name by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(categories.first()) }
    var dueDate by remember { mutableStateOf(Calendar.getInstance().timeInMillis) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dueDate)
    var showDatePicker by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceDark
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).padding(bottom = 24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(s.addBudgetItem, color = PrimaryPurpleLight, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text(s.trackUpcoming, color = TextSecondary, fontSize = 13.sp)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextSecondary)
                }
            }

            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(s.itemName) },
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors()
            )

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text(s.amountLabel) },
                prefix = { Text("Rs.") },
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors()
            )

            Spacer(Modifier.height(16.dp))
            Text(s.dueDate, color = TextSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = SimpleDateFormat("MM/dd/yyyy", Locale.US).format(java.util.Date(dueDate)),
                onValueChange = {},
                readOnly = true,
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Filled.DateRange, contentDescription = "Pick date", tint = PrimaryPurpleLight)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors()
            )

            if (showDatePicker) {
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            datePickerState.selectedDateMillis?.let { dueDate = it }
                            showDatePicker = false
                        }) { Text(s.ok) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) { Text(s.cancel) }
                    }
                ) {
                    DatePicker(state = datePickerState)
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(s.categoryLabel, color = TextSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            LazyRowCategories(selected = category, onSelect = { category = it })

            Spacer(Modifier.height(28.dp))
            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull() ?: return@Button
                    if (name.isBlank() || amount <= 0.0) return@Button
                    onSave(name.trim(), amount, dueDate, category)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
            ) {
                Text(s.saveBudgetItem, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Filled.CheckCircle, contentDescription = null)
            }
        }
    }
}

@Composable
private fun LazyRowCategories(selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categories.take(4).forEach { cat ->
            val isSelected = cat == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isSelected) PrimaryPurple.copy(alpha = 0.25f) else Color.Transparent)
                    .border(
                        width = 1.dp,
                        color = if (isSelected) PrimaryPurpleLight else DividerColor,
                        shape = RoundedCornerShape(20.dp)
                    )
                    .clickable { onSelect(cat) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(cat, color = if (isSelected) PrimaryPurpleLight else TextSecondary, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = PrimaryPurple,
    unfocusedBorderColor = DividerColor,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedLabelColor = PrimaryPurpleLight,
    unfocusedLabelColor = TextSecondary,
    cursorColor = PrimaryPurple
)
