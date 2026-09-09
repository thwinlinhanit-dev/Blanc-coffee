package com.blanccoffee.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blanccoffee.app.data.model.OrderStatus
import com.blanccoffee.app.data.model.PaymentStatus
import com.blanccoffee.app.data.model.ProductCategory
import com.blanccoffee.app.ui.theme.CoffeePrimary
import com.blanccoffee.app.ui.theme.IncomeGreen
import com.blanccoffee.app.ui.theme.IncomeGreenBg
import com.blanccoffee.app.ui.theme.MacadamiaTertiary
import com.blanccoffee.app.ui.theme.MatchaSecondary
import com.blanccoffee.app.ui.theme.OutcomeRed
import com.blanccoffee.app.ui.theme.OutcomeRedBg
import com.blanccoffee.app.ui.theme.PendingBlue
import com.blanccoffee.app.ui.theme.PendingBlueBg
import com.blanccoffee.app.ui.theme.WarningOrange
import com.blanccoffee.app.ui.theme.WarningOrangeBg
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Formats an amount as Myanmar Kyat (MMK). MMK has no practical subunit in daily shop use,
 * so amounts are always shown as grouped whole numbers, e.g. "64,000 MMK".
 */
fun formatCurrency(amount: Double): String {
    val format = NumberFormat.getNumberInstance(Locale.US).apply {
        maximumFractionDigits = 0
        minimumFractionDigits = 0
        isGroupingUsed = true
    }
    return "${format.format(kotlin.math.round(amount))} MMK"
}

fun formatDateTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

fun formatDateOnly(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@Composable
fun CategoryBadge(categoryName: String, modifier: Modifier = Modifier) {
    val category = ProductCategory.fromString(categoryName)
    val (bgColor, textColor, icon) = when (category) {
        ProductCategory.COFFEE -> Triple(
            Color(0xFFEFE8E1),
            CoffeePrimary,
            Icons.Default.Coffee
        )
        ProductCategory.GREEN_TEA -> Triple(
            Color(0xFFE2EFE0),
            MatchaSecondary,
            Icons.Default.Spa
        )
        ProductCategory.MACADAMIA_NUT -> Triple(
            Color(0xFFFBF1DE),
            MacadamiaTertiary,
            Icons.Default.LocalFlorist
        )
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = category.displayName,
                color = textColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun OrderStatusBadge(status: String, modifier: Modifier = Modifier) {
    val (bgColor, textColor, icon) = when (status) {
        OrderStatus.PENDING.name -> Triple(
            PendingBlueBg,
            PendingBlue,
            Icons.Default.HourglassTop
        )
        OrderStatus.PREPARING.name -> Triple(
            WarningOrangeBg,
            WarningOrange,
            Icons.Default.Coffee
        )
        OrderStatus.COMPLETED.name -> Triple(
            IncomeGreenBg,
            IncomeGreen,
            Icons.Default.CheckCircle
        )
        OrderStatus.CANCELLED.name -> Triple(
            OutcomeRedBg,
            OutcomeRed,
            Icons.Outlined.Cancel
        )
        else -> Triple(Color(0xFFEEEEEE), Color.Gray, Icons.Default.HourglassTop)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = status.replace("_", " "),
                color = textColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun PaymentBadge(paymentStatus: String, modifier: Modifier = Modifier) {
    val isPaid = paymentStatus == PaymentStatus.PAID.name
    val isRefunded = paymentStatus == PaymentStatus.REFUNDED.name
    val (bgColor, textColor) = when {
        isPaid -> Pair(IncomeGreenBg, IncomeGreen)
        isRefunded -> Pair(OutcomeRedBg, OutcomeRed)
        else -> Pair(WarningOrangeBg, WarningOrange)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = bgColor
    ) {
        Text(
            text = paymentStatus,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun StockBadge(quantity: Int, minThreshold: Int, unit: String, modifier: Modifier = Modifier) {
    val isOut = quantity <= 0
    val isLow = !isOut && quantity <= minThreshold
    val (bgColor, textColor, label, icon) = when {
        isOut -> Quad(OutcomeRedBg, OutcomeRed, "Out of Stock", Icons.Default.Error)
        isLow -> Quad(WarningOrangeBg, WarningOrange, "Low: $quantity $unit", Icons.Default.Warning)
        else -> Quad(IncomeGreenBg, IncomeGreen, "$quantity $unit", Icons.Default.CheckCircle)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        // Out-of-stock gets a visible border so it cannot be missed at a glance.
        border = if (isOut) BorderStroke(1.5.dp, OutcomeRed) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = if (isOut) "Out of stock" else if (isLow) "Low stock" else null,
                tint = textColor,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = textColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** Simple immutable holder of four related values (used by [StockBadge]). */
private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
fun StockQuickAdjuster(
    currentStock: Int,
    onAdjust: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = { if (currentStock > 0) onAdjust(currentStock - 1) },
            enabled = currentStock > 0,
            modifier = Modifier
                .size(36.dp)
                .testTag("adjust_stock_minus"),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = "Decrease stock",
                modifier = Modifier.size(16.dp)
            )
        }

        Text(
            text = "$currentStock",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            modifier = Modifier
                .width(44.dp)
                .padding(horizontal = 4.dp),
            textAlign = TextAlign.Center
        )

        IconButton(
            onClick = { onAdjust(currentStock + 1) },
            modifier = Modifier
                .size(36.dp)
                .testTag("adjust_stock_plus"),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Increase stock",
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun EmptyStateView(
    icon: ImageVector = Icons.Outlined.Inbox,
    title: String,
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
