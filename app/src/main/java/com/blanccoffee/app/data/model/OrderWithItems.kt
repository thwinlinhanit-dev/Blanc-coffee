package com.blanccoffee.app.data.model

import androidx.room.Embedded
import androidx.room.Relation

data class OrderWithItems(
    @Embedded val order: CustomerOrder,
    @Relation(
        parentColumn = "id",
        entityColumn = "orderId"
    )
    val items: List<OrderItem>
)
