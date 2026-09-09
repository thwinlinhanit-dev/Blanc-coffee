package com.blanccoffee.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "products",
    indices = [
        Index("category"),
        Index("sku")
    ]
)
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String, // COFFEE, GREEN_TEA, MACADAMIA_NUT
    val stockQuantity: Int,
    val unit: String = "units",
    val costPrice: Double,
    val sellingPrice: Double,
    val minStockThreshold: Int = 5,
    val sku: String = "",
    val description: String = "",
    /** Epoch millis of expiry, or null when the product doesn't expire. */
    val expiryDate: Long? = null,
    /**
     * True when this product is produced fresh per order (made-to-order).
     * Made-to-order products skip finished-stock checks, deduction and restore —
     * selling them never blocks and never touches [stockQuantity]. Raw ingredients
     * linked through recipes are still auto-deducted (the real constraint).
     */
    val madeToOrder: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    val isLowStock: Boolean
        get() = !madeToOrder && stockQuantity in 1..minStockThreshold

    val isOutOfStock: Boolean
        get() = !madeToOrder && stockQuantity <= 0

    val profitMargin: Double
        get() = if (sellingPrice > 0) ((sellingPrice - costPrice) / sellingPrice) * 100 else 0.0
}
