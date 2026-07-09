package com.cashguard.app.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.cashguard.app.ui.components.AddBudgetItemSheet
import com.cashguard.app.ui.components.BottomNavBar
import com.cashguard.app.ui.components.NavTab
import com.cashguard.app.ui.components.TransactionAlertDialog
import com.cashguard.app.ui.screens.BudgetScreen
import com.cashguard.app.ui.screens.DashboardScreen
import com.cashguard.app.ui.screens.HistoryScreen
import com.cashguard.app.ui.screens.SettingsScreen
import com.cashguard.app.viewmodel.MainViewModel

@Composable
fun CashGuardNavGraph(viewModel: MainViewModel, onRequestNotificationAccess: () -> Unit) {
    var selectedTab by remember { mutableStateOf(NavTab.Dashboard) }
    var showAddSheet by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                NavTab.Dashboard -> DashboardScreen(
                    viewModel = viewModel,
                    onAddClick = { showAddSheet = true },
                    onViewAll = { selectedTab = NavTab.History },
                    onAdjustLimits = { selectedTab = NavTab.Budget }
                )
                NavTab.History -> HistoryScreen(viewModel = viewModel)
                NavTab.Budget -> BudgetScreen(viewModel = viewModel, onAddClick = { showAddSheet = true })
                NavTab.Settings -> SettingsScreen(
                    viewModel = viewModel,
                    onRequestNotificationAccess = onRequestNotificationAccess
                )
            }
        }
        BottomNavBar(selected = selectedTab, onSelect = { selectedTab = it })
    }

    if (showAddSheet) {
        AddBudgetItemSheet(
            onDismiss = { showAddSheet = false },
            onSave = { name, amount, dueDate, category ->
                viewModel.addBudgetItem(name, amount, dueDate, category)
            }
        )
    }
}
