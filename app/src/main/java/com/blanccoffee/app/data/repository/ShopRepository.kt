package com.blanccoffee.app.data.repository

import com.blanccoffee.app.data.local.AppDatabase
import com.blanccoffee.app.data.local.CustomerPaymentDao
import com.blanccoffee.app.data.local.InventoryDao
import com.blanccoffee.app.data.local.OrderDao
import com.blanccoffee.app.data.local.ProductDao
import com.blanccoffee.app.data.local.RawMaterialDao
import com.blanccoffee.app.data.local.TransactionDao
import com.blanccoffee.app.data.model.CustomerOrder
import com.blanccoffee.app.data.model.CustomerPayment
import com.blanccoffee.app.data.model.OrderItem
import com.blanccoffee.app.data.model.OrderStatus
import com.blanccoffee.app.data.model.OrderWithItems
import com.blanccoffee.app.data.model.PaymentMethod
import com.blanccoffee.app.data.model.PaymentStatus
import com.blanccoffee.app.data.model.Product
import com.blanccoffee.app.data.model.ProductCategory
import com.blanccoffee.app.data.model.ProductRecipe
import com.blanccoffee.app.data.model.RawMaterial
import com.blanccoffee.app.data.model.RawMaterialMovement
import com.blanccoffee.app.data.model.RawMovementType
import com.blanccoffee.app.data.model.toShareText
import com.blanccoffee.app.data.model.Transaction
import com.blanccoffee.app.data.model.TransactionCategory
import com.blanccoffee.app.data.model.TransactionType
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Single source of truth for all BLANC COFFEE business operations.
 *
 * Receives the real Room DAOs through its constructor (no anonymous/fake DAO adapters)
 * and runs every mutating operation on [Dispatchers.IO], with multi-table writes wrapped
 * in Room transactions so the inventory, order and finance ledgers can never drift apart.
 */
class ShopRepository(
    private val database: AppDatabase,
    private val productDao: ProductDao,
    private val inventoryDao: InventoryDao,
    private val orderDao: OrderDao,
    private val transactionDao: TransactionDao,
    private val rawMaterialDao: RawMaterialDao,
    private val customerPaymentDao: CustomerPaymentDao
) {
    // Reactive streams from DAOs
    val allProducts: Flow<List<Product>> = productDao.getAllProducts()
    val lowStockProducts: Flow<List<Product>> = inventoryDao.getLowStockInventory()
    val allOrders: Flow<List<OrderWithItems>> = orderDao.getAllOrdersWithItems()
    val allTransactions: Flow<List<Transaction>> = transactionDao.getAllTransactions()
    val allIncome: Flow<List<Transaction>> = transactionDao.getTransactionsByType(TransactionType.INCOME.name)
    val allExpenses: Flow<List<Transaction>> = transactionDao.getTransactionsByType(TransactionType.OUTCOME.name)
    // Raw-ingredient stock streams (buy / use / remaining)
    val allRawMaterials: Flow<List<RawMaterial>> = rawMaterialDao.getAllRawMaterials()
    val lowRawMaterials: Flow<List<RawMaterial>> = rawMaterialDao.getLowRawMaterials()
    val allRawMovements: Flow<List<RawMaterialMovement>> = rawMaterialDao.getAllMovements()
    val allRecipes: Flow<List<ProductRecipe>> = rawMaterialDao.getAllRecipes()
    // Credit-tab payment stream
    val allPayments: Flow<List<CustomerPayment>> = customerPaymentDao.getAllPayments()

    /** The database handle used to run atomic multi-table transactions. */

    fun getProductsByCategory(category: String): Flow<List<Product>> =
        productDao.getProductsByCategory(category)

    fun getOrdersByStatus(status: String): Flow<List<OrderWithItems>> =
        orderDao.getOrdersByStatus(status)

    fun getTransactionsByType(type: String): Flow<List<Transaction>> =
        transactionDao.getTransactionsByType(type)

    /**
     * Inserts a new product, or updates the existing one when [Product.id] is set.
     * All work runs on [Dispatchers.IO].
     *
     * @return the row id of the inserted (or existing) product.
     */
    suspend fun saveProduct(product: Product): Long = withContext(Dispatchers.IO) {
        if (product.id == 0L) {
            productDao.insertProduct(product)
        } else {
            productDao.updateProduct(product)
            product.id
        }
    }

    /**
     * Manually sets the absolute stock level of a product (quick stock adjuster).
     * Early-returns if the product no longer exists.
     */
    suspend fun updateStock(productId: Long, newStock: Int) = withContext(Dispatchers.IO) {
        if (productDao.getProductById(productId) == null) return@withContext
        productDao.updateStock(productId, newStock)
    }

    /**
     * Adds inventory to a product and records a RESTOCKING expense transaction.
     *
     * @param additionalQuantity must be > 0, otherwise the call is ignored.
     * @param totalCost must be >= 0, otherwise the call is ignored.
     */
    suspend fun restockProduct(productId: Long, additionalQuantity: Int, totalCost: Double, note: String = "") = withContext(Dispatchers.IO) {
        if (additionalQuantity <= 0 || totalCost < 0) return@withContext
        val product = productDao.getProductById(productId) ?: return@withContext
        val newStock = product.stockQuantity + additionalQuantity
        productDao.updateStock(productId, newStock)

        // Record restock outcome expense
        val expense = Transaction(
            type = TransactionType.OUTCOME.name,
            category = TransactionCategory.RESTOCKING.name,
            amount = totalCost,
            title = "Restock: ${product.name} (+$additionalQuantity ${product.unit})",
            note = note.ifBlank { "Added $additionalQuantity ${product.unit} to inventory" },
            timestamp = System.currentTimeMillis()
        )
        transactionDao.insertTransaction(expense)
    }

    /** Permanently removes a product from the catalog. */
    suspend fun deleteProduct(product: Product) = withContext(Dispatchers.IO) {
        productDao.deleteProduct(product)
    }

    /**
     * Creates a new customer order with real-time stock deduction.
     *
     * Business rules enforced here:
     * - Rejects empty item lists and any line whose quantity exceeds available stock by
     *   throwing [IllegalArgumentException] (the UI also validates before calling this).
     *   Made-to-order products skip the stock check/deduction entirely (they are
     *   produced fresh; only their raw-ingredient recipes are consumed).
     * - Generates collision-free order numbers from MAX(existing "ORD-<n>") instead of row
     *   counts, so numbers stay unique even after orders have been deleted.
     * - [discountAmount] is clamped to [0, gross]; income is always recorded on the
     *   net (gross − discount), never the gross.
     * - Records exactly one ORDER_SALE income transaction for paid orders (idempotent guard).
     * - UNPAID orders stay open as credit tabs (see [recordCustomerPayment]); completing
     *   them does NOT auto-mark them paid.
     *
     * @return the freshly inserted [CustomerOrder] (with generated id and order number),
     *   ready for receipt / order-slip generation.
     */
    suspend fun createOrder(
        customerName: String,
        customerPhone: String,
        customerNote: String,
        paymentStatus: PaymentStatus,
        paymentMethod: PaymentMethod,
        items: List<Pair<Product, Int>>,
        discountAmount: Double = 0.0,
        discountReason: String = ""
    ): CustomerOrder = withContext(Dispatchers.IO) {
        // Hard guard: never allow creating an order that exceeds available stock.
        // Made-to-order products are produced fresh, so they skip the stock check
        // (raw ingredients linked through recipes are still auto-deducted below).
        val insufficient = items.filter { (prod, qty) ->
            qty <= 0 || (!prod.madeToOrder && qty > prod.stockQuantity)
        }
        if (items.isEmpty() || insufficient.isNotEmpty()) {
            val detail = insufficient.joinToString { (prod, qty) ->
                "${prod.name}: requested $qty, available ${prod.stockQuantity}"
            }
            throw IllegalArgumentException(
                if (items.isEmpty()) "Cannot create an order with no items." else "Insufficient stock - $detail"
            )
        }

        // Everything below (numbering, order + items insert, stock deduction, income)
        // runs inside a single Room transaction, so a partial failure can never leave
        // inventory deducted without the order (or vice versa).
        database.withTransaction {
        // Robust order numbering: derive from the highest existing ORD-<n> suffix.
        val maxNumber = orderDao.getMaxOrderNumber() ?: 1000
        var orderNumber = "ORD-${maxNumber + 1}"
        while (orderDao.getOrderByNumber(orderNumber) != null) {
            orderNumber = "ORD-${orderNumber.removePrefix("ORD-").toInt() + 1}"
        }
        val totalAmount = items.sumOf { (prod, qty) -> prod.sellingPrice * qty }
        val totalCost = items.sumOf { (prod, qty) -> prod.costPrice * qty }
        val discount = discountAmount.coerceIn(0.0, totalAmount)
        val netAmount = totalAmount - discount

        val order = CustomerOrder(
            orderNumber = orderNumber,
            customerName = customerName.trim(),
            customerPhone = customerPhone.trim(),
            customerNote = customerNote.trim(),
            status = OrderStatus.PENDING.name,
            paymentStatus = paymentStatus.name,
            paymentMethod = paymentMethod.name,
            totalAmount = totalAmount,
            totalCost = totalCost,
            discountAmount = discount,
            discountReason = discountReason.trim(),
            createdAt = System.currentTimeMillis()
        )
        val orderId = orderDao.insertOrder(order)

        val orderItems = items.map { (prod, qty) ->
            OrderItem(
                orderId = orderId,
                productId = prod.id,
                productName = prod.name,
                category = prod.category,
                unitPrice = prod.sellingPrice,
                costPrice = prod.costPrice,
                quantity = qty,
                subtotal = prod.sellingPrice * qty
            )
        }
        orderDao.insertOrderItems(orderItems)

        // Deduct finished inventory in real-time (made-to-order is produced
        // fresh, so it touches no finished stock — only raw ingredients below).
        for ((prod, qty) in items) {
            if (!prod.madeToOrder) productDao.deductStock(prod.id, qty)
        }

        // Auto-consume raw ingredients via recipes (never blocks the order —
        // raw shortage just clamps to zero so the sale still goes through,
        // with the usage still recorded for bought/used/remaining accuracy).
        for ((prod, qty) in items) {
            val recipes = rawMaterialDao.getRecipesForProduct(prod.id)
            for (recipe in recipes) {
                val needed = recipe.quantityPerUnit * qty
                if (needed <= 0) continue
                val raw = rawMaterialDao.getRawMaterialById(recipe.materialId) ?: continue
                val newStock = (raw.stockQuantity - needed).coerceAtLeast(0.0)
                rawMaterialDao.updateRawMaterial(
                    raw.copy(stockQuantity = newStock, lastUpdated = System.currentTimeMillis())
                )
                rawMaterialDao.insertMovement(
                    RawMaterialMovement(
                        materialId = raw.id,
                        type = RawMovementType.USAGE.name,
                        quantity = needed,
                        note = "Auto-use for $orderNumber (${prod.name} x$qty)",
                        linkedOrderId = orderId,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        }

        // If paid, record income transaction - guarded so income is inserted only once.
        // Income is always the NET (gross - discount).
        if (paymentStatus == PaymentStatus.PAID &&
            transactionDao.countPositiveIncomeForOrder(orderId) == 0
        ) {
            val income = Transaction(
                type = TransactionType.INCOME.name,
                category = TransactionCategory.ORDER_SALE.name,
                amount = netAmount,
                title = "Order #$orderNumber - ${customerName.trim().ifEmpty { "Walk-in Customer" }}",
                note = buildString {
                    append("Items: ${items.joinToString { "${it.first.name} x${it.second}" }}")
                    if (discount > 0) append(" | Discount ${discount} MMK (${discountReason.trim().ifBlank { "promo" }})")
                },
                referenceOrderId = orderId,
                timestamp = System.currentTimeMillis()
            )
            transactionDao.insertTransaction(income)
        }

        order.copy(id = orderId)
        }
    }

    /**
     * Moves an order through the PENDING → PREPARING → COMPLETED flow.
     *
     * Completing an UNPAID order does NOT mark it paid — it stays open as a
     * credit tab until [recordCustomerPayment] covers the net total. The only
     * auto-settle left is when partial payments already cover the net (e.g. a
     * tab fully paid before pickup): then completion flips it to PAID and
     * records the single ORDER_SALE income row (idempotent guard).
     */
    suspend fun updateOrderStatus(orderId: Long, newStatus: OrderStatus) = withContext(Dispatchers.IO) {
        val completedAt = if (newStatus == OrderStatus.COMPLETED) System.currentTimeMillis() else null
        orderDao.updateOrderStatus(orderId, newStatus.name, completedAt)

        if (newStatus == OrderStatus.COMPLETED) {
            val orderWithItems = orderDao.getOrderWithItemsById(orderId)
            if (orderWithItems != null &&
                orderWithItems.order.paymentStatus != PaymentStatus.PAID.name
            ) {
                val net = orderWithItems.order.netAmount
                val paid = customerPaymentDao.getPaidTotalForOrder(orderId)
                if (paid >= net && transactionDao.countPositiveIncomeForOrder(orderId) == 0) {
                    orderDao.updatePaymentStatus(orderId, PaymentStatus.PAID.name)
                    val income = Transaction(
                        type = TransactionType.INCOME.name,
                        category = TransactionCategory.ORDER_SALE.name,
                        amount = net,
                        title = "Order #${orderWithItems.order.orderNumber} - ${orderWithItems.order.customerName}",
                        note = "Tab settled in full (${paid} MMK received)",
                        referenceOrderId = orderId,
                        timestamp = System.currentTimeMillis()
                    )
                    transactionDao.insertTransaction(income)
                }
            }
        }
    }

    /**
     * Records a cash-in against an UNPAID (tab) order — full or partial.
     *
     * Throws [IllegalArgumentException] when the amount is invalid (≤ 0 or more
     * than the remaining due). When payments reach the net total, the order flips
     * to PAID and exactly one ORDER_SALE income row is written for the net amount.
     *
     * @return the remaining due after this payment (0 when the tab is settled).
     */
    suspend fun recordCustomerPayment(
        orderId: Long,
        amount: Double,
        method: PaymentMethod,
        note: String = ""
    ): Double = withContext(Dispatchers.IO) {
        if (amount <= 0) throw IllegalArgumentException("Payment must be greater than 0.")
        val orderWithItems = orderDao.getOrderWithItemsById(orderId)
            ?: throw IllegalArgumentException("Order not found.")
        val order = orderWithItems.order
        if (order.status == OrderStatus.CANCELLED.name) {
            throw IllegalArgumentException("Cannot collect on a cancelled order.")
        }
        if (order.paymentStatus == PaymentStatus.PAID.name) {
            throw IllegalArgumentException("Order ${order.orderNumber} is already paid.")
        }
        val due = (order.netAmount - customerPaymentDao.getPaidTotalForOrder(orderId))
            .coerceAtLeast(0.0)
        if (amount > due + 0.009) {
            throw IllegalArgumentException(
                "Payment exceeds the due: ${amount} MMK > ${due} MMK still owed."
            )
        }
        database.withTransaction {
            customerPaymentDao.insertPayment(
                CustomerPayment(
                    orderId = orderId,
                    amount = amount,
                    method = method.name,
                    note = note.trim(),
                    timestamp = System.currentTimeMillis()
                )
            )
            val paidNow = customerPaymentDao.getPaidTotalForOrder(orderId)
            if (paidNow + 0.009 >= order.netAmount) {
                orderDao.updatePaymentStatus(orderId, PaymentStatus.PAID.name)
                if (transactionDao.countPositiveIncomeForOrder(orderId) == 0) {
                    transactionDao.insertTransaction(
                        Transaction(
                            type = TransactionType.INCOME.name,
                            category = TransactionCategory.ORDER_SALE.name,
                            amount = order.netAmount,
                            title = "Order #${order.orderNumber} - ${order.customerName}",
                            note = "Tab settled in full" +
                                (if (order.discountAmount > 0) " (discount ${order.discountAmount} MMK)" else ""),
                            referenceOrderId = orderId,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
        ((order.netAmount - customerPaymentDao.getPaidTotalForOrder(orderId)).coerceAtLeast(0.0))
    }

    /** Total paid so far against an order (0 when nothing recorded). */
    suspend fun getPaidTotal(orderId: Long): Double =
        customerPaymentDao.getPaidTotalForOrder(orderId)

    /** Remaining due on an order: net − paid (0 for PAID/cancelled-settled orders). */
    suspend fun getAmountDue(orderId: Long): Double = withContext(Dispatchers.IO) {
        val full = orderDao.getOrderWithItemsById(orderId) ?: return@withContext 0.0
        if (full.order.paymentStatus == PaymentStatus.PAID.name) return@withContext 0.0
        (full.order.netAmount - customerPaymentDao.getPaidTotalForOrder(orderId)).coerceAtLeast(0.0)
    }

    /**
     * Updates the payment status of an order.
     *
     * When manually marking an order as PAID, an ORDER_SALE income transaction is recorded
     * for the NET amount (gross − discount) — only if none exists yet for that order
     * ([TransactionDao.countPositiveIncomeForOrder]), preventing double income when the
     * income row was already created at order creation time.
     */
    suspend fun updatePaymentStatus(orderId: Long, newPaymentStatus: PaymentStatus) = withContext(Dispatchers.IO) {
        val existingOrder = orderDao.getOrderWithItemsById(orderId) ?: return@withContext
        orderDao.updatePaymentStatus(orderId, newPaymentStatus.name)
        if (newPaymentStatus == PaymentStatus.PAID &&
            transactionDao.countPositiveIncomeForOrder(orderId) == 0
        ) {
            val income = Transaction(
                type = TransactionType.INCOME.name,
                category = TransactionCategory.ORDER_SALE.name,
                amount = existingOrder.order.netAmount,
                title = "Order #${existingOrder.order.orderNumber} Payment - ${existingOrder.order.customerName}",
                note = "Payment received" +
                    (if (existingOrder.order.discountAmount > 0) " (net of ${existingOrder.order.discountAmount} MMK discount)" else ""),
                referenceOrderId = orderId,
                timestamp = System.currentTimeMillis()
            )
            transactionDao.insertTransaction(income)
        }
    }

    /**
     * Cancels an order and compensates for it:
     * 1. Restores the exact stock quantities of every [OrderItem] back into inventory.
     * 2. If the order was already PAID, inserts a compensating REFUND entry - a negative
     *    INCOME transaction of the same amount referencing the same order
     *    ([Transaction.referenceOrderId]). This net-zeroes the income for the cancelled
     *    order while preserving a complete audit trail (the original sale and the refund
     *    both remain visible in the finance ledger), instead of silently deleting history.
     *
     * Safe to call multiple times: stock is only restored while the order has not yet been
     * marked CANCELLED, and refunds are only created when a positive income entry exists.
     */
    suspend fun cancelOrder(orderWithItems: OrderWithItems) = withContext(Dispatchers.IO) {
        val order = orderWithItems.order
        val alreadyCancelled = order.status == OrderStatus.CANCELLED.name
        // Stock restoration + refund entry must commit together with the status change.
        database.withTransaction {
        orderDao.updateOrderStatus(order.id, OrderStatus.CANCELLED.name, null)

        // Return the exact ordered quantities back to inventory (only on first
        // cancellation, and only for stocked products — made-to-order never
        // took finished stock, so there is nothing to return).
        if (!alreadyCancelled) {
            for (item in orderWithItems.items) {
                val prod = productDao.getProductById(item.productId) ?: continue
                if (!prod.madeToOrder) productDao.updateStock(prod.id, prod.stockQuantity + item.quantity)
            }
        }

        // If the cancelled order had been paid, create the compensating refund entry once.
        val hadPositiveIncome = transactionDao.countPositiveIncomeForOrder(order.id) > 0
        val alreadyRefunded = transactionDao.getTransactionsForOrder(order.id)
            .any { it.type == TransactionType.INCOME.name && it.amount < 0 }
        if (order.paymentStatus == PaymentStatus.PAID.name && hadPositiveIncome && !alreadyRefunded) {
            val refund = Transaction(
                type = TransactionType.INCOME.name,
                category = TransactionCategory.ORDER_REFUND.name,
                amount = -order.netAmount,
                title = "Refund: Order #${order.orderNumber} - ${order.customerName}",
                note = "Compensating refund for cancelled order (stock restored)",
                referenceOrderId = order.id,
                timestamp = System.currentTimeMillis()
            )
            transactionDao.insertTransaction(refund)
        }
        }
    }

    // ---------- Raw materials: buy / use / remaining ----------

    fun getMovementsForMaterial(materialId: Long): Flow<List<RawMaterialMovement>> =
        rawMaterialDao.getMovementsForMaterial(materialId)

    suspend fun getTotalPurchased(materialId: Long): Double =
        rawMaterialDao.getTotalPurchased(materialId)

    suspend fun getTotalUsed(materialId: Long): Double =
        rawMaterialDao.getTotalUsed(materialId)

    suspend fun getRecipesForProduct(productId: Long): List<ProductRecipe> =
        rawMaterialDao.getRecipesForProduct(productId)

    /** Creates or updates a raw ingredient. Returns its row id. */
    suspend fun saveRawMaterial(material: RawMaterial): Long = withContext(Dispatchers.IO) {
        if (material.id == 0L) {
            rawMaterialDao.insertRawMaterial(material.copy(lastUpdated = System.currentTimeMillis()))
        } else {
            rawMaterialDao.updateRawMaterial(material.copy(lastUpdated = System.currentTimeMillis()))
            material.id
        }
    }

    /** Deletes a raw ingredient plus its ledger + recipe links. */
    suspend fun deleteRawMaterial(material: RawMaterial) = withContext(Dispatchers.IO) {
        database.withTransaction {
            rawMaterialDao.deleteMovementsForMaterial(material.id)
            rawMaterialDao.deleteRecipesForMaterial(material.id)
            rawMaterialDao.deleteRawMaterial(material)
        }
    }

    /**
     * Records buying raw stock: increases remaining, writes a PURCHASE movement
     * (date = now, visible in history) and logs a RESTOCKING expense so finance
     * stays accurate. Ignored when [quantity] <= 0 or [totalCost] < 0.
     */
    suspend fun purchaseRawMaterial(
        materialId: Long,
        quantity: Double,
        totalCost: Double,
        note: String = ""
    ) = withContext(Dispatchers.IO) {
        if (quantity <= 0 || totalCost < 0) return@withContext
        val material = rawMaterialDao.getRawMaterialById(materialId) ?: return@withContext
        database.withTransaction {
            rawMaterialDao.updateRawMaterial(
                material.copy(
                    stockQuantity = material.stockQuantity + quantity,
                    lastUpdated = System.currentTimeMillis()
                )
            )
            rawMaterialDao.insertMovement(
                RawMaterialMovement(
                    materialId = materialId,
                    type = RawMovementType.PURCHASE.name,
                    quantity = quantity,
                    totalCost = totalCost,
                    note = note.ifBlank { "Purchased $quantity ${material.unit}" },
                    timestamp = System.currentTimeMillis()
                )
            )
            if (totalCost > 0) {
                transactionDao.insertTransaction(
                    Transaction(
                        type = TransactionType.OUTCOME.name,
                        category = TransactionCategory.RESTOCKING.name,
                        amount = totalCost,
                        title = "Raw buy: ${material.name} (+$quantity ${material.unit})",
                        note = note.ifBlank { "Raw material purchase" },
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    /**
     * Records consuming raw stock (manual "Use"): decreases remaining and writes
     * a USAGE movement. Throws [IllegalArgumentException] when [quantity] exceeds
     * available stock — the ViewModel surfaces this as a snackbar.
     */
    suspend fun useRawMaterial(
        materialId: Long,
        quantity: Double,
        note: String = "",
        linkedOrderId: Long? = null
    ) = withContext(Dispatchers.IO) {
        if (quantity <= 0) return@withContext
        val material = rawMaterialDao.getRawMaterialById(materialId) ?: return@withContext
        if (quantity > material.stockQuantity) {
            throw IllegalArgumentException(
                "Not enough ${material.name}: requested $quantity ${material.unit}, " +
                    "available ${material.stockQuantity} ${material.unit}"
            )
        }
        database.withTransaction {
            rawMaterialDao.updateRawMaterial(
                material.copy(
                    stockQuantity = material.stockQuantity - quantity,
                    lastUpdated = System.currentTimeMillis()
                )
            )
            rawMaterialDao.insertMovement(
                RawMaterialMovement(
                    materialId = materialId,
                    type = RawMovementType.USAGE.name,
                    quantity = quantity,
                    note = note.ifBlank { "Used $quantity ${material.unit}" },
                    linkedOrderId = linkedOrderId,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    /** Sets an absolute remaining level (recount / spillage) + ADJUST movement. */
    suspend fun adjustRawMaterial(materialId: Long, newStock: Double, note: String = "") =
        withContext(Dispatchers.IO) {
            val material = rawMaterialDao.getRawMaterialById(materialId) ?: return@withContext
            val safe = newStock.coerceAtLeast(0.0)
            database.withTransaction {
                rawMaterialDao.updateRawMaterial(
                    material.copy(stockQuantity = safe, lastUpdated = System.currentTimeMillis())
                )
                rawMaterialDao.insertMovement(
                    RawMaterialMovement(
                        materialId = materialId,
                        type = RawMovementType.ADJUST.name,
                        quantity = kotlin.math.abs(safe - material.stockQuantity),
                        note = note.ifBlank {
                            "Adjusted ${material.stockQuantity} -> $safe ${material.unit}"
                        },
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        }

    /**
     * Sets how much raw is auto-consumed per 1 unit of product.
     * Pass [quantityPerUnit] <= 0 to remove the linkage.
     */
    suspend fun setRecipe(productId: Long, materialId: Long, quantityPerUnit: Double) =
        withContext(Dispatchers.IO) {
            if (quantityPerUnit <= 0) {
                rawMaterialDao.deleteRecipe(productId, materialId)
            } else {
                rawMaterialDao.upsertRecipe(
                    ProductRecipe(
                        productId = productId,
                        materialId = materialId,
                        quantityPerUnit = quantityPerUnit
                    )
                )
            }
        }

    /**
     * Inserts a manual finance entry (income or expense) that is not tied to an order.
     */
    suspend fun addTransaction(
        type: TransactionType,
        category: TransactionCategory,
        amount: Double,
        title: String,
        note: String
    ) = withContext(Dispatchers.IO) {
        val transaction = Transaction(
            type = type.name,
            category = category.name,
            amount = amount,
            title = title.trim(),
            note = note.trim(),
            timestamp = System.currentTimeMillis()
        )
        transactionDao.insertTransaction(transaction)
    }

    /**
     * Edits an existing finance entry's amount, title, note and category.
     * Order-linked entries can also be corrected manually, but the idempotent income
     * guards elsewhere always re-check counts before inserting new income rows.
     */
    suspend fun updateTransaction(transaction: Transaction) = withContext(Dispatchers.IO) {
        transactionDao.updateTransaction(transaction)
    }

    /** Deletes a finance entry from the ledger. */
    suspend fun deleteTransaction(transaction: Transaction) = withContext(Dispatchers.IO) {
        transactionDao.deleteTransaction(transaction)
    }

    /** Deletes a finance entry from the ledger. */
    suspend fun deleteTransaction(transaction: Transaction) = withContext(Dispatchers.IO) {
        transactionDao.deleteTransaction(transaction)
    }

    /**
     * Builds the multi-format export bundle: full-restore `backup.json` plus
     * spreadsheet-ready CSVs (ledger, orders, products, raw materials, payments)
     * and today's close-out as plain text — packed as one ZIP byte array.
     * Everything is computed from one-shot DAO reads on [Dispatchers.IO].
     */
    suspend fun exportSheetsBundle(): ByteArray = withContext(Dispatchers.IO) {
        val products = productDao.getAllProductsOnce()
        val orders = orderDao.getAllOrdersOnce()
        val itemsByOrder = orderDao.getAllOrderItemsOnce().groupBy { it.orderId }
        val ordersWithItems = orders.map { o ->
            OrderWithItems(order = o, items = itemsByOrder[o.id].orEmpty())
        }
        val transactions = transactionDao.getAllTransactionsOnce()
        val raws = rawMaterialDao.getAllRawMaterialsOnce()
        val movements = rawMaterialDao.getAllMovementsOnce()
        val payments = customerPaymentDao.getAllPaymentsOnce()
        val ordersById = ordersWithItems.associateBy { it.order.id }

        val now = System.currentTimeMillis()
        val dayStart = com.blanccoffee.app.data.model.startOfDay(now)
        val paidBy = payments.groupBy { it.orderId }.mapValues { (_, l) -> l.sumOf { it.amount } }
        val closeout = com.blanccoffee.app.data.model.computeCloseout(
            dayStart, dayStart + 24 * 3600 * 1000L,
            ordersWithItems, transactions, movements, raws, payments, paidBy
        )

        val stamp = exportFileDate(now)
        buildExportZip(
            mapOf(
                "backup.json" to exportBackup(),
                "transactions.csv" to transactionsCsv(transactions),
                "orders.csv" to ordersCsv(ordersWithItems),
                "products.csv" to productsCsv(products),
                "raw_materials.csv" to rawMaterialsCsv(raws, movements),
                "payments.csv" to paymentsCsv(payments, ordersById),
                "closeout-$stamp.txt" to closeout.toShareText(),
                "README.txt" to
                    "BLANC COFFEE export ($stamp)\n" +
                    "backup.json = full restore via Dashboard > Restore.\n" +
                    "CSV files open in Excel / Google Sheets.\n" +
                    "closeout-$stamp.txt = today's Z-report.\n"
            )
        )
    }

    // ---------- Backup & restore (offline JSON, no new dependencies) ----------

    companion object {
        const val BACKUP_APP_TAG = "blanc-coffee"
        const val BACKUP_VERSION = 1
    }

    /**
     * Serializes the whole shop database to a versioned JSON string.
     * Used by the Dashboard backup card (file export + share). Never throws for
     * empty tables — they serialize as empty arrays.
     */
    suspend fun exportBackup(): String = withContext(Dispatchers.IO) {
        val root = org.json.JSONObject()
        root.put("app", BACKUP_APP_TAG)
        root.put("version", BACKUP_VERSION)
        root.put("exportedAt", System.currentTimeMillis())

        root.put("products", org.json.JSONArray().apply {
            productDao.getAllProductsOnce().forEach { p ->
                put(org.json.JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("category", p.category)
                    .put("stockQuantity", p.stockQuantity)
                    .put("unit", p.unit)
                    .put("costPrice", p.costPrice)
                    .put("sellingPrice", p.sellingPrice)
                    .put("minStockThreshold", p.minStockThreshold)
                    .put("sku", p.sku)
                    .put("description", p.description)
                    .put("lastUpdated", p.lastUpdated)
                    .put("madeToOrder", p.madeToOrder)
                    .apply { if (p.expiryDate != null) put("expiryDate", p.expiryDate) })
            }
        })
        root.put("orders", org.json.JSONArray().apply {
            orderDao.getAllOrdersOnce().forEach { o ->
                put(org.json.JSONObject()
                    .put("id", o.id)
                    .put("orderNumber", o.orderNumber)
                    .put("customerName", o.customerName)
                    .put("customerPhone", o.customerPhone)
                    .put("customerNote", o.customerNote)
                    .put("status", o.status)
                    .put("paymentStatus", o.paymentStatus)
                    .put("paymentMethod", o.paymentMethod)
                    .put("totalAmount", o.totalAmount)
                    .put("totalCost", o.totalCost)
                    .put("discountAmount", o.discountAmount)
                    .put("discountReason", o.discountReason)
                    .put("createdAt", o.createdAt)
                    .apply { if (o.completedAt != null) put("completedAt", o.completedAt) })
            }
        })
        root.put("orderItems", org.json.JSONArray().apply {
            orderDao.getAllOrderItemsOnce().forEach { i ->
                put(org.json.JSONObject()
                    .put("id", i.id)
                    .put("orderId", i.orderId)
                    .put("productId", i.productId)
                    .put("productName", i.productName)
                    .put("category", i.category)
                    .put("unitPrice", i.unitPrice)
                    .put("costPrice", i.costPrice)
                    .put("quantity", i.quantity)
                    .put("subtotal", i.subtotal))
            }
        })
        root.put("transactions", org.json.JSONArray().apply {
            transactionDao.getAllTransactionsOnce().forEach { t ->
                put(org.json.JSONObject()
                    .put("id", t.id)
                    .put("type", t.type)
                    .put("category", t.category)
                    .put("amount", t.amount)
                    .put("title", t.title)
                    .put("note", t.note)
                    .put("timestamp", t.timestamp)
                    .apply { if (t.referenceOrderId != null) put("referenceOrderId", t.referenceOrderId) })
            }
        })
        root.put("rawMaterials", org.json.JSONArray().apply {
            rawMaterialDao.getAllRawMaterialsOnce().forEach { m ->
                put(org.json.JSONObject()
                    .put("id", m.id)
                    .put("name", m.name)
                    .put("unit", m.unit)
                    .put("stockQuantity", m.stockQuantity)
                    .put("minThreshold", m.minThreshold)
                    .put("costPerUnit", m.costPerUnit)
                    .put("sku", m.sku)
                    .put("note", m.note)
                    .put("lastUpdated", m.lastUpdated)
                    .apply { if (m.expiryDate != null) put("expiryDate", m.expiryDate) })
            }
        })
        root.put("rawMovements", org.json.JSONArray().apply {
            rawMaterialDao.getAllMovementsOnce().forEach { mv ->
                put(org.json.JSONObject()
                    .put("id", mv.id)
                    .put("materialId", mv.materialId)
                    .put("type", mv.type)
                    .put("quantity", mv.quantity)
                    .put("totalCost", mv.totalCost)
                    .put("note", mv.note)
                    .put("timestamp", mv.timestamp)
                    .apply { if (mv.linkedOrderId != null) put("linkedOrderId", mv.linkedOrderId) })
            }
        })
        root.put("recipes", org.json.JSONArray().apply {
            rawMaterialDao.getAllRecipesOnce().forEach { r ->
                put(org.json.JSONObject()
                    .put("productId", r.productId)
                    .put("materialId", r.materialId)
                    .put("quantityPerUnit", r.quantityPerUnit))
            }
        })
        root.put("payments", org.json.JSONArray().apply {
            customerPaymentDao.getAllPaymentsOnce().forEach { p ->
                put(org.json.JSONObject()
                    .put("id", p.id)
                    .put("orderId", p.orderId)
                    .put("amount", p.amount)
                    .put("method", p.method)
                    .put("note", p.note)
                    .put("timestamp", p.timestamp))
            }
        })
        root.toString()
    }

    /**
     * Replaces the whole database with the contents of an [exportBackup] JSON string.
     * Validates the app tag + version first and throws [IllegalArgumentException]
     * for anything else (wrong file, newer backup version). Runs atomically: a
     * corrupt payload never leaves a half-restored database.
     */
    suspend fun importBackup(json: String) = withContext(Dispatchers.IO) {
        val root = try {
            org.json.JSONObject(json)
        } catch (e: Exception) {
            throw IllegalArgumentException("Not a valid backup file.")
        }
        if (root.optString("app") != BACKUP_APP_TAG) {
            throw IllegalArgumentException("Not a BLANC COFFEE backup file.")
        }
        if (root.optInt("version", -1) != BACKUP_VERSION) {
            throw IllegalArgumentException("Unsupported backup version.")
        }
        fun org.json.JSONObject.optLongOrNull(key: String): Long? =
            if (has(key) && !isNull(key)) optLong(key) else null

        val products = mutableListOf<Product>()
        val orders = mutableListOf<CustomerOrder>()
        val items = mutableListOf<OrderItem>()
        val transactions = mutableListOf<Transaction>()
        val raws = mutableListOf<RawMaterial>()
        val movements = mutableListOf<RawMaterialMovement>()
        val recipes = mutableListOf<ProductRecipe>()
        val payments = mutableListOf<CustomerPayment>()
        try {
            val arr = root.getJSONArray("products")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                products.add(
                    Product(
                        id = o.getLong("id"),
                        name = o.getString("name"),
                        category = o.getString("category"),
                        stockQuantity = o.getInt("stockQuantity"),
                        unit = o.optString("unit", "units"),
                        costPrice = o.getDouble("costPrice"),
                        sellingPrice = o.getDouble("sellingPrice"),
                        minStockThreshold = o.optInt("minStockThreshold", 5),
                        sku = o.optString("sku", ""),
                        description = o.optString("description", ""),
                        expiryDate = o.optLongOrNull("expiryDate"),
                        madeToOrder = o.optBoolean("madeToOrder", false),
                        lastUpdated = o.optLong("lastUpdated", System.currentTimeMillis())
                    )
                )
            }
            val oarr = root.getJSONArray("orders")
            for (i in 0 until oarr.length()) {
                val o = oarr.getJSONObject(i)
                orders.add(
                    CustomerOrder(
                        id = o.getLong("id"),
                        orderNumber = o.getString("orderNumber"),
                        customerName = o.getString("customerName"),
                        customerPhone = o.optString("customerPhone", ""),
                        customerNote = o.optString("customerNote", ""),
                        status = o.optString("status", OrderStatus.PENDING.name),
                        paymentStatus = o.optString("paymentStatus", PaymentStatus.PAID.name),
                        paymentMethod = o.optString("paymentMethod", PaymentMethod.CASH.name),
                        totalAmount = o.getDouble("totalAmount"),
                        totalCost = o.optDouble("totalCost", 0.0),
                        discountAmount = o.optDouble("discountAmount", 0.0),
                        discountReason = o.optString("discountReason", ""),
                        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                        completedAt = o.optLongOrNull("completedAt")
                    )
                )
            }
            val iarr = root.getJSONArray("orderItems")
            for (i in 0 until iarr.length()) {
                val o = iarr.getJSONObject(i)
                items.add(
                    OrderItem(
                        id = o.getLong("id"),
                        orderId = o.getLong("orderId"),
                        productId = o.getLong("productId"),
                        productName = o.getString("productName"),
                        category = o.optString("category", ""),
                        unitPrice = o.getDouble("unitPrice"),
                        costPrice = o.optDouble("costPrice", 0.0),
                        quantity = o.getInt("quantity"),
                        subtotal = o.getDouble("subtotal")
                    )
                )
            }
            val tarr = root.getJSONArray("transactions")
            for (i in 0 until tarr.length()) {
                val o = tarr.getJSONObject(i)
                transactions.add(
                    Transaction(
                        id = o.getLong("id"),
                        type = o.getString("type"),
                        category = o.getString("category"),
                        amount = o.getDouble("amount"),
                        title = o.getString("title"),
                        note = o.optString("note", ""),
                        referenceOrderId = o.optLongOrNull("referenceOrderId"),
                        timestamp = o.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
            val rarr = root.getJSONArray("rawMaterials")
            for (i in 0 until rarr.length()) {
                val o = rarr.getJSONObject(i)
                raws.add(
                    RawMaterial(
                        id = o.getLong("id"),
                        name = o.getString("name"),
                        unit = o.optString("unit", "units"),
                        stockQuantity = o.getDouble("stockQuantity"),
                        minThreshold = o.optDouble("minThreshold", 5.0),
                        costPerUnit = o.optDouble("costPerUnit", 0.0),
                        sku = o.optString("sku", ""),
                        note = o.optString("note", ""),
                        expiryDate = o.optLongOrNull("expiryDate"),
                        lastUpdated = o.optLong("lastUpdated", System.currentTimeMillis())
                    )
                )
            }
            val marr = root.getJSONArray("rawMovements")
            for (i in 0 until marr.length()) {
                val o = marr.getJSONObject(i)
                movements.add(
                    RawMaterialMovement(
                        id = o.getLong("id"),
                        materialId = o.getLong("materialId"),
                        type = o.getString("type"),
                        quantity = o.getDouble("quantity"),
                        totalCost = o.optDouble("totalCost", 0.0),
                        note = o.optString("note", ""),
                        linkedOrderId = o.optLongOrNull("linkedOrderId"),
                        timestamp = o.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
            val rcarr = root.getJSONArray("recipes")
            for (i in 0 until rcarr.length()) {
                val o = rcarr.getJSONObject(i)
                recipes.add(
                    ProductRecipe(
                        productId = o.getLong("productId"),
                        materialId = o.getLong("materialId"),
                        quantityPerUnit = o.getDouble("quantityPerUnit")
                    )
                )
            }
            val parr = root.getJSONArray("payments")
            for (i in 0 until parr.length()) {
                val o = parr.getJSONObject(i)
                payments.add(
                    CustomerPayment(
                        id = o.getLong("id"),
                        orderId = o.getLong("orderId"),
                        amount = o.getDouble("amount"),
                        method = o.optString("method", PaymentMethod.CASH.name),
                        note = o.optString("note", ""),
                        timestamp = o.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw IllegalArgumentException("Backup file is corrupt (missing data).")
        }

        database.withTransaction {
            customerPaymentDao.deleteAllPayments()
            orderDao.deleteAllOrderItems()
            orderDao.deleteAllOrders()
            transactionDao.deleteAllTransactions()
            rawMaterialDao.deleteAllMovements()
            rawMaterialDao.deleteAllRecipes()
            rawMaterialDao.deleteAllRawMaterials()
            productDao.deleteAllProducts()

            if (products.isNotEmpty()) productDao.insertProducts(products)
            if (raws.isNotEmpty()) rawMaterialDao.insertRawMaterials(raws)
            if (movements.isNotEmpty()) rawMaterialDao.insertMovements(movements)
            recipes.forEach { rawMaterialDao.upsertRecipe(it) }
            orders.forEach { orderDao.insertOrder(it) }
            if (items.isNotEmpty()) orderDao.insertOrderItems(items)
            if (transactions.isNotEmpty()) transactionDao.insertTransactions(transactions)
            if (payments.isNotEmpty()) customerPaymentDao.insertPayments(payments)
        }
    }

    /**
     * Seeds sample MMK data (products, finance history and orders with Myanmar customer
     * names) ONLY when the database is completely empty. Existing real data is never wiped.
     * The sample catalog intentionally mixes healthy-stock items with low-stock ones so
     * the low-stock alerts and badges are demonstrable out of the box.
     */
    suspend fun seedInitialDataIfNeeded() = withContext(Dispatchers.IO) {
        // Seed only if the database is completely empty - never destroy real shop data.
        val hasProducts = productDao.getProductCount() > 0
        val hasTransactions = transactionDao.getTransactionCount() > 0
        val hasOrders = orderDao.getOrderCount() > 0
        val isFreshInstall = !hasProducts && !hasTransactions && !hasOrders

        val now = System.currentTimeMillis()
        val hour = 3600 * 1000L
        val day = 24 * 3600 * 1000L

        if (isFreshInstall) {
        // Products in MMK
        val starterProducts = listOf(
            // Coffee Products
            Product(
                name = "Single-Origin Ethiopian Yirgacheffe",
                category = ProductCategory.COFFEE.name,
                stockQuantity = 28,
                unit = "bags",
                costPrice = 11000.0,
                sellingPrice = 20000.0,
                minStockThreshold = 8,
                sku = "COF-ETH-250",
                madeToOrder = true,
                description = "Light roast with floral notes of jasmine, bergamot, and sweet citrus finish. 250g whole bean."
            ),
            Product(
                name = "Dark Espresso Roast Beans",
                category = ProductCategory.COFFEE.name,
                stockQuantity = 15,
                unit = "bags",
                costPrice = 9500.0,
                sellingPrice = 18000.0,
                minStockThreshold = 6,
                sku = "COF-ESP-500",
                madeToOrder = true,
                description = "Bold, velvety blend of Colombia & Sumatra with notes of dark chocolate and toasted almond. 500g."
            ),
            Product(
                name = "Nitro Cold Brew Ready-to-Drink",
                category = ProductCategory.COFFEE.name,
                stockQuantity = 4, // Low stock demo
                unit = "cans",
                costPrice = 3200.0,
                sellingPrice = 6500.0,
                minStockThreshold = 10,
                sku = "COF-NCB-330",
                madeToOrder = true,
                description = "Smooth, micro-infused cold brew draft in 330ml recyclable sleek cans.",
                expiryDate = now + 20 * day // perishable demo: expires in 20 days
            ),
            Product(
                name = "Artisan Drip Coffee Pouches (10pk)",
                category = ProductCategory.COFFEE.name,
                stockQuantity = 22,
                unit = "boxes",
                costPrice = 7000.0,
                sellingPrice = 14000.0,
                minStockThreshold = 5,
                sku = "COF-DRP-10",
                madeToOrder = true,
                description = "Single-serve pour-over filter bags filled with fresh medium roast specialty grind."
            ),

            // Green Tea Products
            Product(
                name = "Uji Ceremonial Grade Matcha",
                category = ProductCategory.GREEN_TEA.name,
                stockQuantity = 12,
                unit = "tins",
                costPrice = 22000.0,
                sellingPrice = 42000.0,
                minStockThreshold = 5,
                sku = "TEA-MTC-050",
                description = "First harvest stone-ground green tea from Kyoto, Japan. Vibrant jade color, rich umami."
            ),
            Product(
                name = "Organic Shizuoka Sencha",
                category = ProductCategory.GREEN_TEA.name,
                stockQuantity = 18,
                unit = "packs",
                costPrice = 8500.0,
                sellingPrice = 17000.0,
                minStockThreshold = 6,
                sku = "TEA-SNC-100",
                description = "Steamed high-grown loose green tea leaves with refreshing grassy sweetness and golden liquor. 100g."
            ),
            Product(
                name = "Genmaicha Roasted Brown Rice Tea",
                category = ProductCategory.GREEN_TEA.name,
                stockQuantity = 2, // Low stock demo
                unit = "packs",
                costPrice = 7500.0,
                sellingPrice = 15000.0,
                minStockThreshold = 5,
                sku = "TEA-GMC-150",
                description = "Traditional green tea blended with nutty toasted brown rice and popped kernels. 150g."
            ),
            Product(
                name = "Matcha Latte Concentrate Bottle",
                category = ProductCategory.GREEN_TEA.name,
                stockQuantity = 14,
                unit = "bottles",
                costPrice = 6000.0,
                sellingPrice = 13500.0,
                minStockThreshold = 5,
                sku = "TEA-MLC-500",
                madeToOrder = true,
                description = "Pure ceremonial matcha syrup blend for quick hot or iced matcha lattes at home. 500ml."
            ),

            // Macadamia Nut Products
            Product(
                name = "Roasted Himalayan Salt Macadamias",
                category = ProductCategory.MACADAMIA_NUT.name,
                stockQuantity = 25,
                unit = "pouches",
                costPrice = 11000.0,
                sellingPrice = 22000.0,
                minStockThreshold = 8,
                sku = "NUT-SLT-200",
                madeToOrder = true,
                description = "Dry roasted premium grade whole macadamias lightly seasoned with pure pink Himalayan salt. 200g."
            ),
            Product(
                name = "Raw Organic Macadamia Kernels",
                category = ProductCategory.MACADAMIA_NUT.name,
                stockQuantity = 20,
                unit = "packs",
                costPrice = 14000.0,
                sellingPrice = 28000.0,
                minStockThreshold = 5,
                sku = "NUT-RAW-400",
                description = "Unsalted, unroasted buttery fresh macadamia nuts. Perfect for baking or wholesome snacking. 400g."
            ),
            Product(
                name = "Wildflower Honey Glazed Macadamias",
                category = ProductCategory.MACADAMIA_NUT.name,
                stockQuantity = 3, // Low stock demo
                unit = "pouches",
                costPrice = 12000.0,
                sellingPrice = 24000.0,
                minStockThreshold = 6,
                sku = "NUT-HNY-200",
                madeToOrder = true,
                description = "Crunchy golden roasted nuts tossed in organic wildflower honey and a touch of sea salt. 200g."
            ),
            Product(
                name = "Matcha White Chocolate Macadamias",
                category = ProductCategory.MACADAMIA_NUT.name,
                stockQuantity = 16,
                unit = "boxes",
                costPrice = 13000.0,
                sellingPrice = 26000.0,
                minStockThreshold = 5,
                sku = "NUT-MTC-180",
                madeToOrder = true,
                description = "Roasted macadamia core enrobed in smooth Belgian white chocolate infused with Uji matcha. 180g."
            )
        )
        productDao.insertProducts(starterProducts)

        // Transactions (financial history in MMK: today and past few days)
        val starterTransactions = listOf(
            // Today's activities
            Transaction(
                type = TransactionType.INCOME.name,
                category = TransactionCategory.ORDER_SALE.name,
                amount = 64000.0,
                title = "Order #ORD-1001 - Emma Watson",
                note = "Matcha Tin + Salted Macadamias",
                timestamp = now - 2 * hour
            ),
            Transaction(
                type = TransactionType.INCOME.name,
                category = TransactionCategory.ORDER_SALE.name,
                amount = 38000.0,
                title = "Order #ORD-1002 - Marcus Chen",
                note = "Ethiopian Yirgacheffe + Dark Espresso",
                timestamp = now - 1 * hour
            ),
            Transaction(
                type = TransactionType.OUTCOME.name,
                category = TransactionCategory.PACKAGING.name,
                amount = 85000.0,
                title = "Custom Kraft Pouches & Stickers",
                note = "Biodegradable coffee bags and eco seal labels",
                timestamp = now - 3 * hour
            ),
            // Yesterday's activities
            Transaction(
                type = TransactionType.INCOME.name,
                category = TransactionCategory.WHOLESALE.name,
                amount = 450000.0,
                title = "Wholesale to Downtown Bistro",
                note = "15x Ethiopian Beans + 5x Sencha packs",
                timestamp = now - 1 * day
            ),
            Transaction(
                type = TransactionType.OUTCOME.name,
                category = TransactionCategory.RESTOCKING.name,
                amount = 280000.0,
                title = "Batch Coffee Bean Sourcing",
                note = "Green coffee beans from import cooperative",
                timestamp = now - 1 * day - 2 * hour
            ),
            Transaction(
                type = TransactionType.OUTCOME.name,
                category = TransactionCategory.UTILITIES.name,
                amount = 110000.0,
                title = "Shop Water Filtration & Power",
                note = "Bi-weekly roaster energy allocation",
                timestamp = now - 2 * day
            ),
            Transaction(
                type = TransactionType.INCOME.name,
                category = TransactionCategory.ORDER_SALE.name,
                amount = 125000.0,
                title = "Weekend Tea & Nut Gift Box",
                note = "Matcha, Macadamias, and Sencha bundle",
                timestamp = now - 2 * day - 4 * hour
            )
        )
        transactionDao.insertTransactions(starterTransactions)

        // Starter Orders in MMK (featuring Myanmar customer names & requests)
        val order1 = CustomerOrder(
            orderNumber = "ORD-1001",
            customerName = "ဒေါ်အေးအေးသင်း (Daw Aye Aye Thin)",
            customerPhone = "+95 9 789 123456",
            customerNote = "မွေးနေ့လက်ဆောင် ထုပ်ပိုးပေးပါ",
            status = OrderStatus.COMPLETED.name,
            paymentStatus = PaymentStatus.PAID.name,
            paymentMethod = PaymentMethod.CARD.name,
            totalAmount = 64000.0,
            totalCost = 33000.0,
            createdAt = now - 2 * hour,
            completedAt = now - 1 * hour - 30 * 60 * 1000L
        )
        val orderId1 = orderDao.insertOrder(order1)
        orderDao.insertOrderItems(listOf(
            OrderItem(
                orderId = orderId1,
                productId = 5L,
                productName = "Uji Ceremonial Grade Matcha",
                category = ProductCategory.GREEN_TEA.name,
                unitPrice = 42000.0,
                costPrice = 22000.0,
                quantity = 1,
                subtotal = 42000.0
            ),
            OrderItem(
                orderId = orderId1,
                productId = 9L,
                productName = "Roasted Himalayan Salt Macadamias",
                category = ProductCategory.MACADAMIA_NUT.name,
                unitPrice = 22000.0,
                costPrice = 11000.0,
                quantity = 1,
                subtotal = 22000.0
            )
        ))

        val order2 = CustomerOrder(
            orderNumber = "ORD-1002",
            customerName = "ဦးမင်းသူ (U Min Thu)",
            customerPhone = "+95 9 950 654321",
            customerNote = "အသင့်ဖြစ်ပါက ဖုန်းဆက်ပေးပါ (Call when ready)",
            status = OrderStatus.PREPARING.name,
            paymentStatus = PaymentStatus.PAID.name,
            paymentMethod = PaymentMethod.MOBILE_PAY.name,
            totalAmount = 38000.0,
            totalCost = 20500.0,
            createdAt = now - 45 * 60 * 1000L
        )
        val orderId2 = orderDao.insertOrder(order2)
        orderDao.insertOrderItems(listOf(
            OrderItem(
                orderId = orderId2,
                productId = 1L,
                productName = "Single-Origin Ethiopian Yirgacheffe",
                category = ProductCategory.COFFEE.name,
                unitPrice = 20000.0,
                costPrice = 11000.0,
                quantity = 1,
                subtotal = 20000.0
            ),
            OrderItem(
                orderId = orderId2,
                productId = 2L,
                productName = "Dark Espresso Roast Beans",
                category = ProductCategory.COFFEE.name,
                unitPrice = 18000.0,
                costPrice = 9500.0,
                quantity = 1,
                subtotal = 18000.0
            )
        ))

        val order3 = CustomerOrder(
            orderNumber = "ORD-1003",
            customerName = "မစုမြတ်နိုး (Sophia / Ma Su)",
            customerPhone = "+95 9 450 789012",
            customerNote = "နေ့လည် ၂ နာရီ လာယူပါမည် (Pickup at 2 PM)",
            status = OrderStatus.PENDING.name,
            paymentStatus = PaymentStatus.PAID.name,
            paymentMethod = PaymentMethod.CARD.name,
            totalAmount = 76000.0,
            totalCost = 38000.0,
            createdAt = now - 15 * 60 * 1000L
        )
        val orderId3 = orderDao.insertOrder(order3)
        orderDao.insertOrderItems(listOf(
            OrderItem(
                orderId = orderId3,
                productId = 12L,
                productName = "Matcha White Chocolate Macadamias",
                category = ProductCategory.MACADAMIA_NUT.name,
                unitPrice = 26000.0,
                costPrice = 13000.0,
                quantity = 2,
                subtotal = 52000.0
            ),
            OrderItem(
                orderId = orderId3,
                productId = 11L,
                productName = "Wildflower Honey Glazed Macadamias",
                category = ProductCategory.MACADAMIA_NUT.name,
                unitPrice = 24000.0,
                costPrice = 12000.0,
                quantity = 1,
                subtotal = 24000.0
            )
        ))
        } // end fresh-install product/order seed

        // Raw-ingredient seed runs whenever the raw tables are empty — including
        // upgrades from v1 where products already exist. Never wipes real data.
        if (rawMaterialDao.getRawMaterialCount() == 0) {
            seedRawMaterialsIfEmpty(now, day)
        }
    }

    /**
     * Seeds raw ingredients (bought-in-bags history), a few PURCHASE/USAGE
     * movements with real dates, and product recipes for auto-deduct.
     * Product ids 1..12 match the fresh-install catalog above.
     */
    private suspend fun seedRawMaterialsIfEmpty(now: Long, day: Long) {
        val raws = listOf(
            RawMaterial(
                name = "Raw Macadamia Kernels",
                unit = "bags",
                stockQuantity = 14.0,
                minThreshold = 4.0,
                costPerUnit = 95000.0,
                sku = "RAW-NUT-25KG",
                note = "25kg bags from Shan supplier"
            ),
            RawMaterial(
                name = "Green Coffee Beans (Arabica)",
                unit = "bags",
                stockQuantity = 22.0,
                minThreshold = 6.0,
                costPerUnit = 85000.0,
                sku = "RAW-COF-60KG",
                note = "60kg bags, Yirgacheffe + Espresso blend"
            ),
            RawMaterial(
                name = "Ceremonial Matcha Powder",
                unit = "kg",
                stockQuantity = 3.5,
                minThreshold = 2.0,
                costPerUnit = 380000.0,
                sku = "RAW-MTC-1KG",
                note = "Uji first-harvest, 1kg tins"
            ),
            RawMaterial(
                name = "Wildflower Honey",
                unit = "bottles",
                stockQuantity = 2.0,
                minThreshold = 3.0,
                costPerUnit = 18000.0,
                sku = "RAW-HNY-1L",
                note = "Low-stock demo — reorder soon",
                expiryDate = now + 60 * day
            ),
            RawMaterial(
                name = "Kraft Pouches + Labels",
                unit = "pcs",
                stockQuantity = 480.0,
                minThreshold = 100.0,
                costPerUnit = 450.0,
                sku = "RAW-PKG-500",
                note = "Biodegradable pouches + eco seals"
            )
        )
        rawMaterialDao.insertRawMaterials(raws)

        // Re-read to get generated ids (raw tables are brand-new in v2, so
        // auto-increment starts at 1 and insert order matches the list above).
        val all = mutableListOf<RawMaterial>()
        for (id in 1L..5L) {
            rawMaterialDao.getRawMaterialById(id)?.let { all.add(it) }
        }
        fun idOf(name: String): Long =
            all.firstOrNull { it.name == name }?.id ?: 0L

        val nutsId = idOf("Raw Macadamia Kernels")
        val beansId = idOf("Green Coffee Beans (Arabica)")
        val matchaId = idOf("Ceremonial Matcha Powder")
        val honeyId = idOf("Wildflower Honey")

        if (nutsId != 0L) {
            rawMaterialDao.insertMovements(
                listOf(
                    RawMaterialMovement(
                        materialId = nutsId,
                        type = RawMovementType.PURCHASE.name,
                        quantity = 10.0,
                        totalCost = 950000.0,
                        note = "Bought 10 bags — Shan harvest lot",
                        timestamp = now - 6 * day
                    ),
                    RawMaterialMovement(
                        materialId = nutsId,
                        type = RawMovementType.PURCHASE.name,
                        quantity = 8.0,
                        totalCost = 760000.0,
                        note = "Bought 8 bags — top-up",
                        timestamp = now - 2 * day
                    ),
                    RawMaterialMovement(
                        materialId = nutsId,
                        type = RawMovementType.USAGE.name,
                        quantity = 4.0,
                        note = "Roasting batch: salted + honey-glazed pouches",
                        timestamp = now - 1 * day
                    )
                )
            )
        }
        if (beansId != 0L) {
            rawMaterialDao.insertMovements(
                listOf(
                    RawMaterialMovement(
                        materialId = beansId,
                        type = RawMovementType.PURCHASE.name,
                        quantity = 15.0,
                        totalCost = 1275000.0,
                        note = "Bought 15 bags — import cooperative",
                        timestamp = now - 5 * day
                    ),
                    RawMaterialMovement(
                        materialId = beansId,
                        type = RawMovementType.USAGE.name,
                        quantity = 3.0,
                        note = "Roast run: Ethiopian + Espresso",
                        timestamp = now - 1 * day
                    )
                )
            )
        }
        if (matchaId != 0L) {
            rawMaterialDao.insertMovements(
                listOf(
                    RawMaterialMovement(
                        materialId = matchaId,
                        type = RawMovementType.PURCHASE.name,
                        quantity = 5.0,
                        totalCost = 1900000.0,
                        note = "Bought 5 kg — Uji ceremonial",
                        timestamp = now - 4 * day
                    ),
                    RawMaterialMovement(
                        materialId = matchaId,
                        type = RawMovementType.USAGE.name,
                        quantity = 1.5,
                        note = "Tinning + latte concentrate batch",
                        timestamp = now - 2 * day
                    )
                )
            )
        }
        if (honeyId != 0L) {
            rawMaterialDao.insertMovements(
                listOf(
                    RawMaterialMovement(
                        materialId = honeyId,
                        type = RawMovementType.PURCHASE.name,
                        quantity = 6.0,
                        totalCost = 108000.0,
                        note = "Bought 6 bottles",
                        timestamp = now - 7 * day
                    ),
                    RawMaterialMovement(
                        materialId = honeyId,
                        type = RawMovementType.USAGE.name,
                        quantity = 4.0,
                        note = "Honey-glazed macadamia batch",
                        timestamp = now - 3 * day
                    )
                )
            )
        }

        // Recipes: finished product -> raw usage per 1 unit sold.
        // Coffee pouches consume beans; macadamia pouches consume raw kernels (+ honey).
        val recipes = listOf(
            // 1 bag of roasted beans ≈ 1 bag green beans (simplified 1:1 minus roast loss)
            ProductRecipe(productId = 1L, materialId = beansId, quantityPerUnit = 1.0),
            ProductRecipe(productId = 2L, materialId = beansId, quantityPerUnit = 1.0),
            ProductRecipe(productId = 5L, materialId = matchaId, quantityPerUnit = 0.05),
            ProductRecipe(productId = 9L, materialId = nutsId, quantityPerUnit = 0.2),
            ProductRecipe(productId = 10L, materialId = nutsId, quantityPerUnit = 0.4),
            ProductRecipe(productId = 11L, materialId = nutsId, quantityPerUnit = 0.2),
            ProductRecipe(productId = 11L, materialId = honeyId, quantityPerUnit = 0.1),
            ProductRecipe(productId = 12L, materialId = nutsId, quantityPerUnit = 0.2),
            ProductRecipe(productId = 12L, materialId = matchaId, quantityPerUnit = 0.02)
        ).filter { it.materialId != 0L }
        recipes.forEach { rawMaterialDao.upsertRecipe(it) }
    }
}
