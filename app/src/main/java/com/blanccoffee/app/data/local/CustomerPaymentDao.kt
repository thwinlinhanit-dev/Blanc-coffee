package com.blanccoffee.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.blanccoffee.app.data.model.CustomerPayment
import kotlinx.coroutines.flow.Flow

/** Ledger of partial/full cash-ins against UNPAID (tab) orders. */
@Dao
interface CustomerPaymentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: CustomerPayment): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayments(payments: List<CustomerPayment>)

    @Query("SELECT * FROM customer_payments ORDER BY timestamp DESC")
    fun getAllPayments(): Flow<List<CustomerPayment>>

    @Query("SELECT * FROM customer_payments ORDER BY timestamp DESC")
    suspend fun getAllPaymentsOnce(): List<CustomerPayment>

    @Query("SELECT * FROM customer_payments WHERE orderId = :orderId ORDER BY timestamp ASC")
    fun getPaymentsForOrder(orderId: Long): Flow<List<CustomerPayment>>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM customer_payments WHERE orderId = :orderId")
    suspend fun getPaidTotalForOrder(orderId: Long): Double

    @Query("DELETE FROM customer_payments WHERE orderId = :orderId")
    suspend fun deletePaymentsForOrder(orderId: Long)

    @Query("DELETE FROM customer_payments WHERE orderId IN (:orderIds)")
    suspend fun deletePaymentsForOrders(orderIds: List<Long>): Int

    @Query("DELETE FROM customer_payments")
    suspend fun deleteAllPayments()
}
