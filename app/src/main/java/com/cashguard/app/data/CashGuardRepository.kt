package com.cashguard.app.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class CashGuardRepository(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val transactionDao = db.transactionDao()
    private val budgetDao = db.budgetDao()
    val settingsRepository = SettingsRepository(context)

    // Transactions
    fun observeRecentTransactions(limit: Int = 10): Flow<List<TransactionEntity>> =
        transactionDao.observeRecent(limit)

    fun observeAllTransactions(): Flow<List<TransactionEntity>> =
        transactionDao.observeAll()

    fun observeTransactionsForRange(start: Long, end: Long): Flow<List<TransactionEntity>> =
        transactionDao.observeForRange(start, end)

    fun observeTotalSpent(start: Long, end: Long): Flow<Double?> =
        transactionDao.observeTotalSpent(start, end)

    fun observeTotalIncome(start: Long, end: Long): Flow<Double?> =
        transactionDao.observeTotalIncome(start, end)

    suspend fun getLatestTransaction(): TransactionEntity? = transactionDao.getLatest()

    suspend fun getDebitsSince(since: Long): List<TransactionEntity> =
        transactionDao.getDebitsSince(since)

    suspend fun insertTransaction(transaction: TransactionEntity): Long {
        // De-duplicate identical notifications (same raw message)
        val existing = transactionDao.findByRawMessage(transaction.rawMessage)
        if (existing != null) return existing.id
        return transactionDao.insert(transaction)
    }

    suspend fun markTransactionMatched(transaction: TransactionEntity, budgetItemId: Long) {
        transactionDao.update(transaction.copy(matchedBudgetItemId = budgetItemId))
    }

    // Budget
    fun observeBudgetForCycle(cycleMonth: String): Flow<List<BudgetItemEntity>> =
        budgetDao.observeForCycle(cycleMonth)

    suspend fun addBudgetItem(item: BudgetItemEntity): Long = budgetDao.insert(item)

    suspend fun updateBudgetItem(item: BudgetItemEntity) = budgetDao.update(item)

    suspend fun deleteBudgetItem(id: Long) = budgetDao.delete(id)

    suspend fun getUnpaidBudgetItems(cycleMonth: String): List<BudgetItemEntity> =
        budgetDao.getUnpaidForCycle(cycleMonth)

    /**
     * Called whenever a new DEBIT transaction is inserted.
     * Tries to auto-match it against an unpaid budget item this cycle
     * (same category keyword found in merchant/rawMessage, and similar amount).
     */
    suspend fun tryAutoMatchBudgetItem(transaction: TransactionEntity, cycleMonth: String) {
        if (transaction.type != TxType.DEBIT) return
        val unpaid = getUnpaidBudgetItems(cycleMonth)
        val match = unpaid.firstOrNull { item ->
            val amountClose = kotlin.math.abs(item.amount - transaction.amount) < 1.0
            val nameMatch = transaction.merchant.contains(item.name, ignoreCase = true) ||
                transaction.rawMessage.contains(item.name, ignoreCase = true)
            amountClose || nameMatch
        }
        if (match != null) {
            updateBudgetItem(match.copy(isPaid = true, matchedTransactionId = transaction.id))
            markTransactionMatched(transaction, match.id)
        }
    }
}
