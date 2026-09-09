package com.blanccoffee.app.data.model

data class CategorySalesStat(
    val category: ProductCategory,
    val unitsSold: Int,
    val totalRevenue: Double,
    val revenueSharePercent: Float
)

data class SalesPerformanceSummary(
    val totalIncome: Double,
    val totalOutcome: Double,
    val netProfit: Double,
    val profitMarginPercent: Double,
    val completedOrdersCount: Int,
    val pendingOrdersCount: Int,
    val totalProductsCount: Int,
    val lowStockCount: Int,
    val outOfStockCount: Int,
    val categoryStats: List<CategorySalesStat>
)

enum class TimePeriodFilter(val displayName: String) {
    TODAY("Today"),
    THIS_WEEK("7 Days"),
    THIS_MONTH("30 Days"),
    ALL_TIME("All Time")
}
