package com.blanccoffee.app.data.repository

import com.blanccoffee.app.data.local.AppDatabase
import com.blanccoffee.app.data.local.InventoryDao
import com.blanccoffee.app.data.local.OrderDao
import com.blanccoffee.app.data.local.ProductDao
import com.blanccoffee.app.data.local.RawMaterialDao
import com.blanccoffee.app.data.local.TransactionDao
import com.blanccoffee.app.data.model.CustomerOrder
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
    private val rawMaterialDao: RawMaterialDao
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
     * - Generates collision-free order numbers from MAX(existing "ORD-<n>") instead of row
     *   counts, so numbers stay unique even after orders have been deleted.
     * - Records exactly one ORDER_SALE income transaction for paid orders (idempotent guard).
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
        items: List<Pair<Product, Int>>
    ): CustomerOrder = withContext(Dispatchers.IO) {
        // Hard guard: never allow creating an order that exceeds available stock.
        val insufficient = items.filter { (prod, qty) -> qty <= 0 || qty > prod.stockQuantity }
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

        // Deduct inventory in real-time
        for ((prod, qty) in items) {
            productDao.deductStock(prod.id, qty)
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
        if (paymentStatus == PaymentStatus.PAID &&
            transactionDao.countPositiveIncomeForOrder(orderId) == 0
        ) {
            val income = Transaction(
                type = TransactionType.INCOME.name,
                category = TransactionCategory.ORDER_SALE.name,
                amount = totalAmount,
                title = "Order #$orderNumber - ${customerName.trim().ifEmpty { "Walk-in Customer" }}",
                note = "Items: ${items.joinToString { "${it.first.name} x${it.second}" }}",
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
     * When an order transitions to COMPLETED while still unpaid, it is settled automatically:
     * the payment status becomes PAID and an ORDER_SALE income transaction is inserted -
     * but only if no positive income transaction already exists for this order, which
     * makes the operation idempotent and prevents double income recording.
     */
    suspend fun updateOrderStatus(orderId: Long, newStatus: OrderStatus) = withContext(Dispatchers.IO) {
        val completedAt = if (newStatus == OrderStatus.COMPLETED) System.currentTimeMillis() else null
        orderDao.updateOrderStatus(orderId, newStatus.name, completedAt)

        // If transitioning to completed and payment was not paid, or to ensure income is logged
        val orderWithItems = orderDao.getOrderWithItemsById(orderId)
        if (newStatus == OrderStatus.COMPLETED && orderWithItems != null) {
            if (orderWithItems.order.paymentStatus != PaymentStatus.PAID.name) {
                orderDao.updatePaymentStatus(orderId, PaymentStatus.PAID.name)
                // Idempotent income guard: only insert if this order has no positive income yet.
                if (transactionDao.countPositiveIncomeForOrder(orderId) == 0) {
                    val income = Transaction(
                        type = TransactionType.INCOME.name,
                        category = TransactionCategory.ORDER_SALE.name,
                        amount = orderWithItems.order.totalAmount,
                        title = "Order #${orderWithItems.order.orderNumber} - ${orderWithItems.order.customerName}",
                        note = "Completed and settled",
                        referenceOrderId = orderId,
                        timestamp = System.currentTimeMillis()
                    )
                    transactionDao.insertTransaction(income)
                }
            }
        }
    }

    /**
     * Updates the payment status of an order.
     *
     * When manually marking an order as PAID, an ORDER_SALE income transaction is recorded
     * only if none exists yet for that order ([TransactionDao.countPositiveIncomeForOrder]),
     * preventing double income when the income row was already created at order creation time.
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
                amount = existingOrder.order.totalAmount,
                title = "Order #${existingOrder.order.orderNumber} Payment - ${existingOrder.order.customerName}",
                note = "Payment received",
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

        // Return the exact ordered quantities back to inventory (only on first cancellation).
        if (!alreadyCancelled) {
            for (item in orderWithItems.items) {
                val prod = productDao.getProductById(item.productId) ?: continue
                productDao.updateStock(prod.id, prod.stockQuantity + item.quantity)
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
                amount = -order.totalAmount,
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
                description = "Smooth, micro-infused cold brew draft in 330ml recyclable sleek cans."
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
                note = "Low-stock demo — reorder soon"
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
