package com.cashguard.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cashguard.app.notification.BankMessageParser
import com.cashguard.app.ui.i18n.LocalStrings
import com.cashguard.app.ui.i18n.SUPPORTED_LANGUAGES
import com.cashguard.app.ui.theme.*
import com.cashguard.app.viewmodel.MainViewModel

@Composable
fun SettingsScreen(viewModel: MainViewModel, onRequestNotificationAccess: () -> Unit) {
    val s = LocalStrings.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var showBankList by remember { mutableStateOf(false) }
    var showAddPayday by remember { mutableStateOf(false) }

    if (showBankList) {
        SupportedBanksDialog(onDismiss = { showBankList = false })
    }

    if (showAddPayday) {
        PaydayPickerDialog(
            onConfirm = { day ->
                viewModel.setPayDays(settings.payDays + day)
                showAddPayday = false
            },
            onDismiss = { showAddPayday = false }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp)
    ) {
        item {
            Text(s.settingsTitle, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 26.sp)
            Text(s.settingsSubtitle, color = TextSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(24.dp))

            SectionLabel(s.languageSection)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(SurfaceDark)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SUPPORTED_LANGUAGES.forEach { lang ->
                    val selected = settings.language == lang.code
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) PrimaryPurple else SurfaceLight)
                            .clickable { viewModel.setLanguage(lang.code) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            lang.nativeName,
                            color = if (selected) Color.White else TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel(s.alertsSection)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(SurfaceDark)
            ) {
                SettingsToggleRow(
                    icon = Icons.Filled.Sms,
                    title = s.smsAlerts,
                    subtitle = s.smsAlertsDesc,
                    checked = settings.smsAlertsEnabled,
                    onCheckedChange = { viewModel.setSmsAlerts(it) }
                )
                HorizontalDivider(color = DividerColor)
                SettingsToggleRow(
                    icon = Icons.Filled.Description,
                    title = s.dailySummary,
                    subtitle = s.dailySummaryDesc,
                    checked = settings.dailySummaryEnabled,
                    onCheckedChange = { viewModel.setDailySummary(it) }
                )
                HorizontalDivider(color = DividerColor)
                SettingsToggleRow(
                    icon = Icons.Filled.Speed,
                    title = s.nightWatch,
                    subtitle = s.nightWatchDesc,
                    checked = settings.velocityAlertsEnabled,
                    onCheckedChange = { viewModel.setVelocityAlerts(it) }
                )
                HorizontalDivider(color = DividerColor)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onRequestNotificationAccess() }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(s.notifAccess, color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text(s.notifAccessDesc, color = TextSecondary, fontSize = 12.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextSecondary)
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel(s.popupSection)
            val context = LocalContext.current
            val overlayGranted = AndroidSettings.canDrawOverlays(context)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(SurfaceDark)
            ) {
                SettingsToggleRow(
                    icon = Icons.Filled.PictureInPicture,
                    title = s.popupToggle,
                    subtitle = s.popupToggleDesc,
                    checked = settings.overlayPopupEnabled,
                    onCheckedChange = { viewModel.setOverlayPopup(it) }
                )
                HorizontalDivider(color = DividerColor)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            context.startActivity(
                                Intent(
                                    AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(s.displayOverApps, color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text(
                            if (overlayGranted) s.grantedTick else s.tapToGrant,
                            color = if (overlayGranted) SuccessGreen else DangerRedSoft,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextSecondary)
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel(s.thresholdsSection)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(SurfaceDark)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        s.lowBalanceThreshold, color = TextPrimary, fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Rs %,.0f".format(settings.lowBalanceThreshold), color = SuccessGreen,
                        fontWeight = FontWeight.SemiBold, fontSize = 14.sp
                    )
                }
                Slider(
                    value = settings.lowBalanceThreshold.toFloat(),
                    onValueChange = { viewModel.setLowBalanceThreshold(it.toDouble()) },
                    valueRange = 1000f..50000f,
                    colors = SliderDefaults.colors(thumbColor = PrimaryPurpleLight, activeTrackColor = PrimaryPurple)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("RS 1K", color = TextSecondary, fontSize = 11.sp)
                    Text("RS 50K", color = TextSecondary, fontSize = 11.sp)
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel(s.banksSection)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(SurfaceDark)
            ) {
                BankRow(
                    letter = "🏦",
                    name = s.allBanksAuto,
                    subtitle = s.banksSupportedFmt.format(BankMessageParser.SUPPORTED_BANKS.size)
                ) { showBankList = true }
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel(s.paydaySection)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(SurfaceDark)
                    .padding(16.dp)
            ) {
                Text(s.paydayDesc, color = TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    settings.payDays.forEach { day ->
                        PaydayChip(
                            day = day,
                            contentDescription = s.removePayday,
                            onRemove = { viewModel.setPayDays(settings.payDays - day) }
                        )
                    }
                    if (settings.payDays.size < MAX_PAYDAYS) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.dp, DividerColor, RoundedCornerShape(14.dp))
                                .clickable { showAddPayday = true }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text(s.addPayday, color = PrimaryPurpleLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                if (settings.payDays.isEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(s.noPaydaysHint, color = TextSecondary, fontSize = 11.sp)
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun SettingsToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(icon, contentDescription = null, tint = TextSecondary)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text(subtitle, color = TextSecondary, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PrimaryPurple)
        )
    }
}

@Composable
private fun BankRow(letter: String, name: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(PrimaryPurple.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(letter, color = PrimaryPurpleLight, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(subtitle, color = TextSecondary, fontSize = 11.sp)
            }
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextSecondary)
    }
}

@Composable
private fun SupportedBanksDialog(onDismiss: () -> Unit) {
    val s = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = {
            Column {
                Text(s.supportedBanksTitle, color = TextPrimary, fontWeight = FontWeight.Bold)
                Text(
                    s.supportedBanksDesc,
                    color = TextSecondary, fontSize = 12.sp
                )
            }
        },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                BankMessageParser.BankCategory.entries.forEach { category ->
                    val banks = BankMessageParser.SUPPORTED_BANKS
                        .filter { it.category == category }
                        .sortedBy { it.name }
                    if (banks.isEmpty()) return@forEach
                    item(key = category.name) {
                        Text(
                            category.label.uppercase(),
                            color = PrimaryPurpleLight,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )
                    }
                    items(banks.size, key = { banks[it].name }) { index ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("✓", color = SuccessGreen, fontSize = 13.sp)
                            Spacer(Modifier.width(10.dp))
                            Text(banks[index].name, color = TextPrimary, fontSize = 14.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(s.close, color = PrimaryPurpleLight) }
        }
    )
}

private const val MAX_PAYDAYS = 6

private fun dayWithSuffix(day: Int): String {
    val suffix = when {
        day in 11..13 -> "th"
        day % 10 == 1 -> "st"
        day % 10 == 2 -> "nd"
        day % 10 == 3 -> "rd"
        else -> "th"
    }
    return "$day$suffix"
}

@Composable
private fun PaydayChip(day: Int, contentDescription: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(PrimaryPurple.copy(alpha = 0.2f))
            .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.CalendarMonth, contentDescription = null,
            tint = PrimaryPurpleLight, modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(dayWithSuffix(day), color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Spacer(Modifier.width(4.dp))
        Icon(
            Icons.Filled.Close,
            contentDescription = contentDescription,
            tint = TextSecondary,
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .clickable { onRemove() }
                .padding(2.dp)
        )
    }
}

@Composable
private fun PaydayPickerDialog(onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    var day by remember { mutableIntStateOf(1) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = { Text(s.addPaydayTitle, color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(s.addPaydayDesc, color = TextSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = day.toFloat(),
                    onValueChange = { day = it.toInt().coerceIn(1, 31) },
                    valueRange = 1f..31f,
                    steps = 29,
                    colors = SliderDefaults.colors(
                        thumbColor = PrimaryPurpleLight,
                        activeTrackColor = PrimaryPurple
                    )
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("1", color = TextSecondary, fontSize = 11.sp)
                    Text(
                        s.dayWithSuffixArriveFmt.format(dayWithSuffix(day)),
                        color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp
                    )
                    Text("31", color = TextSecondary, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(day) }) { Text(s.save, color = PrimaryPurpleLight) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(s.cancel, color = TextSecondary) }
        }
    )
}
