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
 * Data Access Object for Expense (Outcome) transactions on the local Room database.
 * Supports full CRUD operations for BLANC COFFEE expense management.
 */
@Dao
interface ExpenseDao {

    // --- CREATE ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(transaction: Transaction): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllExpenses(transactions: List<Transaction>)

    // --- READ ---
    @Query("SELECT * FROM transactions WHERE type = 'OUTCOME' ORDER BY timestamp DESC")
    fun getAllExpenses(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE id = :id AND type = 'OUTCOME' LIMIT 1")
    suspend fun getExpenseById(id: Long): Transaction?

    @Query("SELECT * FROM transactions WHERE type = 'OUTCOME' AND category = :category ORDER BY timestamp DESC")
    fun getExpensesByCategory(category: String): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE type = 'OUTCOME' AND timestamp >= :startTimestamp AND timestamp <= :endTimestamp ORDER BY timestamp DESC")
    fun getExpensesBetweenTimestamps(startTimestamp: Long, endTimestamp: Long): Flow<List<Transaction>>

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM transactions WHERE type = 'OUTCOME'")
    fun getTotalExpenseAmount(): Flow<Double>

    @Query("SELECT COUNT(*) FROM transactions WHERE type = 'OUTCOME'")
    suspend fun getExpenseCount(): Int

    // --- UPDATE ---
    @Update
    suspend fun updateExpense(transaction: Transaction)

    @Query("UPDATE transactions SET amount = :newAmount, title = :newTitle, note = :newNote WHERE id = :id AND type = 'OUTCOME'")
    suspend fun updateExpenseDetails(id: Long, newAmount: Double, newTitle: String, newNote: String)

    // --- DELETE ---
    @Delete
    suspend fun deleteExpense(transaction: Transaction)

    @Query("DELETE FROM transactions WHERE id = :id AND type = 'OUTCOME'")
    suspend fun deleteExpenseById(id: Long)

    @Query("DELETE FROM transactions WHERE type = 'OUTCOME'")
    suspend fun deleteAllExpenses()
}
