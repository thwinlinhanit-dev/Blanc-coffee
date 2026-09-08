package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.di.DatabaseModule
import com.example.data.local.AppDatabase
import com.example.data.model.CategorySalesStat
import com.example.data.model.CustomerOrder
import com.example.data.model.OrderStatus
import com.example.data.model.OrderWithItems
import com.example.data.model.PaymentMethod
import com.example.data.model.PaymentStatus
import com.example.data.model.Product
import com.example.data.model.ProductCategory
import com.example.data.model.SalesPerformanceSummary
import com.example.data.model.TimePeriodFilter
import com.example.data.model.Transaction
import com.example.data.model.TransactionCategory
import com.example.data.model.TransactionType
import com.example.data.repository.ShopRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class ShopViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DatabaseModule.provideShopRepository(application)

    val products: StateFlow<List<Product>> = repository.allProducts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val orders: StateFlow<List<OrderWithItems>> = repository.allOrders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val transactions: StateFlow<List<Transaction>> = repository.allTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lowStockProducts: StateFlow<List<Product>> = repository.lowStockProducts
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

    // Customer Order actions
    fun createCustomerOrder(
        customerName: String,
        customerPhone: String,
        customerNote: String,
        paymentStatus: PaymentStatus,
        paymentMethod: PaymentMethod,
        items: List<Pair<Product, Int>>,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.createOrder(
                customerName = customerName,
                customerPhone = customerPhone,
                customerNote = customerNote,
                paymentStatus = paymentStatus,
                paymentMethod = paymentMethod,
                items = items
            )
            onSuccess()
        }
    }

    fun advanceOrderStatus(orderWithItems: OrderWithItems) {
        viewModelScope.launch {
            val nextStatus = when (orderWithItems.order.status) {
                OrderStatus.PENDING.name -> OrderStatus.PREPARING
                OrderStatus.PREPARING.name -> OrderStatus.COMPLETED
                else -> return@launch
            }
            repository.updateOrderStatus(orderWithItems.order.id, nextStatus)
        }
    }

    fun updatePaymentStatus(orderId: Long, status: PaymentStatus) {
        viewModelScope.launch {
            repository.updatePaymentStatus(orderId, status)
        }
    }

    fun cancelOrder(orderWithItems: OrderWithItems) {
        viewModelScope.launch {
            repository.cancelOrder(orderWithItems)
        }
    }

    // Inventory actions
    fun saveProduct(product: Product, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            repository.saveProduct(product)
            onSuccess()
        }
    }

    fun updateStock(productId: Long, newStock: Int) {
        viewModelScope.launch {
            repository.updateStock(productId, newStock.coerceAtLeast(0))
        }
    }

    fun restockProduct(productId: Long, quantity: Int, totalCost: Double, note: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            repository.restockProduct(productId, quantity, totalCost, note)
            onSuccess()
        }
    }

    fun deleteProduct(product: Product) {
        viewModelScope.launch {
            repository.deleteProduct(product)
        }
    }

    // Finance actions
    fun addTransaction(
        type: TransactionType,
        category: TransactionCategory,
        amount: Double,
        title: String,
        note: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.addTransaction(type, category, amount, title, note)
            onSuccess()
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
        }
    }
}
