package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CategorySalesStat
import com.example.data.model.ProductCategory
import com.example.data.model.SalesPerformanceSummary
import com.example.ui.theme.CoffeePrimary
import com.example.ui.theme.CoffeePrimaryLight
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.IncomeGreenBg
import com.example.ui.theme.MacadamiaTertiary
import com.example.ui.theme.MatchaSecondary
import com.example.ui.theme.OutcomeRed
import com.example.ui.theme.OutcomeRedBg
import com.example.ui.theme.WarningOrange
import com.example.ui.theme.WarningOrangeBg

@Composable
fun NetProfitHeroCard(
    summary: SalesPerformanceSummary,
    modifier: Modifier = Modifier
) {
    val isPositive = summary.netProfit >= 0
    val gradientColors = if (isPositive) {
        listOf(CoffeePrimary, Color(0xFF2E4528))
    } else {
        listOf(CoffeePrimary, Color(0xFF5A1E1E))
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("net_profit_hero_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .background(Brush.horizontalGradient(gradientColors))
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "REAL-TIME NET PROFIT",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.2f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isPositive) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                contentDescription = null,
                                tint = if (isPositive) Color(0xFFA5D6A7) else Color(0xFFFFCDD2),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = String.format(java.util.Locale.US, "%.1f%% margin", summary.profitMarginPercent),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = formatCurrency(summary.netProfit),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Breakdown row inside hero
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Income
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFA5D6A7))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Total Income",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.75f)
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "+${formatCurrency(summary.totalIncome)}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFA5D6A7)
                        )
                    }

                    // Outcome / Expenses
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFFCDD2))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Total Outcome",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.75f)
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "-${formatCurrency(summary.totalOutcome)}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFCDD2)
                        )
                    }

                    // Orders Completed
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Orders Done",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.75f)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${summary.completedOrdersCount} orders",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryPerformanceCard(
    categoryStats: List<CategorySalesStat>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("category_performance_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Product Sales Performance",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "By Product Line",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Multi-segment progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    categoryStats.forEach { stat ->
                        val weight = (stat.revenueSharePercent / 100f).coerceIn(0.01f, 1f)
                        val barColor = when (stat.category) {
                            ProductCategory.COFFEE -> CoffeePrimary
                            ProductCategory.GREEN_TEA -> MatchaSecondary
                            ProductCategory.MACADAMIA_NUT -> MacadamiaTertiary
                        }
                        Box(
                            modifier = Modifier
                                .weight(weight)
                                .fillMaxHeight()
                                .background(barColor)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Legend rows
            categoryStats.forEach { stat ->
                val (icon, color, name) = when (stat.category) {
                    ProductCategory.COFFEE -> Triple(Icons.Default.Coffee, CoffeePrimary, "Coffee Products")
                    ProductCategory.GREEN_TEA -> Triple(Icons.Default.Spa, MatchaSecondary, "Green Tea")
                    ProductCategory.MACADAMIA_NUT -> Triple(Icons.Default.LocalFlorist, MacadamiaTertiary, "Macadamia Nut")
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(color.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = color,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = name,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "${stat.unitsSold} units sold",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = formatCurrency(stat.totalRevenue),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = String.format(java.util.Locale.US, "%.1f%% share", stat.revenueSharePercent),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = color
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InventoryAlertBanner(
    lowStockCount: Int,
    outOfStockCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (lowStockCount == 0 && outOfStockCount == 0) return

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("inventory_alert_banner"),
        shape = RoundedCornerShape(12.dp),
        color = if (outOfStockCount > 0) OutcomeRedBg else WarningOrangeBg,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = if (outOfStockCount > 0) OutcomeRed else WarningOrange,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Inventory Level Notice",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (outOfStockCount > 0) OutcomeRed else WarningOrange
                )
                Text(
                    text = buildString {
                        if (outOfStockCount > 0) append("$outOfStockCount item out of stock! ")
                        if (lowStockCount > 0) append("$lowStockCount items below reorder threshold.")
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "Restock →",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = if (outOfStockCount > 0) OutcomeRed else WarningOrange
            )
        }
    }
}
