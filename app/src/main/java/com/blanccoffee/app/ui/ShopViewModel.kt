package com.blanccoffee.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blanccoffee.app.data.di.DatabaseModule
import com.blanccoffee.app.data.local.AppDatabase
import com.blanccoffee.app.data.model.CategorySalesStat
import com.blanccoffee.app.data.model.CustomerOrder
import com.blanccoffee.app.data.model.OrderStatus
import com.blanccoffee.app.data.model.OrderWithItems
import com.blanccoffee.app.data.model.PaymentMethod
import com.blanccoffee.app.data.model.PaymentStatus
import com.blanccoffee.app.data.model.Product
import com.blanccoffee.app.data.model.ProductCategory
import com.blanccoffee.app.data.model.ProductRecipe
import com.blanccoffee.app.data.model.RawMaterial
import com.blanccoffee.app.data.model.RawMaterialMovement
import com.blanccoffee.app.data.model.SalesPerformanceSummary
import com.blanccoffee.app.data.model.TimePeriodFilter
import com.blanccoffee.app.data.model.Transaction
import com.blanccoffee.app.data.model.TransactionCategory
import com.blanccoffee.app.data.model.TransactionType
import com.blanccoffee.app.data.repository.ShopRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class ShopViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DatabaseModule.provideShopRepository(application)

    /** True while any database write is in flight; screens show a subtle progress indicator. */
    private val _isWriting = MutableStateFlow(false)
    val isWriting: StateFlow<Boolean> = _isWriting.asStateFlow()

    /** One-shot error message for failed writes (e.g. insufficient stock); null when clear. */
    private val _writeError = MutableStateFlow<String?>(null)
    val writeError: StateFlow<String?> = _writeError.asStateFlow()

    /** Alias of [writeError] for one-shot snackbar display at the app scaffold level. */
    val oneShotError: StateFlow<String?> = _writeError.asStateFlow()

    /** Clears the current write error, e.g. after it has been shown in a snackbar. */
    fun clearWriteError() {
        _writeError.value = null
    }

    /** Consumes (clears) the current one-shot error after it has been displayed. */
    fun consumeError() {
        _writeError.value = null
    }

    /**
     * Runs a database write on the ViewModel scope with shared loading/error handling:
     * exposes [isWriting] while in flight, captures failures into [writeError], and only
     * invokes [onSuccess] when the write completed without throwing.
     */
    private fun launchWrite(onSuccess: () -> Unit = {}, block: suspend () -> Unit) {
        viewModelScope.launch {
            _isWriting.value = true
            try {
                block()
                onSuccess()
            } catch (e: IllegalArgumentException) {
                _writeError.value = e.message ?: "Invalid input"
            } catch (e: Exception) {
                _writeError.value = e.message ?: "Something went wrong. Please try again."
            } finally {
                _isWriting.value = false
            }
        }
    }

    val products: StateFlow<List<Product>> = repository.allProducts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val orders: StateFlow<List<OrderWithItems>> = repository.allOrders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val transactions: StateFlow<List<Transaction>> = repository.allTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lowStockProducts: StateFlow<List<Product>> = repository.lowStockProducts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rawMaterials: StateFlow<List<RawMaterial>> = repository.allRawMaterials
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lowRawMaterials: StateFlow<List<RawMaterial>> = repository.lowRawMaterials
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rawMovements: StateFlow<List<RawMaterialMovement>> = repository.allRawMovements
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recipes: StateFlow<List<ProductRecipe>> = repository.allRecipes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedTimePeriod = MutableStateFlow(TimePeriodFilter.TODAY)
    val inventoryCategoryFilter = MutableStateFlow<ProductCategory?>(null)
    val inventorySearchQuery = MutableStateFlow("")
    val orderStatusFilter = MutableStateFlow<OrderStatus?>(null)
    val transactionTypeFilter = MutableStateFlow<TransactionType?>(null)

    val performanceStats: StateFlow<SalesPerformanceSummary> = combine(
        transactions,
        orders,
        products,
        selectedTimePeriod
    ) { txs, ords, prods, period ->
        val cutoff = calculateCutoffTimestamp(period)

        val periodTxs = if (cutoff == 0L) txs else txs.filter { it.timestamp >= cutoff }
        val periodOrders = if (cutoff == 0L) ords else ords.filter { it.order.createdAt >= cutoff }

        val income = periodTxs.filter { it.type == TransactionType.INCOME.name }.sumOf { it.amount }
        val outcome = periodTxs.filter { it.type == TransactionType.OUTCOME.name }.sumOf { it.amount }
        val net = income - outcome
        val margin = if (income > 0) (net / income) * 100.0 else 0.0

        val completedOrders = periodOrders.count { it.order.status == OrderStatus.COMPLETED.name }
        val pendingOrders = periodOrders.count { it.order.status == OrderStatus.PENDING.name || it.order.status == OrderStatus.PREPARING.name }

        val lowStock = prods.count { it.isLowStock }
        val outOfStock = prods.count { it.isOutOfStock }

        // Category breakdown based on items sold in period orders
        val completedOrPaidItems = periodOrders
            .filter { it.order.status != OrderStatus.CANCELLED.name }
            .flatMap { it.items }

        val coffeeItems = completedOrPaidItems.filter { it.category == ProductCategory.COFFEE.name }
        val teaItems = completedOrPaidItems.filter { it.category == ProductCategory.GREEN_TEA.name }
        val nutItems = completedOrPaidItems.filter { it.category == ProductCategory.MACADAMIA_NUT.name }

        val coffeeRev = coffeeItems.sumOf { it.subtotal }
        val teaRev = teaItems.sumOf { it.subtotal }
        val nutRev = nutItems.sumOf { it.subtotal }
        val totalCatRev = (coffeeRev + teaRev + nutRev).coerceAtLeast(0.01)

        val catStats = listOf(
            CategorySalesStat(
                category = ProductCategory.COFFEE,
                unitsSold = coffeeItems.sumOf { it.quantity },
                totalRevenue = coffeeRev,
                revenueSharePercent = ((coffeeRev / totalCatRev) * 100).toFloat()
            ),
            CategorySalesStat(
                category = ProductCategory.GREEN_TEA,
                unitsSold = teaItems.sumOf { it.quantity },
                totalRevenue = teaRev,
                revenueSharePercent = ((teaRev / totalCatRev) * 100).toFloat()
            ),
            CategorySalesStat(
                category = ProductCategory.MACADAMIA_NUT,
                unitsSold = nutItems.sumOf { it.quantity },
                totalRevenue = nutRev,
                revenueSharePercent = ((nutRev / totalCatRev) * 100).toFloat()
            )
        )

        SalesPerformanceSummary(
            totalIncome = income,
            totalOutcome = outcome,
            netProfit = net,
            profitMarginPercent = margin,
            completedOrdersCount = completedOrders,
            pendingOrdersCount = pendingOrders,
            totalProductsCount = prods.size,
            lowStockCount = lowStock,
            outOfStockCount = outOfStock,
            categoryStats = catStats
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        SalesPerformanceSummary(0.0, 0.0, 0.0, 0.0, 0, 0, 0, 0, 0, emptyList())
    )

    init {
        viewModelScope.launch {
            repository.seedInitialDataIfNeeded()
        }
    }

    private fun calculateCutoffTimestamp(period: TimePeriodFilter): Long {
        val cal = Calendar.getInstance()
        return when (period) {
            TimePeriodFilter.TODAY -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            TimePeriodFilter.THIS_WEEK -> {
                System.currentTimeMillis() - 7 * 24 * 3600 * 1000L
            }
            TimePeriodFilter.THIS_MONTH -> {
                System.currentTimeMillis() - 30 * 24 * 3600 * 1000L
            }
            TimePeriodFilter.ALL_TIME -> 0L
        }
    }

    /**
     * Creates a customer order with real-time stock deduction.
     * Rejects (via [writeError]) any order whose quantities exceed available stock.
     * [onSuccess] receives the freshly created [CustomerOrder] (with its generated number)
     * and only runs after the order was persisted, so callers can show a receipt.
     */
    fun createCustomerOrder(
        customerName: String,
        customerPhone: String,
        customerNote: String,
        paymentStatus: PaymentStatus,
        paymentMethod: PaymentMethod,
        items: List<Pair<Product, Int>>,
        onSuccess: (CustomerOrder) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isWriting.value = true
            try {
                val created = repository.createOrder(
                    customerName = customerName,
                    customerPhone = customerPhone,
                    customerNote = customerNote,
                    paymentStatus = paymentStatus,
                    paymentMethod = paymentMethod,
                    items = items
                )
                onSuccess(created)
            } catch (e: IllegalArgumentException) {
                _writeError.value = e.message ?: "Invalid input"
            } catch (e: Exception) {
                _writeError.value = e.message ?: "Something went wrong. Please try again."
            } finally {
                _isWriting.value = false
            }
        }
    }

    /**
     * Advances an order along the PENDING → PREPARING → COMPLETED flow.
     * Completing an unpaid order settles it: marks PAID and records income exactly once.
     */
    fun advanceOrderStatus(orderWithItems: OrderWithItems) = launchWrite {
        val nextStatus = when (orderWithItems.order.status) {
            OrderStatus.PENDING.name -> OrderStatus.PREPARING
            OrderStatus.PREPARING.name -> OrderStatus.COMPLETED
            else -> return@launchWrite
        }
        repository.updateOrderStatus(orderWithItems.order.id, nextStatus)
    }

    /**
     * Manually sets an order's payment status. Marking PAID records income only if
     * no income transaction exists for the order yet (idempotent).
     */
    fun updatePaymentStatus(orderId: Long, status: PaymentStatus) = launchWrite {
        repository.updatePaymentStatus(orderId, status)
    }

    /**
     * Cancels an order: restores the exact stock of every item and, if the order was
     * already paid, creates a compensating negative-income (refund) entry exactly once.
     */
    fun cancelOrder(orderWithItems: OrderWithItems) = launchWrite {
        repository.cancelOrder(orderWithItems)
    }

    // Inventory actions

    /** Inserts or updates a product; [onSuccess] runs after the write completes. */
    fun saveProduct(product: Product, onSuccess: () -> Unit = {}) = launchWrite(onSuccess) {
        repository.saveProduct(product)
    }

    /** Manually sets a product's absolute stock level (never below zero). */
    fun updateStock(productId: Long, newStock: Int) = launchWrite {
        repository.updateStock(productId, newStock.coerceAtLeast(0))
    }

    /**
     * Adds stock to a product and logs a RESTOCKING expense.
     * Quantity must be > 0 and cost must be >= 0; invalid input is ignored.
     */
    fun restockProduct(productId: Long, quantity: Int, totalCost: Double, note: String, onSuccess: () -> Unit = {}) = launchWrite(onSuccess) {
        repository.restockProduct(productId, quantity, totalCost, note)
    }

    /** Permanently deletes a product from the catalog. */
    fun deleteProduct(product: Product) = launchWrite {
        repository.deleteProduct(product)
    }

    // Raw-material stock actions (buy / use / remaining)

    /** Creates or updates a raw ingredient. */
    fun saveRawMaterial(material: RawMaterial, onSuccess: () -> Unit = {}) =
        launchWrite(onSuccess) {
            repository.saveRawMaterial(material)
        }

    /**
     * Records buying raw stock: increases remaining, writes a dated PURCHASE
     * movement and logs a RESTOCKING expense.
     */
    fun purchaseRawMaterial(
        materialId: Long,
        quantity: Double,
        totalCost: Double,
        note: String,
        onSuccess: () -> Unit = {}
    ) = launchWrite(onSuccess) {
        repository.purchaseRawMaterial(materialId, quantity, totalCost, note)
    }

    /**
     * Records consuming raw stock (manual Use). Rejects over-use via [writeError].
     */
    fun useRawMaterial(
        materialId: Long,
        quantity: Double,
        note: String,
        onSuccess: () -> Unit = {}
    ) = launchWrite(onSuccess) {
        repository.useRawMaterial(materialId, quantity, note)
    }

    /** Corrects a raw material to an absolute remaining level. */
    fun adjustRawMaterial(materialId: Long, newStock: Double, note: String) =
        launchWrite {
            repository.adjustRawMaterial(materialId, newStock, note)
        }

    /** Deletes a raw ingredient plus its ledger and recipe links. */
    fun deleteRawMaterial(material: RawMaterial) = launchWrite {
        repository.deleteRawMaterial(material)
    }

    /**
     * Sets auto-deduct linkage: how much raw is consumed per 1 unit of product.
     * Pass qty <= 0 to remove the linkage.
     */
    fun setRecipe(productId: Long, materialId: Long, quantityPerUnit: Double) =
        launchWrite {
            repository.setRecipe(productId, materialId, quantityPerUnit)
        }

    fun movementsForMaterial(materialId: Long) =
        repository.getMovementsForMaterial(materialId)

    // Finance actions

    /** Records a manual income or expense entry not tied to an order. */
    fun addTransaction(
        type: TransactionType,
        category: TransactionCategory,
        amount: Double,
        title: String,
        note: String,
        onSuccess: () -> Unit = {}
    ) = launchWrite(onSuccess) {
        repository.addTransaction(type, category, amount, title, note)
    }

    /**
     * Edits an existing finance entry (amount, title, note, category).
     */
    fun updateTransaction(transaction: Transaction) = launchWrite {
        repository.updateTransaction(transaction)
    }

    /** Deletes a finance entry from the ledger. */
    fun deleteTransaction(transaction: Transaction) = launchWrite {
        repository.deleteTransaction(transaction)
    }
}
