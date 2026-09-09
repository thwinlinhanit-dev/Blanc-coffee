package com.blanccoffee.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.blanccoffee.app.data.model.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE type = :type ORDER BY timestamp DESC")
    fun getTransactionsByType(type: String): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE category = :category ORDER BY timestamp DESC")
    fun getTransactionsByCategory(category: String): Flow<List<Transaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<Transaction>)

    @Update
    suspend fun updateTransaction(transaction: Transaction)

    @Delete
    suspend fun deleteTransaction(transaction: Transaction)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Long)

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun getTransactionCount(): Int

    /**
     * Number of *positive* income transactions linked to an order via [Transaction.referenceOrderId].
     * Refunds are stored as negative income rows with the same referenceOrderId and are therefore
     * not counted. Used to guarantee income is only ever recorded once per order.
     */
    @Query("SELECT COUNT(*) FROM transactions WHERE referenceOrderId = :orderId AND type = 'INCOME' AND amount > 0")
    suspend fun countPositiveIncomeForOrder(orderId: Long): Int

    /** All ledger entries (income + refunds) linked to an order, oldest first. */
    @Query("SELECT * FROM transactions WHERE referenceOrderId = :orderId ORDER BY timestamp ASC")
    suspend fun getTransactionsForOrder(orderId: Long): List<Transaction>

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()
}
