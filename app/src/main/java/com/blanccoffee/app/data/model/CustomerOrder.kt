package com.blanccoffee.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class OrderStatus(val displayName: String) {
    PENDING("Pending"),
    PREPARING("Preparing"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled")
}

enum class PaymentStatus(val displayName: String) {
    PAID("Paid"),
    UNPAID("Unpaid"),
    REFUNDED("Refunded")
}

enum class PaymentMethod(val displayName: String) {
    CASH("Cash"),
    CARD("Credit/Debit Card"),
    MOBILE_PAY("Mobile Pay / QR"),
    TRANSFER("Bank Transfer")
}

@Entity(
    tableName = "orders",
    indices = [
        Index("orderNumber", unique = true),
        Index("status"),
        Index("createdAt")
    ]
)
data class CustomerOrder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderNumber: String,
    val customerName: String,
    val customerPhone: String,
    val customerNote: String = "",
    val status: String = OrderStatus.PENDING.name,
    val paymentStatus: String = PaymentStatus.PAID.name,
    val paymentMethod: String = PaymentMethod.CASH.name,
    val totalAmount: Double,
    val totalCost: Double = 0.0,
    /** Flat discount (MMK) granted at sale time; income is recorded net of this. */
    val discountAmount: Double = 0.0,
    val discountReason: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    /** Demo-seeder flag — see [Product.isSeed]. Never true for real shop orders. */
    val isSeed: Boolean = false
) {
    /** What the customer actually owes for this order (never negative). */
    val netAmount: Double
        get() = (totalAmount - discountAmount).coerceAtLeast(0.0)
}
