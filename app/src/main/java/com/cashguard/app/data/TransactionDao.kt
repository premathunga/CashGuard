package com.cashguard.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE timestamp >= :start AND timestamp < :end ORDER BY timestamp DESC")
    fun observeForRange(start: Long, end: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatest(): TransactionEntity?

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'DEBIT' AND timestamp >= :start AND timestamp < :end")
    fun observeTotalSpent(start: Long, end: Long): Flow<Double?>

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'CREDIT' AND timestamp >= :start AND timestamp < :end")
    fun observeTotalIncome(start: Long, end: Long): Flow<Double?>

    @Query("SELECT * FROM transactions WHERE rawMessage = :rawMessage LIMIT 1")
    suspend fun findByRawMessage(rawMessage: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE type = 'DEBIT' AND timestamp >= :since ORDER BY timestamp DESC")
    suspend fun getDebitsSince(since: Long): List<TransactionEntity>
}
