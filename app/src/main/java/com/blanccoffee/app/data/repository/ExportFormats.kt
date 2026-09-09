package com.blanccoffee.app.data.repository

import com.blanccoffee.app.data.model.CustomerOrder
import com.blanccoffee.app.data.model.CustomerPayment
import com.blanccoffee.app.data.model.OrderWithItems
import com.blanccoffee.app.data.model.Product
import com.blanccoffee.app.data.model.RawMaterial
import com.blanccoffee.app.data.model.RawMaterialMovement
import com.blanccoffee.app.data.model.RawMovementType
import com.blanccoffee.app.data.model.Transaction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private val csvDateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
private val fileDateFmt = SimpleDateFormat("yyyyMMdd", Locale.US)

fun csvStamp(timestamp: Long): String = csvDateFmt.format(Date(timestamp))

/** RFC-4180 cell escaping (commas/quotes/newlines, incl. Myanmar text). */
fun String.csvCell(): String {
    return if (any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"" + replace("\"", "\"\"") + "\""
    } else this
}

private fun mmkWhole(amount: Double): String = "%.0f".format(Locale.US, amount)

fun transactionsCsv(transactions: List<Transaction>): String = buildString {
    appendLine("id,date,type,category,amount_mmk,title,note,order_ref")
    transactions.forEach { t ->
        appendLine(
            listOf(
                t.id.toString(),
                csvStamp(t.timestamp),
                t.type,
                t.category,
                mmkWhole(t.amount),
                t.title.csvCell(),
                t.note.csvCell(),
                (t.referenceOrderId?.toString() ?: "").csvCell()
            ).joinToString(",")
        )
    }
}

fun ordersCsv(orders: List<OrderWithItems>): String = buildString {
    appendLine(
        "order_number,date,customer,phone,status,payment_status,payment_method," +
            "item_name,item_category,qty,unit_price_mmk,subtotal_mmk," +
            "order_gross_mmk,discount_mmk,discount_reason,order_net_mmk"
    )
    orders.forEach { o ->
        val order = o.order
        if (o.items.isEmpty()) {
            appendLine(orderRow(order, "", "", 0, 0.0, 0.0))
        } else {
            o.items.forEach { item ->
                appendLine(
                    orderRow(
                        order,
                        item.productName,
                        item.category,
                        item.quantity,
                        item.unitPrice,
                        item.subtotal
                    )
                )
            }
        }
    }
}

private fun orderRow(
    order: CustomerOrder,
    itemName: String,
    itemCategory: String,
    qty: Int,
    unitPrice: Double,
    subtotal: Double
): String = listOf(
    order.orderNumber,
    csvStamp(order.createdAt),
    order.customerName.csvCell(),
    order.customerPhone.csvCell(),
    order.status,
    order.paymentStatus,
    order.paymentMethod,
    itemName.csvCell(),
    itemCategory,
    qty.toString(),
    mmkWhole(unitPrice),
    mmkWhole(subtotal),
    mmkWhole(order.totalAmount),
    mmkWhole(order.discountAmount),
    order.discountReason.csvCell(),
    mmkWhole(order.netAmount)
).joinToString(",")

fun productsCsv(products: List<Product>): String = buildString {
    appendLine("id,name,category,sku,unit,stock,min_stock,cost_mmk,price_mmk,margin_pct,expiry_date")
    products.forEach { p ->
        appendLine(
            listOf(
                p.id.toString(),
                p.name.csvCell(),
                p.category,
                p.sku.csvCell(),
                p.unit.csvCell(),
                p.stockQuantity.toString(),
                p.minStockThreshold.toString(),
                mmkWhole(p.costPrice),
                mmkWhole(p.sellingPrice),
                "%.1f".format(Locale.US, p.profitMargin),
                if (p.expiryDate != null) csvStamp(p.expiryDate) else ""
            ).joinToString(",")
        )
    }
}

fun rawMaterialsCsv(
    materials: List<RawMaterial>,
    movements: List<RawMaterialMovement>
): String = buildString {
    appendLine("id,name,sku,unit,bought,used,remaining,min_threshold,cost_per_unit_mmk,expiry_date")
    val boughtBy = movements
        .filter { it.type == RawMovementType.PURCHASE.name }
        .groupBy { it.materialId }
        .mapValues { (_, l) -> l.sumOf { it.quantity } }
    val usedBy = movements
        .filter { it.type == RawMovementType.USAGE.name }
        .groupBy { it.materialId }
        .mapValues { (_, l) -> l.sumOf { it.quantity } }
    materials.forEach { m ->
        appendLine(
            listOf(
                m.id.toString(),
                m.name.csvCell(),
                m.sku.csvCell(),
                m.unit.csvCell(),
                trimQty(boughtBy[m.id] ?: 0.0),
                trimQty(usedBy[m.id] ?: 0.0),
                trimQty(m.stockQuantity),
                trimQty(m.minThreshold),
                mmkWhole(m.costPerUnit),
                if (m.expiryDate != null) csvStamp(m.expiryDate) else ""
            ).joinToString(",")
        )
    }
}

fun paymentsCsv(
    payments: List<CustomerPayment>,
    ordersById: Map<Long, OrderWithItems>
): String = buildString {
    appendLine("id,date,order_number,customer,amount_mmk,method,note")
    payments.forEach { p ->
        val order = ordersById[p.orderId]?.order
        appendLine(
            listOf(
                p.id.toString(),
                csvStamp(p.timestamp),
                (order?.orderNumber ?: "#${p.orderId}").csvCell(),
                (order?.customerName ?: "").csvCell(),
                mmkWhole(p.amount),
                p.method,
                p.note.csvCell()
            ).joinToString(",")
        )
    }
}

/** Packs named text files into a ZIP byte array (stored, no extra dependency). */
fun buildExportZip(files: Map<String, String>): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        files.forEach { (name, content) ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(content.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }
    return out.toByteArray()
}

fun exportFileDate(timestamp: Long): String = fileDateFmt.format(Date(timestamp))

private fun trimQty(qty: Double): String =
    if (qty == kotlin.math.floor(qty)) "%.0f".format(Locale.US, qty)
    else "%.2f".format(Locale.US, qty).trimEnd('0').trimEnd('.')
