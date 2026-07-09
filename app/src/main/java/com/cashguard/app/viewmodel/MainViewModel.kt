package com.cashguard.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cashguard.app.data.AppSettings
import com.cashguard.app.data.BudgetItemEntity
import com.cashguard.app.data.CashGuardRepository
import com.cashguard.app.data.CycleCalculator
import com.cashguard.app.data.TransactionEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(private val repository: CashGuardRepository) : ViewModel() {

    /**
     * Recomputed whenever the configured payday dates change. With no
     * paydays set this is just the calendar month — see [CycleCalculator].
     */
    private val currentCycleFlow = repository.settingsRepository.settingsFlow
        .map { it.payDays }
        .distinctUntilChanged()
        .map { payDays -> CycleCalculator.currentCycle(payDays, System.currentTimeMillis()) }

    val recentTransactions: StateFlow<List<TransactionEntity>> =
        repository.observeRecentTransactions(10)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTransactions: StateFlow<List<TransactionEntity>> =
        repository.observeAllTransactions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalSpentThisMonth: StateFlow<Double> = currentCycleFlow
        .flatMapLatest { cycle -> repository.observeTotalSpent(cycle.startMillis, cycle.endMillis) }
        .map { it ?: 0.0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalIncomeThisMonth: StateFlow<Double> = currentCycleFlow
        .flatMapLatest { cycle -> repository.observeTotalIncome(cycle.startMillis, cycle.endMillis) }
        .map { it ?: 0.0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val budgetItems: StateFlow<List<BudgetItemEntity>> = currentCycleFlow
        .flatMapLatest { cycle -> repository.observeBudgetForCycle(cycle.key) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** "July 2026" for plain calendar months, or "Jul 10 – Aug 9" for payday cycles. */
    val cycleLabel: StateFlow<String> = currentCycleFlow
        .map { cycle -> formatCycleLabel(cycle) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    private fun formatCycleLabel(cycle: CycleCalculator.Cycle): String {
        return if (cycle.key.startsWith("pd-")) {
            val start = SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(cycle.startMillis))
            val end = SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(cycle.endMillis - 1))
            "$start – $end"
        } else {
            SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(cycle.startMillis))
        }
    }

    val settings: StateFlow<AppSettings> =
        repository.settingsRepository.settingsFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    // null until DataStore loads — lets the UI avoid flashing onboarding
    // for users who already completed it.
    val settingsOrNull: StateFlow<AppSettings?> =
        repository.settingsRepository.settingsFlow
            .map<AppSettings, AppSettings?> { it }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setLanguage(code: String) = viewModelScope.launch {
        repository.settingsRepository.setLanguage(code)
    }

    fun completeOnboarding() = viewModelScope.launch {
        repository.settingsRepository.setOnboardingDone()
    }

    fun addBudgetItem(name: String, amount: Double, dueDate: Long, category: String) {
        viewModelScope.launch {
            val payDays = repository.settingsRepository.settingsFlow.first().payDays
            val cycle = CycleCalculator.currentCycle(payDays, System.currentTimeMillis())
            repository.addBudgetItem(
                BudgetItemEntity(
                    name = name,
                    amount = amount,
                    dueDate = dueDate,
                    category = category,
                    cycleMonth = cycle.key
                )
            )
        }
    }

    fun toggleBudgetItemPaid(item: BudgetItemEntity) {
        viewModelScope.launch {
            repository.updateBudgetItem(item.copy(isPaid = !item.isPaid))
        }
    }

    fun deleteBudgetItem(id: Long) {
        viewModelScope.launch { repository.deleteBudgetItem(id) }
    }

    fun setSmsAlerts(enabled: Boolean) = viewModelScope.launch {
        repository.settingsRepository.setSmsAlerts(enabled)
    }

    fun setDailySummary(enabled: Boolean) = viewModelScope.launch {
        repository.settingsRepository.setDailySummary(enabled)
    }

    fun setLowBalanceThreshold(value: Double) = viewModelScope.launch {
        repository.settingsRepository.setLowBalanceThreshold(value)
    }

    fun setPayDays(days: List<Int>) = viewModelScope.launch {
        repository.settingsRepository.setPayDays(days.distinct().sorted())
    }

    fun armPartyGuard(cap: Double) = viewModelScope.launch {
        repository.settingsRepository.armPartyGuard(cap)
    }

    fun disarmPartyGuard() = viewModelScope.launch {
        repository.settingsRepository.disarmPartyGuard()
    }

    fun snoozePartyGuard(durationMs: Long) = viewModelScope.launch {
        repository.settingsRepository.snoozePartyGuardUntil(System.currentTimeMillis() + durationMs)
    }

    fun setVelocityAlerts(enabled: Boolean) = viewModelScope.launch {
        repository.settingsRepository.setVelocityAlerts(enabled)
    }

    fun setOverlayPopup(enabled: Boolean) = viewModelScope.launch {
        repository.settingsRepository.setOverlayPopup(enabled)
    }

    // Simulates receiving a bank alert notification — useful for testing
    // the alert UI without needing a real BOC transaction.
    fun simulateTransaction(
        amount: Double,
        isDebit: Boolean,
        merchant: String,
        balanceAfter: Double,
        source: String = "BOC"
    ) {
        viewModelScope.launch {
            val entity = TransactionEntity(
                amount = amount,
                type = if (isDebit) com.cashguard.app.data.TxType.DEBIT else com.cashguard.app.data.TxType.CREDIT,
                balanceAfter = balanceAfter,
                merchant = merchant,
                timestamp = System.currentTimeMillis(),
                source = source,
                rawMessage = "SIMULATED-${System.currentTimeMillis()}"
            )
            val id = repository.insertTransaction(entity)
            val payDays = repository.settingsRepository.settingsFlow.first().payDays
            val cycle = CycleCalculator.currentCycle(payDays, System.currentTimeMillis())
            repository.tryAutoMatchBudgetItem(entity.copy(id = id), cycle.key)
        }
    }
}

class MainViewModelFactory(private val repository: CashGuardRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MainViewModel(repository) as T
    }
}
