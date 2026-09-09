package com.blanccoffee.app.data.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Revenue bucket for one calendar day (sales-trend chart). */
data class DayRevenue(
    val dayStart: Long,
    val label: String,
    val revenue: Double
)

/** Money collected through one payment method during the close-out day. */
data class MethodTotal(
    val method: String,
    val amount: Double
)

/** One product's sales during the close-out day. */
data class ProductSales(
    val productName: String,
    val quantity: Int,
    val revenue: Double
)

/** Raw ingredient consumed during the close-out day (manual + auto-deduct). */
data class RawUsage(
    val materialName: String,
    val unit: String,
    val quantity: Double
)

/**
 * End-of-day Z-report. All money is MMK.
 *
 * - [grossSales]/[discounts]/[netSales] come from non-cancelled orders
 *   *created* that day (what was sold, net of discounts).
 * - [incomeCollected]/[expenses] come from ledger transactions *timestamped*
 *   that day (what cash actually moved — tab payments land here on the day
 *   the customer pays, not the day they bought).
 */
data class DailyCloseout(
    val dayStart: Long,
    val dayLabel: String,
    val grossSales: Double,
    val discounts: Double,
    val netSales: Double,
    val incomeCollected: Double,
    val expenses: Double,
    val netCash: Double,
    val ordersCount: Int,
    val avgTicket: Double,
    val byMethod: List<MethodTotal>,
    val topProducts: List<ProductSales>,
    val rawUsed: List<RawUsage>,
    val tabsOpened: Double,
    val tabsCollected: Double,
    val tabsOutstanding: Double
)

/** Midnight (00:00) of the day containing [timestamp], in device timezone. */
fun startOfDay(timestamp: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = timestamp
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

fun formatDayLabel(timestamp: Long): String =
    SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(timestamp))

fun formatDayShort(timestamp: Long): String =
    SimpleDateFormat("EEE", Locale.getDefault()).format(Date(timestamp))

/**
 * Pure close-out computation — no database access, so it is unit-testable.
 *
 * @param paidByOrder maps orderId -> total paid so far (customer_payments sums).
 */
fun computeCloseout(
    dayStart: Long,
    dayEnd: Long,
    orders: List<OrderWithItems>,
    transactions: List<Transaction>,
    movements: List<RawMaterialMovement>,
    rawMaterials: List<RawMaterial>,
    payments: List<CustomerPayment>,
    paidByOrder: Map<Long, Double>
): DailyCloseout {
    val dayOrders = orders.filter {
        it.order.createdAt in dayStart until dayEnd &&
            it.order.status != OrderStatus.CANCELLED.name
    }
    val gross = dayOrders.sumOf { it.order.totalAmount }
    val discounts = dayOrders.sumOf { it.order.discountAmount }
    val net = dayOrders.sumOf { it.order.netAmount }

    val dayIncome = transactions
        .filter { it.timestamp in dayStart until dayEnd && it.type == TransactionType.INCOME.name }
        .sumOf { it.amount }
    val dayExpenses = transactions
        .filter { it.timestamp in dayStart until dayEnd && it.type == TransactionType.OUTCOME.name }
        .sumOf { it.amount }

    // Method split: paid-at-sale orders (by their method) + tab payments (by theirs).
    val methodMap = mutableMapOf<String, Double>()
    dayOrders
        .filter { it.order.paymentStatus == PaymentStatus.PAID.name }
        .forEach { methodMap[it.order.paymentMethod] = (methodMap[it.order.paymentMethod] ?: 0.0) + it.order.netAmount }
    payments
        .filter { it.timestamp in dayStart until dayEnd }
        .forEach { methodMap[it.method] = (methodMap[it.method] ?: 0.0) + it.amount }

    val productMap = mutableMapOf<String, Pair<Int, Double>>()
    dayOrders.flatMap { it.items }.forEach { item ->
        val (q, r) = productMap[item.productName] ?: (0 to 0.0)
        productMap[item.productName] = (q + item.quantity) to (r + item.subtotal)
    }
    val topProducts = productMap
        .map { (name, qr) -> ProductSales(name, qr.first, qr.second) }
        .sortedByDescending { it.revenue }
        .take(5)

    val rawById = rawMaterials.associateBy { it.id }
    val usageMap = mutableMapOf<Long, Double>()
    movements
        .filter { it.timestamp in dayStart until dayEnd && it.type == RawMovementType.USAGE.name }
        .forEach { usageMap[it.materialId] = (usageMap[it.materialId] ?: 0.0) + it.quantity }
    val rawUsed = usageMap.mapNotNull { (id, qty) ->
        rawById[id]?.let { RawUsage(it.name, it.unit, qty) }
    }.sortedByDescending { it.quantity }

    val tabsOpened = dayOrders
        .filter { it.order.paymentStatus == PaymentStatus.UNPAID.name }
        .sumOf { it.order.netAmount - (paidByOrder[it.order.id] ?: 0.0) }
    val tabsCollected = payments
        .filter { it.timestamp in dayStart until dayEnd }
        .sumOf { it.amount }
    val tabsOutstanding = orders
        .filter {
            it.order.status != OrderStatus.CANCELLED.name &&
                it.order.paymentStatus == PaymentStatus.UNPAID.name
        }
        .sumOf { (it.order.netAmount - (paidByOrder[it.order.id] ?: 0.0)).coerceAtLeast(0.0) }

    return DailyCloseout(
        dayStart = dayStart,
        dayLabel = formatDayLabel(dayStart),
        grossSales = gross,
        discounts = discounts,
        netSales = net,
        incomeCollected = dayIncome,
        expenses = dayExpenses,
        netCash = dayIncome - dayExpenses,
        ordersCount = dayOrders.size,
        avgTicket = if (dayOrders.isNotEmpty()) net / dayOrders.size else 0.0,
        byMethod = methodMap.map { (m, a) -> MethodTotal(m, a) }.sortedByDescending { it.amount },
        topProducts = topProducts,
        rawUsed = rawUsed,
        tabsOpened = tabsOpened,
        tabsCollected = tabsCollected,
        tabsOutstanding = tabsOutstanding
    )
}

/** Net revenue per day for the last [days] days (oldest → newest), for the trend chart. */
fun computeRevenueTrend(
    now: Long,
    days: Int,
    orders: List<OrderWithItems>
): List<DayRevenue> {
    val todayStart = startOfDay(now)
    val day = 24 * 3600 * 1000L
    return (days - 1 downTo 0).map { back ->
        val start = todayStart - back * day
        val revenue = orders
            .filter {
                it.order.status != OrderStatus.CANCELLED.name &&
                    it.order.createdAt in start until (start + day)
            }
            .sumOf { it.order.netAmount }
        DayRevenue(start, formatDayShort(start), revenue)
    }
}

/** Share-ready plain-text close-out (Viber/Telegram/Bluetooth-print targets). */
fun DailyCloseout.toShareText(): String {
    val sb = StringBuilder()
    sb.appendLine("BLANC COFFEE — DAY CLOSE-OUT")
    sb.appendLine(dayLabel)
    sb.appendLine("------------------------------")
    sb.appendLine("Orders: $ordersCount")
    sb.appendLine("Gross sales: ${mmk(grossSales)}")
    sb.appendLine("Discounts: -${mmk(discounts)}")
    sb.appendLine("Net sales: ${mmk(netSales)}")
    sb.appendLine("Cash in (ledger): +${mmk(incomeCollected)}")
    sb.appendLine("Cash out (ledger): -${mmk(expenses)}")
    sb.appendLine("Net cash: ${mmk(netCash)}")
    if (byMethod.isNotEmpty()) {
        sb.appendLine("------------------------------")
        sb.appendLine("By method:")
        byMethod.forEach { sb.appendLine("- ${it.method}: ${mmk(it.amount)}") }
    }
    if (topProducts.isNotEmpty()) {
        sb.appendLine("------------------------------")
        sb.appendLine("Top sellers:")
        topProducts.forEach { sb.appendLine("- ${it.productName} x${it.quantity} = ${mmk(it.revenue)}") }
    }
    if (rawUsed.isNotEmpty()) {
        sb.appendLine("------------------------------")
        sb.appendLine("Raw used:")
        rawUsed.forEach { sb.appendLine("- ${it.materialName}: ${trimQty(it.quantity)} ${it.unit}") }
    }
    sb.appendLine("------------------------------")
    sb.appendLine("Tabs opened: ${mmk(tabsOpened)}")
    sb.appendLine("Tabs collected: ${mmk(tabsCollected)}")
    sb.appendLine("Tabs outstanding: ${mmk(tabsOutstanding)}")
    return sb.toString().trimEnd()
}

private fun mmk(amount: Double): String {
    val grouped = "%,.0f".format(Locale.US, kotlin.math.round(amount))
    return "$grouped MMK"
}

private fun trimQty(qty: Double): String =
    if (qty == kotlin.math.floor(qty)) "%.0f".format(Locale.US, qty)
    else "%.2f".format(Locale.US, qty).trimEnd('0').trimEnd('.')
