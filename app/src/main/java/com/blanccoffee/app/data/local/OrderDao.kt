package com.blanccoffee.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.blanccoffee.app.data.model.CustomerOrder
import com.blanccoffee.app.data.model.OrderItem
import com.blanccoffee.app.data.model.OrderWithItems
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

    /**
     * Highest numeric suffix across existing "ORD-<n>" order numbers.
     * Used by the repository to generate collision-free order numbers
     * (more robust than counting rows, which breaks after deletions).
     */
    @Query("SELECT MAX(CAST(SUBSTR(orderNumber, 5) AS INTEGER)) FROM orders WHERE orderNumber LIKE 'ORD-%'")
    suspend fun getMaxOrderNumber(): Int?

    /** Looks up an order by its business order number (e.g. "ORD-1001"), or null. */
    @Query("SELECT * FROM orders WHERE orderNumber = :orderNumber LIMIT 1")
    suspend fun getOrderByNumber(orderNumber: String): CustomerOrder?

    @Query("DELETE FROM orders")
    suspend fun deleteAllOrders()

    @Query("DELETE FROM order_items")
    suspend fun deleteAllOrderItems()

    @Query("SELECT * FROM orders ORDER BY id ASC")
    suspend fun getAllOrdersOnce(): List<CustomerOrder>

    @Query("SELECT * FROM order_items ORDER BY id ASC")
    suspend fun getAllOrderItemsOnce(): List<OrderItem>

    @Query("SELECT * FROM order_items WHERE orderId = :orderId ORDER BY id ASC")
    suspend fun getItemsForOrderOnce(orderId: Long): List<OrderItem>

    /** Ids of demo-seeded orders: flagged rows plus legacy ORD-1001..1003 numbers. */
    @Query("SELECT id FROM orders WHERE isSeed = 1 OR orderNumber IN (:seedNumbers)")
    suspend fun findSeedOrderIds(seedNumbers: List<String>): List<Long>

    @Query("DELETE FROM order_items WHERE orderId IN (:orderIds)")
    suspend fun deleteItemsForOrders(orderIds: List<Long>): Int

    @Query("DELETE FROM orders WHERE id IN (:ids)")
    suspend fun deleteOrdersByIds(ids: List<Long>): Int
}
