package com.blanccoffee.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Raw ingredient / packaging material that is bought in bulk and consumed to
 * make (or sell alongside) finished [Product]s.
 *
 * Example: raw macadamia kernels bought in bags, green coffee beans, matcha
 * powder in kg, kraft pouches in pcs.
 *
 * Stock is kept as [Double] so fractional units (0.5 kg, 2.5 bags) work.
 * Bought / Used totals are derived from [RawMaterialMovement] rows; this
 * table only stores the live [stockQuantity] (remaining).
 */
@Entity(
    tableName = "raw_materials",
    indices = [
        Index("name"),
        Index("sku")
    ]
)
data class RawMaterial(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val unit: String = "bags",
    val stockQuantity: Double = 0.0,
    val minThreshold: Double = 5.0,
    val costPerUnit: Double = 0.0,
    val sku: String = "",
    val note: String = "",
    val lastUpdated: Long = System.currentTimeMillis()
) {
    val isLowStock: Boolean
        get() = stockQuantity > 0 && stockQuantity <= minThreshold

    val isOutOfStock: Boolean
        get() = stockQuantity <= 0
}
