package com.blanccoffee.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blanccoffee.app.data.model.DailyCloseout
import com.blanccoffee.app.data.model.DayRevenue
import com.blanccoffee.app.data.model.Product
import com.blanccoffee.app.data.model.RawMaterial
import com.blanccoffee.app.data.model.toShareText
import com.blanccoffee.app.ui.components.formatCurrency
import com.blanccoffee.app.ui.theme.CoffeePrimary
import com.blanccoffee.app.ui.theme.IncomeGreen
import com.blanccoffee.app.ui.theme.OutcomeRed
import com.blanccoffee.app.ui.theme.WarningOrange

/** Expiry warning banner: expired + expiring-within-7-days across products and raws. */
@Composable
fun ExpiryBanner(
    products: List<Product>,
    rawMaterials: List<RawMaterial>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val now = System.currentTimeMillis()
    val week = 7 * 24 * 3600 * 1000L
    val expired = products.count { it.expiryDate != null && it.expiryDate <= now } +
        rawMaterials.count { it.expiryDate != null && it.expiryDate <= now }
    val expiring = products.count { it.expiryDate != null && it.expiryDate in (now + 1)..(now + week) } +
        rawMaterials.count { it.expiryDate != null && it.expiryDate in (now + 1)..(now + week) }
    if (expired == 0 && expiring == 0) return

    Surface(
        color = (if (expired > 0) OutcomeRed else WarningOrange).copy(alpha = 0.12f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("expiry_banner")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (expired > 0) "⛔" else "⏳",
                fontSize = 20.sp
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = buildString {
                        if (expired > 0) append("$expired expired")
                        if (expired > 0 && expiring > 0) append(" • ")
                        if (expiring > 0) append("$expiring expiring this week")
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (expired > 0) OutcomeRed else WarningOrange
                )
                Text(
                    text = "Check dates in Inventory before selling",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 7-day net-revenue bar chart (pure Compose boxes, no chart dependency). */
@Composable
fun SalesTrendCard(
    trend: List<DayRevenue>,
    modifier: Modifier = Modifier
) {
    val max = (trend.maxOfOrNull { it.revenue } ?: 0.0).coerceAtLeast(1.0)
    val weekTotal = trend.sumOf { it.revenue }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("sales_trend_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📈 7-Day Sales Trend",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = formatCurrency(weekTotal),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                trend.forEach { day ->
                    val fraction = (day.revenue / max).toFloat().coerceIn(0.04f, 1f)
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(fraction, fill = true)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (day.revenue > 0) CoffeePrimary
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = day.label,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/** Today's close-out summary card with a full-report dialog + share. */
@Composable
fun CloseoutCard(
    closeout: DailyCloseout?,
    modifier: Modifier = Modifier
) {
    var showReport by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("closeout_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Assessment,
                        contentDescription = null,
                        tint = CoffeePrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Day Close-Out",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = closeout?.dayLabel ?: "Today",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                OutlinedButton(
                    onClick = { showReport = true },
                    shape = RoundedCornerShape(10.dp),
                    enabled = closeout != null,
                    modifier = Modifier.testTag("btn_view_closeout")
                ) {
                    Text("Report", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CloseoutStat("Net sales", formatCurrency(closeout?.netSales ?: 0.0), CoffeePrimary)
                CloseoutStat("Cash in", "+${formatCurrency(closeout?.incomeCollected ?: 0.0)}", IncomeGreen)
                CloseoutStat("Cash out", "-${formatCurrency(closeout?.expenses ?: 0.0)}", OutcomeRed)
            }

            if ((closeout?.tabsOutstanding ?: 0.0) > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "📒 Tabs outstanding: ${formatCurrency(closeout?.tabsOutstanding ?: 0.0)}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WarningOrange
                )
            }
        }
    }

    if (showReport && closeout != null) {
        CloseoutDialog(closeout = closeout, onDismiss = { showReport = false })
    }
}

@Composable
private fun CloseoutStat(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column {
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = color
        )
    }
}

@Composable
private fun CloseoutDialog(
    closeout: DailyCloseout,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Close-Out • ${closeout.dayLabel}") },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    CloseoutRow("Orders", "${closeout.ordersCount} (avg ${formatCurrency(closeout.avgTicket)})")
                    CloseoutRow("Gross sales", formatCurrency(closeout.grossSales))
                    CloseoutRow("Discounts", "−${formatCurrency(closeout.discounts)}")
                    CloseoutRow("Net sales", formatCurrency(closeout.netSales), bold = true)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    CloseoutRow("Cash in (ledger)", "+${formatCurrency(closeout.incomeCollected)}")
                    CloseoutRow("Cash out (ledger)", "−${formatCurrency(closeout.expenses)}")
                    CloseoutRow("Net cash", formatCurrency(closeout.netCash), bold = true)
                }
                if (closeout.byMethod.isNotEmpty()) {
                    item {
                        Text("By method", fontWeight = FontWeight.Bold, fontSize = 13.sp,
                            modifier = Modifier.padding(top = 6.dp))
                    }
                    items(closeout.byMethod) { m ->
                        CloseoutRow(m.method.replace("_", " "), formatCurrency(m.amount))
                    }
                }
                if (closeout.topProducts.isNotEmpty()) {
                    item {
                        Text("Top sellers", fontWeight = FontWeight.Bold, fontSize = 13.sp,
                            modifier = Modifier.padding(top = 6.dp))
                    }
                    items(closeout.topProducts) { p ->
                        CloseoutRow("${p.productName} x${p.quantity}", formatCurrency(p.revenue))
                    }
                }
                if (closeout.rawUsed.isNotEmpty()) {
                    item {
                        Text("Raw used", fontWeight = FontWeight.Bold, fontSize = 13.sp,
                            modifier = Modifier.padding(top = 6.dp))
                    }
                    items(closeout.rawUsed) { r ->
                        CloseoutRow(r.materialName, "${trimQty(r.quantity)} ${r.unit}")
                    }
                }
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    CloseoutRow("Tabs opened", formatCurrency(closeout.tabsOpened))
                    CloseoutRow("Tabs collected", formatCurrency(closeout.tabsCollected))
                    CloseoutRow("Tabs outstanding", formatCurrency(closeout.tabsOutstanding), bold = true)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "BLANC COFFEE Close-Out ${closeout.dayLabel}")
                        putExtra(Intent.EXTRA_TEXT, closeout.toShareText())
                    }
                    context.startActivity(Intent.createChooser(send, "Share Close-Out via"))
                },
                colors = ButtonDefaults.buttonColors(containerColor = CoffeePrimary),
                modifier = Modifier.testTag("btn_share_closeout")
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Share", fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun CloseoutRow(label: String, value: String, bold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold
        )
    }
}

private fun trimQty(qty: Double): String =
    if (qty == kotlin.math.floor(qty)) "%.0f".format(java.util.Locale.US, qty)
    else "%.2f".format(java.util.Locale.US, qty).trimEnd('0').trimEnd('.')

/** Offline backup card: export a JSON copy, re-import it on this or a new phone. */
@Composable
fun BackupCard(
    onExport: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("backup_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "💾 Shop Data Backup",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Text(
                text = "All data lives on this phone. Export a backup file regularly — restore it here or on a new device.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onExport,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CoffeePrimary),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_export_backup")
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onImport,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_import_backup")
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Restore", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
