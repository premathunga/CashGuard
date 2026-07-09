package com.cashguard.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {

    @Insert
    suspend fun insert(item: BudgetItemEntity): Long

    @Update
    suspend fun update(item: BudgetItemEntity)

    @Query("SELECT * FROM budget_items WHERE cycleMonth = :cycleMonth ORDER BY dueDate ASC")
    fun observeForCycle(cycleMonth: String): Flow<List<BudgetItemEntity>>

    @Query("SELECT * FROM budget_items WHERE isPaid = 0 AND cycleMonth = :cycleMonth")
    suspend fun getUnpaidForCycle(cycleMonth: String): List<BudgetItemEntity>

    @Query("DELETE FROM budget_items WHERE id = :id")
    suspend fun delete(id: Long)
}
