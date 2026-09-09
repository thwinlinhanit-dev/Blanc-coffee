package com.blanccoffee.app.data.model

import androidx.room.Entity
import androidx.room.Index

/**
 * Recipe linkage: how much of a raw material is consumed to make/sell ONE
 * unit of a finished product.
 *
 * Example: 1x "Roasted Salted Macadamias (pouch)" consumes 0.2 bags of
 * "Raw Macadamia Kernels". When an order is created, the repository
 * auto-deducts [quantityPerUnit] * orderedQty from the raw stock and writes
 * a [RawMaterialMovement] of type USAGE (manual "Use" taps write the same
 * kind of row without a linked order).
 */
@Entity(
    tableName = "product_recipes",
    primaryKeys = ["productId", "materialId"],
    indices = [
        Index("materialId")
    ]
)
data class ProductRecipe(
    val productId: Long,
    val materialId: Long,
    val quantityPerUnit: Double = 0.0
)
