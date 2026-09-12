package com.blanccoffee.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TransactionType(val displayName: String) {
    INCOME("Income"),
    OUTCOME("Expense")
}

enum class TransactionCategory(val displayName: String, val type: TransactionType) {
    // Income categories
    ORDER_SALE("Customer Order", TransactionType.INCOME),
    /**
     * System-generated compensating refund entry (negative income) created when a
     * PAID order is cancelled. Not selectable when manually adding/editing entries.
     */
    ORDER_REFUND("Order Refund", TransactionType.INCOME),
    CATERING("Catering / Event", TransactionType.INCOME),
    WHOLESALE("Wholesale Sale", TransactionType.INCOME),
    OTHER_INCOME("Other Income", TransactionType.INCOME),

    // Outcome categories
    RESTOCKING("Inventory Restock", TransactionType.OUTCOME),
    PACKAGING("Packaging Supplies", TransactionType.OUTCOME),
    UTILITIES("Utilities & Bills", TransactionType.OUTCOME),
    EQUIPMENT("Roasting & Brewing Gear", TransactionType.OUTCOME),
    LOGISTICS("Shipping & Delivery", TransactionType.OUTCOME),
    MARKETING("Marketing & Promo", TransactionType.OUTCOME),
    RENT("Store Rent", TransactionType.OUTCOME),
    OTHER_OUTCOME("Other Expense", TransactionType.OUTCOME);

    companion object {
        fun fromString(value: String): TransactionCategory {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: OTHER_INCOME
        }
    }
}

@Entity(
    tableName = "transactions",
    indices = [
        Index("type"),
        Index("category"),
        Index("timestamp")
    ]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // INCOME or OUTCOME
    val category: String,
    val amount: Double,
    val title: String,
    val note: String = "",
    val referenceOrderId: Long? = null,
    /** Demo-seeder flag — see [Product.isSeed]. Never true for real ledger rows. */
    val isSeed: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
