package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Income transactions on the local Room database.
 * Supports full CRUD operations for BLANC COFFEE income tracking.
 */
@Dao
interface IncomeDao {

    // --- CREATE ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIncome(transaction: Transaction): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllIncome(transactions: List<Transaction>)

    // --- READ ---
    @Query("SELECT * FROM transactions WHERE type = 'INCOME' ORDER BY timestamp DESC")
    fun getAllIncome(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE id = :id AND type = 'INCOME' LIMIT 1")
    suspend fun getIncomeById(id: Long): Transaction?

    @Query("SELECT * FROM transactions WHERE type = 'INCOME' AND category = :category ORDER BY timestamp DESC")
    fun getIncomeByCategory(category: String): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE type = 'INCOME' AND timestamp >= :startTimestamp AND timestamp <= :endTimestamp ORDER BY timestamp DESC")
    fun getIncomeBetweenTimestamps(startTimestamp: Long, endTimestamp: Long): Flow<List<Transaction>>

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM transactions WHERE type = 'INCOME'")
    fun getTotalIncomeAmount(): Flow<Double>

    @Query("SELECT COUNT(*) FROM transactions WHERE type = 'INCOME'")
    suspend fun getIncomeCount(): Int

    // --- UPDATE ---
    @Update
    suspend fun updateIncome(transaction: Transaction)

    @Query("UPDATE transactions SET amount = :newAmount, title = :newTitle, note = :newNote WHERE id = :id AND type = 'INCOME'")
    suspend fun updateIncomeDetails(id: Long, newAmount: Double, newTitle: String, newNote: String)

    // --- DELETE ---
    @Delete
    suspend fun deleteIncome(transaction: Transaction)

    @Query("DELETE FROM transactions WHERE id = :id AND type = 'INCOME'")
    suspend fun deleteIncomeById(id: Long)

    @Query("DELETE FROM transactions WHERE type = 'INCOME'")
    suspend fun deleteAllIncome()
}
