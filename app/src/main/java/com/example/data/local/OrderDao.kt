package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.model.CustomerOrder
import com.example.data.model.OrderItem
import com.example.data.model.OrderWithItems
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {
    @Transaction
    @Query("SELECT * FROM orders ORDER BY createdAt DESC")
    fun getAllOrdersWithItems(): Flow<List<OrderWithItems>>

    @Transaction
    @Query("SELECT * FROM orders WHERE status = :status ORDER BY createdAt DESC")
    fun getOrdersByStatus(status: String): Flow<List<OrderWithItems>>

    @Transaction
    @Query("SELECT * FROM orders WHERE id = :orderId LIMIT 1")
    suspend fun getOrderWithItemsById(orderId: Long): OrderWithItems?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: CustomerOrder): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrderItems(items: List<OrderItem>)

    @Update
    suspend fun updateOrder(order: CustomerOrder)

    @Query("UPDATE orders SET status = :status, completedAt = :completedAt WHERE id = :orderId")
    suspend fun updateOrderStatus(orderId: Long, status: String, completedAt: Long?)

    @Query("UPDATE orders SET paymentStatus = :paymentStatus WHERE id = :orderId")
    suspend fun updatePaymentStatus(orderId: Long, paymentStatus: String)

    @Delete
    suspend fun deleteOrder(order: CustomerOrder)

    @Query("DELETE FROM order_items WHERE orderId = :orderId")
    suspend fun deleteOrderItems(orderId: Long)

    @Query("SELECT COUNT(*) FROM orders")
    suspend fun getOrderCount(): Int

    @Query("DELETE FROM orders")
    suspend fun deleteAllOrders()

    @Query("DELETE FROM order_items")
    suspend fun deleteAllOrderItems()
}
