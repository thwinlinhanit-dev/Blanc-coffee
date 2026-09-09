package com.blanccoffee.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Movement type for the raw-material ledger.
 * - PURCHASE: buying stock in (increases remaining). Records date, qty, cost.
 * - USAGE: consuming stock — manual "Use" taps or automatic deduction when a
 *   finished product with a [ProductRecipe] is sold.
 * - ADJUST: manual correction to an absolute level (spillage, recount).
 */
enum class RawMovementType {
    PURCHASE,
    USAGE,
    ADJUST
}

@Entity(
    tableName = "raw_movements",
    indices = [
        Index("materialId"),
        Index("timestamp"),
        Index("type")
    ]
)
data class RawMaterialMovement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val materialId: Long,
    val type: String = RawMovementType.PURCHASE.name,
    /** Always a positive amount in the material's unit. */
    val quantity: Double = 0.0,
    val totalCost: Double = 0.0,
    val note: String = "",
    /** Set when this usage was auto-created by selling a product. */
    val linkedOrderId: Long? = null,
    val timestamp: Long = System.currentTimeMillis()
)
