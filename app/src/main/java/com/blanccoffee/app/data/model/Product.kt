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
    val lastUpdated: Long = System.currentTimeMillis()
) {
    val isLowStock: Boolean
        get() = stockQuantity in 1..minStockThreshold

    val isOutOfStock: Boolean
        get() = stockQuantity <= 0

    val profitMargin: Double
        get() = if (sellingPrice > 0) ((sellingPrice - costPrice) / sellingPrice) * 100 else 0.0
}
