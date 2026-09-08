package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Product
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Inventory operations on the local Room database.
 * Supports complete CRUD and stock management operations for BLANC COFFEE.
 */
@Dao
interface InventoryDao {

    // --- CREATE ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInventoryItem(product: Product): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInventoryItems(products: List<Product>)

    // --- READ ---
    @Query("SELECT * FROM products ORDER BY category ASC, name ASC")
    fun getAllInventory(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getInventoryById(id: Long): Product?

    @Query("SELECT * FROM products WHERE category = :category ORDER BY name ASC")
    fun getInventoryByCategory(category: String): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE stockQuantity <= minStockThreshold ORDER BY stockQuantity ASC")
    fun getLowStockInventory(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE stockQuantity = 0")
    fun getOutOfStockInventory(): Flow<List<Product>>

    @Query("SELECT COUNT(*) FROM products")
    suspend fun getTotalInventoryCount(): Int

    @Query("SELECT COUNT(*) FROM products WHERE stockQuantity <= minStockThreshold")
    fun getLowStockCount(): Flow<Int>

    // --- UPDATE ---
    @Update
    suspend fun updateInventoryItem(product: Product)

    @Query("UPDATE products SET stockQuantity = :newStock, lastUpdated = :timestamp WHERE id = :productId")
    suspend fun updateStockQuantity(productId: Long, newStock: Int, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE products SET stockQuantity = stockQuantity + :addedQuantity, lastUpdated = :timestamp WHERE id = :productId")
    suspend fun restockProduct(productId: Long, addedQuantity: Int, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE products SET stockQuantity = CASE WHEN (stockQuantity - :quantity) < 0 THEN 0 ELSE (stockQuantity - :quantity) END, lastUpdated = :timestamp WHERE id = :productId")
    suspend fun deductStock(productId: Long, quantity: Int, timestamp: Long = System.currentTimeMillis())

    // --- DELETE ---
    @Delete
    suspend fun deleteInventoryItem(product: Product)

    @Query("DELETE FROM products WHERE id = :productId")
    suspend fun deleteInventoryById(productId: Long)

    @Query("DELETE FROM products")
    suspend fun clearAllInventory()
}
