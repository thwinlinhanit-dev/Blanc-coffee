package com.example.data.repository

import com.example.data.local.ExpenseDao
import com.example.data.local.IncomeDao
import com.example.data.local.InventoryDao
import com.example.data.local.OrderDao
import com.example.data.local.ProductDao
import com.example.data.local.TransactionDao
import com.example.data.model.CustomerOrder
import com.example.data.model.OrderItem
import com.example.data.model.OrderStatus
import com.example.data.model.OrderWithItems
import com.example.data.model.PaymentMethod
import com.example.data.model.PaymentStatus
import com.example.data.model.Product
import com.example.data.model.ProductCategory
import com.example.data.model.Transaction
import com.example.data.model.TransactionCategory
import com.example.data.model.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ShopRepository(
    val productDao: ProductDao,
    val inventoryDao: InventoryDao,
    val incomeDao: IncomeDao,
    val expenseDao: ExpenseDao,
    val orderDao: OrderDao,
    val transactionDao: TransactionDao
) {
    // Secondary constructor for backwards compatibility
    constructor(
        productDao: ProductDao,
        orderDao: OrderDao,
        transactionDao: TransactionDao
    ) : this(
        productDao = productDao,
        inventoryDao = object : InventoryDao {
            override suspend fun insertInventoryItem(product: Product) = productDao.insertProduct(product)
            override suspend fun insertInventoryItems(products: List<Product>) = productDao.insertProducts(products)
            override fun getAllInventory() = productDao.getAllProducts()
            override suspend fun getInventoryById(id: Long) = productDao.getProductById(id)
            override fun getInventoryByCategory(category: String) = productDao.getProductsByCategory(category)
            override fun getLowStockInventory() = productDao.getLowStockProducts()
            override fun getOutOfStockInventory() = productDao.getProductsByCategory("")
            override suspend fun getTotalInventoryCount() = productDao.getProductCount()
            override fun getLowStockCount() = kotlinx.coroutines.flow.flowOf(0)
            override suspend fun updateInventoryItem(product: Product) = productDao.updateProduct(product)
            override suspend fun updateStockQuantity(productId: Long, newStock: Int, timestamp: Long) = productDao.updateStock(productId, newStock, timestamp)
            override suspend fun restockProduct(productId: Long, addedQuantity: Int, timestamp: Long) {}
            override suspend fun deductStock(productId: Long, quantity: Int, timestamp: Long) = productDao.deductStock(productId, quantity, timestamp)
            override suspend fun deleteInventoryItem(product: Product) = productDao.deleteProduct(product)
            override suspend fun deleteInventoryById(productId: Long) = productDao.deleteProductById(productId)
            override suspend fun clearAllInventory() = productDao.deleteAllProducts()
        },
        incomeDao = object : IncomeDao {
            override suspend fun insertIncome(transaction: Transaction) = transactionDao.insertTransaction(transaction)
            override suspend fun insertAllIncome(transactions: List<Transaction>) = transactionDao.insertTransactions(transactions)
            override fun getAllIncome() = transactionDao.getTransactionsByType(TransactionType.INCOME.name)
            override suspend fun getIncomeById(id: Long) = null
            override fun getIncomeByCategory(category: String) = transactionDao.getTransactionsByCategory(category)
            override fun getIncomeBetweenTimestamps(startTimestamp: Long, endTimestamp: Long) = transactionDao.getAllTransactions()
            override fun getTotalIncomeAmount() = kotlinx.coroutines.flow.flowOf(0.0)
            override suspend fun getIncomeCount() = transactionDao.getTransactionCount()
            override suspend fun updateIncome(transaction: Transaction) = transactionDao.updateTransaction(transaction)
            override suspend fun updateIncomeDetails(id: Long, newAmount: Double, newTitle: String, newNote: String) {}
            override suspend fun deleteIncome(transaction: Transaction) = transactionDao.deleteTransaction(transaction)
            override suspend fun deleteIncomeById(id: Long) = transactionDao.deleteTransactionById(id)
            override suspend fun deleteAllIncome() {}
        },
        expenseDao = object : ExpenseDao {
            override suspend fun insertExpense(transaction: Transaction) = transactionDao.insertTransaction(transaction)
            override suspend fun insertAllExpenses(transactions: List<Transaction>) = transactionDao.insertTransactions(transactions)
            override fun getAllExpenses() = transactionDao.getTransactionsByType(TransactionType.OUTCOME.name)
            override suspend fun getExpenseById(id: Long) = null
            override fun getExpensesByCategory(category: String) = transactionDao.getTransactionsByCategory(category)
            override fun getExpensesBetweenTimestamps(startTimestamp: Long, endTimestamp: Long) = transactionDao.getAllTransactions()
            override fun getTotalExpenseAmount() = kotlinx.coroutines.flow.flowOf(0.0)
            override suspend fun getExpenseCount() = transactionDao.getTransactionCount()
            override suspend fun updateExpense(transaction: Transaction) = transactionDao.updateTransaction(transaction)
            override suspend fun updateExpenseDetails(id: Long, newAmount: Double, newTitle: String, newNote: String) {}
            override suspend fun deleteExpense(transaction: Transaction) = transactionDao.deleteTransaction(transaction)
            override suspend fun deleteExpenseById(id: Long) = transactionDao.deleteTransactionById(id)
            override suspend fun deleteAllExpenses() {}
        },
        orderDao = orderDao,
        transactionDao = transactionDao
    )

    // Reactive streams from DAOs
    val allProducts: Flow<List<Product>> = productDao.getAllProducts()
    val lowStockProducts: Flow<List<Product>> = inventoryDao.getLowStockInventory()
    val allOrders: Flow<List<OrderWithItems>> = orderDao.getAllOrdersWithItems()
    val allTransactions: Flow<List<Transaction>> = transactionDao.getAllTransactions()
    val allIncome: Flow<List<Transaction>> = incomeDao.getAllIncome()
    val allExpenses: Flow<List<Transaction>> = expenseDao.getAllExpenses()

    fun getProductsByCategory(category: String): Flow<List<Product>> =
        productDao.getProductsByCategory(category)

    fun getOrdersByStatus(status: String): Flow<List<OrderWithItems>> =
        orderDao.getOrdersByStatus(status)

    fun getTransactionsByType(type: String): Flow<List<Transaction>> =
        transactionDao.getTransactionsByType(type)

    suspend fun saveProduct(product: Product): Long = withContext(Dispatchers.IO) {
        if (product.id == 0L) {
            productDao.insertProduct(product)
        } else {
            productDao.updateProduct(product)
            product.id
        }
    }

    suspend fun updateStock(productId: Long, newStock: Int) = withContext(Dispatchers.IO) {
        productDao.updateStock(productId, newStock)
    }

    suspend fun restockProduct(productId: Long, additionalQuantity: Int, totalCost: Double, note: String = "") = withContext(Dispatchers.IO) {
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

    suspend fun deleteProduct(product: Product) = withContext(Dispatchers.IO) {
        productDao.deleteProduct(product)
    }

    suspend fun createOrder(
        customerName: String,
        customerPhone: String,
        customerNote: String,
        paymentStatus: PaymentStatus,
        paymentMethod: PaymentMethod,
        items: List<Pair<Product, Int>>
    ): Long = withContext(Dispatchers.IO) {
        val count = orderDao.getOrderCount()
        val orderNumber = "ORD-${1001 + count}"
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

        // If paid, record income transaction
        if (paymentStatus == PaymentStatus.PAID) {
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

        orderId
    }

    suspend fun updateOrderStatus(orderId: Long, newStatus: OrderStatus) = withContext(Dispatchers.IO) {
        val completedAt = if (newStatus == OrderStatus.COMPLETED) System.currentTimeMillis() else null
        orderDao.updateOrderStatus(orderId, newStatus.name, completedAt)

        // If transitioning to completed and payment was not paid, or to ensure income is logged
        val orderWithItems = orderDao.getOrderWithItemsById(orderId)
        if (newStatus == OrderStatus.COMPLETED && orderWithItems != null) {
            if (orderWithItems.order.paymentStatus != PaymentStatus.PAID.name) {
                orderDao.updatePaymentStatus(orderId, PaymentStatus.PAID.name)
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

    suspend fun updatePaymentStatus(orderId: Long, newPaymentStatus: PaymentStatus) = withContext(Dispatchers.IO) {
        orderDao.updatePaymentStatus(orderId, newPaymentStatus.name)
        val orderWithItems = orderDao.getOrderWithItemsById(orderId)
        if (newPaymentStatus == PaymentStatus.PAID && orderWithItems != null) {
            val income = Transaction(
                type = TransactionType.INCOME.name,
                category = TransactionCategory.ORDER_SALE.name,
                amount = orderWithItems.order.totalAmount,
                title = "Order #${orderWithItems.order.orderNumber} Payment - ${orderWithItems.order.customerName}",
                note = "Payment received",
                referenceOrderId = orderId,
                timestamp = System.currentTimeMillis()
            )
            transactionDao.insertTransaction(income)
        }
    }

    suspend fun cancelOrder(orderWithItems: OrderWithItems) = withContext(Dispatchers.IO) {
        orderDao.updateOrderStatus(orderWithItems.order.id, OrderStatus.CANCELLED.name, null)
        // Return products back to inventory
        for (item in orderWithItems.items) {
            val prod = productDao.getProductById(item.productId)
            if (prod != null) {
                productDao.updateStock(prod.id, prod.stockQuantity + item.quantity)
            }
        }
    }

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

    suspend fun deleteTransaction(transaction: Transaction) = withContext(Dispatchers.IO) {
        transactionDao.deleteTransaction(transaction)
    }

    suspend fun seedInitialDataIfNeeded() = withContext(Dispatchers.IO) {
        val existingCount = productDao.getProductCount()
        if (existingCount > 0) {
            val sample = productDao.getFirstProduct()
            if (sample != null && sample.sellingPrice >= 1000.0) {
                // Already seeded in MMK currency
                return@withContext
            }
            // Clear older dollar data to upgrade to MMK values
            productDao.deleteAllProducts()
            orderDao.deleteAllOrders()
            orderDao.deleteAllOrderItems()
            transactionDao.deleteAllTransactions()
        }

        val now = System.currentTimeMillis()
        val hour = 3600 * 1000L
        val day = 24 * 3600 * 1000L

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
    }
}
