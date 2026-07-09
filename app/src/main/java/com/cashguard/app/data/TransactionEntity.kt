package com.cashguard.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TxType { DEBIT, CREDIT }

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val type: TxType,
    val balanceAfter: Double,
    val merchant: String,
    val category: String = "General",
    val timestamp: Long,
    val source: String,          // e.g. "BOC", "Hutch/SLT"
    val rawMessage: String,
    val matchedBudgetItemId: Long? = null
)
