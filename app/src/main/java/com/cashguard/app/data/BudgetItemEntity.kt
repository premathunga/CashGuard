package com.cashguard.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "budget_items")
data class BudgetItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amount: Double,
    val dueDate: Long,           // epoch millis
    val category: String = "General",
    val isPaid: Boolean = false,
    val cycleMonth: String,      // e.g. "2026-07"
    val matchedTransactionId: Long? = null
)
