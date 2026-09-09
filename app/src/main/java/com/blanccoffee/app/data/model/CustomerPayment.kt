package com.blanccoffee.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A partial or full cash-in against a customer's order (credit-tab payment).
 *
 * Orders created UNPAID stay open as tabs; each [recordCustomerPayment] call
 * adds one row here. When the summed payments cover the order's net total
 * (total − discount), the order flips to PAID and a single ORDER_SALE income
 * entry is written — partial payments never touch the finance ledger directly.
 */
@Entity(
    tableName = "customer_payments",
    indices = [
        Index("orderId"),
        Index("timestamp")
    ]
)
data class CustomerPayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    val amount: Double,
    val method: String = "CASH",
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
