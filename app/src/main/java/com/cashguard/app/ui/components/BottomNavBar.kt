package com.cashguard.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import com.cashguard.app.ui.i18n.LocalStrings
import com.cashguard.app.ui.theme.PrimaryPurple
import com.cashguard.app.ui.theme.SurfaceDark
import com.cashguard.app.ui.theme.TextSecondary

enum class NavTab { Dashboard, History, Budget, Settings }

@Composable
fun BottomNavBar(selected: NavTab, onSelect: (NavTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark)
            .navigationBarsPadding()
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val s = LocalStrings.current
        NavTab.entries.forEach { tab ->
            val isSelected = tab == selected
            val icon = when (tab) {
                NavTab.Dashboard -> Icons.Filled.GridView
                NavTab.History -> Icons.Filled.Receipt
                NavTab.Budget -> Icons.Filled.CreditCard
                NavTab.Settings -> Icons.Filled.Settings
            }
            val label = when (tab) {
                NavTab.Dashboard -> s.navDashboard
                NavTab.History -> s.navHistory
                NavTab.Budget -> s.navBudget
                NavTab.Settings -> s.navSettings
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isSelected) PrimaryPurple else Color.Transparent)
                    .clickable { onSelect(tab) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isSelected) Color.White else TextSecondary
                )
                Text(
                    text = label,
                    color = if (isSelected) Color.White else TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}
