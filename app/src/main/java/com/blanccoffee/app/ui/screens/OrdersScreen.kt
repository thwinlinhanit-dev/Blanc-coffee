package com.blanccoffee.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.LocalPhone
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.blanccoffee.app.data.model.CustomerOrder
import com.blanccoffee.app.data.model.OrderStatus
import com.blanccoffee.app.data.model.OrderWithItems
import com.blanccoffee.app.data.model.PaymentMethod
import com.blanccoffee.app.data.model.PaymentStatus
import com.blanccoffee.app.data.model.Product
import com.blanccoffee.app.data.model.ProductCategory
import com.blanccoffee.app.ui.ShopViewModel
import com.blanccoffee.app.ui.components.CategoryBadge
import com.blanccoffee.app.ui.components.EmptyStateView
import com.blanccoffee.app.ui.components.OrderStatusBadge
import com.blanccoffee.app.ui.components.PaymentBadge
import com.blanccoffee.app.ui.components.formatCurrency
import com.blanccoffee.app.ui.components.formatDateTime
import com.blanccoffee.app.ui.theme.CoffeePrimary
import com.blanccoffee.app.ui.theme.IncomeGreen
import com.blanccoffee.app.ui.theme.IncomeGreenBg
import com.blanccoffee.app.ui.theme.MacadamiaTertiary
import com.blanccoffee.app.ui.theme.MatchaSecondary
import com.blanccoffee.app.ui.theme.MyanmarCustomerTextStyle
import com.blanccoffee.app.ui.theme.MyanmarFontFamily
import com.blanccoffee.app.ui.theme.MyanmarInputTextStyle
import com.blanccoffee.app.ui.theme.OutcomeRed
import com.blanccoffee.app.ui.theme.WarningOrange
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.runtime.mutableIntStateOf

data class CustomerProfile(
    val name: String,
    val phone: String,
    val orderCount: Int,
    val totalSpent: Double,
    val lastOrderTime: Long,
    val latestNote: String
)

/** Everything needed to render the order slip for a just-created order. */
data class PendingReceipt(
    val order: CustomerOrder,
    val items: List<Pair<Product, Int>>,
    val paymentMethod: PaymentMethod,
    val paymentStatus: PaymentStatus
)

/** One printable line inside an order slip. */
data class ReceiptLine(
    val name: String,
    val qty: Int,
    val amount: Double
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(
    viewModel: ShopViewModel,
    initialOpenCreateDialog: Boolean = false,
    onDialogDismissed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val orders by viewModel.orders.collectAsState()
    val products by viewModel.products.collectAsState()
    val selectedStatusFilter by viewModel.orderStatusFilter.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Orders, 1: Customers
    var searchQuery by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(initialOpenCreateDialog) }
    var prefillCustomerName by remember { mutableStateOf("") }
    var prefillCustomerPhone by remember { mutableStateOf("") }
    /** Order slip to show right after a successful order creation, or null. */
    var pendingReceipt by remember { mutableStateOf<PendingReceipt?>(null) }
    /** Order whose receipt the staff asked to re-share from the order card, or null. */
    var receiptOrder by remember { mutableStateOf<OrderWithItems?>(null) }
    var orderToCancel by remember { mutableStateOf<OrderWithItems?>(null) }

    // Aggregate unique customer profiles from order histories
    val customerProfiles = remember(orders) {
        orders
            .groupBy {
                val name = it.order.customerName.trim().ifBlank { "Walk-in Customer" }
                val phone = it.order.customerPhone.trim()
                "$name||$phone"
            }
            .map { (_, customerOrders) ->
                val firstOrder = customerOrders.maxByOrNull { it.order.createdAt }!!
                val name = firstOrder.order.customerName.trim().ifBlank { "Walk-in Customer" }
                val phone = customerOrders.map { it.order.customerPhone.trim() }.firstOrNull { it.isNotBlank() } ?: ""
                val latestNote = customerOrders.map { it.order.customerNote.trim() }.firstOrNull { it.isNotBlank() } ?: ""
                CustomerProfile(
                    name = name,
                    phone = phone,
                    orderCount = customerOrders.size,
                    totalSpent = customerOrders.sumOf { it.order.totalAmount },
                    lastOrderTime = customerOrders.maxOf { it.order.createdAt },
                    latestNote = latestNote
                )
            }
            .sortedByDescending { it.totalSpent }
    }

    val filteredOrders = orders.filter { orderWithItems ->
        val matchesStatus = selectedStatusFilter == null || orderWithItems.order.status == selectedStatusFilter?.name
        val matchesQuery = searchQuery.isBlank() ||
                orderWithItems.order.customerName.contains(searchQuery, ignoreCase = true) ||
                orderWithItems.order.orderNumber.contains(searchQuery, ignoreCase = true) ||
                orderWithItems.order.customerPhone.contains(searchQuery, ignoreCase = true)
        matchesStatus && matchesQuery
    }

    val filteredCustomers = remember(customerProfiles, searchQuery) {
        if (searchQuery.isBlank()) customerProfiles
        else customerProfiles.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.phone.contains(searchQuery, ignoreCase = true) ||
                    it.latestNote.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        modifier = modifier.testTag("orders_screen"),
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    prefillCustomerName = ""
                    prefillCustomerPhone = ""
                    showCreateDialog = true
                },
                containerColor = CoffeePrimary,
                contentColor = Color.White,
                modifier = Modifier.testTag("fab_create_order")
            ) {
                Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, contentDescription = "Create Order")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Order", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Mode Sub-Navigation: Orders vs Customer Directory
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().testTag("orders_tab_row")
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Orders (${orders.size})",
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    },
                    modifier = Modifier.testTag("tab_orders")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Customers (${customerProfiles.size})",
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    },
                    modifier = Modifier.testTag("tab_customers")
                )
            }

            // Search Bar & Filters
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    textStyle = MyanmarInputTextStyle,
                    placeholder = {
                        Text(
                            text = if (selectedTab == 0) "Search by name, phone or #ORD-100..."
                            else "Search customers by Myanmar / English name, phone...",
                            style = MyanmarInputTextStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_orders_input")
                )

                if (selectedTab == 0) {
                    Spacer(modifier = Modifier.height(10.dp))

                    // Status Filter Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selectedStatusFilter == null,
                            onClick = { viewModel.orderStatusFilter.value = null },
                            label = { Text("All (${orders.size})") },
                            modifier = Modifier.testTag("filter_orders_all")
                        )
                        OrderStatus.entries.forEach { status ->
                            val count = orders.count { it.order.status == status.name }
                            FilterChip(
                                selected = selectedStatusFilter == status,
                                onClick = {
                                    viewModel.orderStatusFilter.value =
                                        if (selectedStatusFilter == status) null else status
                                },
                                label = { Text("${status.displayName} ($count)") },
                                modifier = Modifier.testTag("filter_orders_${status.name.lowercase()}")
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(6.dp))
                    // Customer summary header
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ဖောက်သည်စာရင်း (${customerProfiles.size} total)",
                                fontFamily = MyanmarFontFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Revenue: ${formatCurrency(customerProfiles.sumOf { it.totalSpent })}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = CoffeePrimary
                            )
                        }
                    }
                }
            }

            // Tab Contents
            if (selectedTab == 0) {
                // Orders List
                if (filteredOrders.isEmpty()) {
                    EmptyStateView(
                        icon = Icons.Outlined.ReceiptLong,
                        title = if (searchQuery.isNotEmpty() || selectedStatusFilter != null) "No Orders Match" else "No Customer Orders Yet",
                        message = if (searchQuery.isNotEmpty()) "Try searching with a different name or order number." else "Tap '+ New Order' to log real-time customer sales with automatic inventory deduction."
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = filteredOrders,
                            key = { it.order.id }
                        ) { orderWithItems ->
                            OrderItemCard(
                                orderWithItems = orderWithItems,
                                onAdvanceStatus = { viewModel.advanceOrderStatus(orderWithItems) },
                                onMarkPaid = { viewModel.updatePaymentStatus(orderWithItems.order.id, PaymentStatus.PAID) },
                                onCancelOrder = { orderToCancel = orderWithItems },
                                onShowReceipt = { receiptOrder = orderWithItems }
                            )
                        }
                    }
                }
            } else {
                // Customers List
                if (filteredCustomers.isEmpty()) {
                    EmptyStateView(
                        icon = Icons.Default.People,
                        title = if (searchQuery.isNotEmpty()) "No Customers Match" else "No Customer Records",
                        message = if (searchQuery.isNotEmpty()) "No customers match '$searchQuery'." else "Customer profiles are automatically compiled as orders are recorded."
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize().testTag("customers_list")
                    ) {
                        items(
                            items = filteredCustomers,
                            key = { "${it.name}_${it.phone}" }
                        ) { customer ->
                            CustomerCard(
                                customer = customer,
                                onNewOrder = {
                                    prefillCustomerName = if (customer.name == "Walk-in Customer") "" else customer.name
                                    prefillCustomerPhone = customer.phone
                                    showCreateDialog = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Cancel Order confirmation dialog
    if (orderToCancel != null) {
        AlertDialog(
            onDismissRequest = { orderToCancel = null },
            title = { Text("Cancel Order #${orderToCancel?.order?.orderNumber}?") },
            text = {
                val wasPaid = orderToCancel?.order?.paymentStatus == PaymentStatus.PAID.name
                Text(
                    if (wasPaid) {
                        "This order was already PAID. Cancelling will restore all items back into " +
                                "inventory stock AND record a compensating refund transaction so the " +
                                "finance ledger stays accurate. This cannot be undone."
                    } else {
                        "Cancelling this order will mark it as Cancelled and return all items back into inventory stock."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        orderToCancel?.let { viewModel.cancelOrder(it) }
                        orderToCancel = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OutcomeRed),
                    modifier = Modifier.testTag("confirm_cancel_order_btn")
                ) {
                    Text("Confirm Cancel")
                }
            },
            dismissButton = {
                TextButton(onClick = { orderToCancel = null }) {
                    Text("Keep Order")
                }
            }
        )
    }

    // Create Order Modal Dialog
    if (showCreateDialog) {
        CreateOrderDialog(
            products = products,
            onDismiss = {
                showCreateDialog = false
                onDialogDismissed()
            },
            onCreateOrder = { name, phone, note, payStatus, payMethod, items ->
                viewModel.createCustomerOrder(
                    customerName = name,
                    customerPhone = phone,
                    customerNote = note,
                    paymentStatus = payStatus,
                    paymentMethod = payMethod,
                    items = items,
                    onSuccess = { created ->
                        pendingReceipt = PendingReceipt(
                            order = created,
                            items = items,
                            paymentMethod = payMethod,
                            paymentStatus = payStatus
                        )
                        showCreateDialog = false
                        onDialogDismissed()
                    }
                )
            }
        )
    }

    // Order slip display right after a successful order creation (shareable / printable text)
    pendingReceipt?.let { receipt ->
        ReceiptDialog(
            order = receipt.order,
            items = receipt.items.map { (p, q) -> ReceiptLine(p.name, q, p.sellingPrice * q) },
            paymentMethodName = receipt.paymentMethod.name,
            paymentStatusName = receipt.paymentStatus.name,
            onDismiss = { pendingReceipt = null }
        )
    }

    // Re-share the receipt for an existing order from its order card
    receiptOrder?.let { owed ->
        ReceiptDialog(
            order = owed.order,
            items = owed.items.map { ReceiptLine(it.productName, it.quantity, it.subtotal) },
            paymentMethodName = owed.order.paymentMethod,
            paymentStatusName = owed.order.paymentStatus,
            onDismiss = { receiptOrder = null }
        )
    }
}

@Composable
private fun OrderItemCard(
    orderWithItems: OrderWithItems,
    onAdvanceStatus: () -> Unit,
    onMarkPaid: () -> Unit,
    onCancelOrder: () -> Unit,
    onShowReceipt: () -> Unit,
    modifier: Modifier = Modifier
) {
    val order = orderWithItems.order
    val context = LocalContext.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("order_card_${order.orderNumber}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Card Top Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "#${order.orderNumber}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OrderStatusBadge(status = order.status)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onShowReceipt,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("btn_receipt_${order.orderNumber}")
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Share receipt",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    PaymentBadge(paymentStatus = order.paymentStatus)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Customer details and phone
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = order.customerName.ifBlank { "Walk-in Customer" },
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    if (order.customerPhone.isNotBlank()) {
                        Text(
                            text = order.customerPhone,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (order.customerPhone.isNotBlank()) {
                    IconButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${order.customerPhone}"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = "Call customer",
                            tint = CoffeePrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (order.customerNote.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = "Note: ${order.customerNote}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(10.dp))

            // Itemized list
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                orderWithItems.items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${item.quantity}x",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(28.dp)
                            )
                            Text(
                                text = item.productName,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = formatCurrency(item.subtotal),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(10.dp))

            // Total and payment method footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Payment: ${order.paymentMethod.replace("_", " ")}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatDateTime(order.createdAt),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                Text(
                    text = formatCurrency(order.totalAmount),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Real-time Action Buttons
            if (order.status != OrderStatus.CANCELLED.name) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (order.status) {
                        OrderStatus.PENDING.name -> {
                            Button(
                                onClick = onAdvanceStatus,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_prepare_order_${order.orderNumber}"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CoffeePrimary)
                            ) {
                                Icon(Icons.Default.DeliveryDining, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Start Preparing", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        OrderStatus.PREPARING.name -> {
                            Button(
                                onClick = onAdvanceStatus,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_complete_order_${order.orderNumber}"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = IncomeGreen)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Mark Completed", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        OrderStatus.COMPLETED.name -> {
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                color = IncomeGreenBg
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.DoneAll, contentDescription = null, tint = IncomeGreen, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Order Fulfilled", color = IncomeGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    if (order.paymentStatus != PaymentStatus.PAID.name && order.status != OrderStatus.COMPLETED.name) {
                        OutlinedButton(
                            onClick = onMarkPaid,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("btn_mark_paid_${order.orderNumber}")
                        ) {
                            Text("Collect Pay", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (order.status != OrderStatus.COMPLETED.name) {
                        IconButton(
                            onClick = onCancelOrder,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Cancel,
                                contentDescription = "Cancel order",
                                tint = OutcomeRed.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateOrderDialog(
    products: List<Product>,
    onDismiss: () -> Unit,
    onCreateOrder: (
        customerName: String,
        customerPhone: String,
        customerNote: String,
        paymentStatus: PaymentStatus,
        paymentMethod: PaymentMethod,
        items: List<Pair<Product, Int>>
    ) -> Unit
) {
    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var customerNote by remember { mutableStateOf("") }
    var selectedPaymentMethod by remember { mutableStateOf(PaymentMethod.CASH) }
    var selectedPaymentStatus by remember { mutableStateOf(PaymentStatus.PAID) }

    // Map of productId to quantity
    val itemQuantities = remember { mutableStateMapOf<Long, Int>() }
    var categoryFilter by remember { mutableStateOf<ProductCategory?>(null) }
    var itemSearchQuery by remember { mutableStateOf("") }

    val filteredProducts = products.filter { product ->
        val matchesCat = categoryFilter == null || product.category == categoryFilter?.name
        val matchesQuery = itemSearchQuery.isBlank() || product.name.contains(itemSearchQuery, ignoreCase = true)
        matchesCat && matchesQuery
    }

    val selectedItemsWithProducts = itemQuantities.filter { it.value > 0 }.mapNotNull { entry ->
        val prod = products.firstOrNull { it.id == entry.key }
        if (prod != null) Pair(prod, entry.value) else null
    }

    val totalAmount = selectedItemsWithProducts.sumOf { (prod, qty) -> prod.sellingPrice * qty }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .testTag("create_order_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "New Customer Order",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        OutlinedTextField(
                            value = customerName,
                            onValueChange = { customerName = it },
                            label = { Text("Customer Name (Optional)") },
                            placeholder = { Text("e.g. Alex Johnson") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("order_customer_name_input")
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = customerPhone,
                            onValueChange = { customerPhone = it },
                            label = { Text("Phone Number (Optional)") },
                            placeholder = { Text("+1 (555) 000-0000") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("order_customer_phone_input")
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = customerNote,
                            onValueChange = { customerNote = it },
                            label = { Text("Special Request / Pickup Note") },
                            placeholder = { Text("e.g. Extra hot, Gift box") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Product Selector Section
                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Select Products (${selectedItemsWithProducts.sumOf { it.second }} items in cart)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        // Category Pills
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = categoryFilter == null,
                                onClick = { categoryFilter = null },
                                label = { Text("All") }
                            )
                            ProductCategory.entries.forEach { cat ->
                                FilterChip(
                                    selected = categoryFilter == cat,
                                    onClick = { categoryFilter = if (categoryFilter == cat) null else cat },
                                    label = { Text(cat.displayName.substringBefore(" ")) }
                                )
                            }
                        }
                    }

                    // Product item selection list
                    items(filteredProducts) { prod ->
                        val qty = itemQuantities[prod.id] ?: 0
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = prod.name,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = formatCurrency(prod.sellingPrice),
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 12.sp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Stock: ${prod.stockQuantity} ${prod.unit}",
                                            fontSize = 11.sp,
                                            color = if (prod.stockQuantity <= 0) OutcomeRed else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            if (qty > 0) itemQuantities[prod.id] = qty - 1
                                        },
                                        enabled = qty > 0,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(14.dp))
                                    }

                                    Text(
                                        text = "$qty",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        modifier = Modifier.width(28.dp),
                                        color = if (qty > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )

                                    IconButton(
                                        onClick = {
                                            if (qty < prod.stockQuantity) itemQuantities[prod.id] = qty + 1
                                        },
                                        enabled = qty < prod.stockQuantity,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }

                    // Payment Method & Status
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Payment Method", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            PaymentMethod.entries.forEach { method ->
                                FilterChip(
                                    selected = selectedPaymentMethod == method,
                                    onClick = { selectedPaymentMethod = method },
                                    label = { Text(method.displayName.substringBefore(" /")) }
                                )
                            }
                        }
                    }

                    item {
                        Text("Payment Status", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = selectedPaymentStatus == PaymentStatus.PAID,
                                onClick = { selectedPaymentStatus = PaymentStatus.PAID },
                                label = { Text("Paid Now") }
                            )
                            FilterChip(
                                selected = selectedPaymentStatus == PaymentStatus.UNPAID,
                                onClick = { selectedPaymentStatus = PaymentStatus.UNPAID },
                                label = { Text("Unpaid (On Pickup)") }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Total and Confirm button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Total Amount", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = formatCurrency(totalAmount),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Button(
                        onClick = {
                            onCreateOrder(
                                customerName.ifBlank { "Walk-in Customer" },
                                customerPhone,
                                customerNote,
                                selectedPaymentStatus,
                                selectedPaymentMethod,
                                selectedItemsWithProducts
                            )
                        },
                        enabled = selectedItemsWithProducts.isNotEmpty(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CoffeePrimary),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("confirm_create_order_btn")
                    ) {
                        Icon(Icons.Default.ShoppingBag, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Place Order", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
/**
 * Simple text-based order slip shown right after an order is created.
 *
 * Lets staff instantly share the slip through any messaging app (Viber, Telegram,
 * Messenger, Email...) or a Bluetooth-printing share target, without needing new
 * dependencies. The slip is generated as plain monospaced text for the widest
 * compatibility with share targets and 58mm thermal printers.
 */
@Composable
private fun ReceiptDialog(
    order: CustomerOrder,
    items: List<ReceiptLine>,
    paymentMethodName: String,
    paymentStatusName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val receiptText = remember(order, items, paymentMethodName, paymentStatusName) {
        buildReceiptText(order, items, paymentMethodName, paymentStatusName)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .testTag("receipt_dialog")
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "BLANC COFFEE",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = CoffeePrimary
                        )
                        Text(
                            text = "Order Receipt",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Receipt preview in a monospaced font for a paper-slip look
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = receiptText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "BLANC COFFEE Receipt ${order.orderNumber}")
                                putExtra(Intent.EXTRA_TEXT, receiptText)
                            }
                            context.startActivity(
                                Intent.createChooser(send, "Share Receipt via")
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_share_receipt"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CoffeePrimary)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share Receipt", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_close_receipt"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Done", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/** Builds the shareable, printer-friendly plain-text order slip. */
private fun buildReceiptText(
    order: CustomerOrder,
    items: List<ReceiptLine>,
    paymentMethodName: String,
    paymentStatusName: String
): String = buildString {
    val div = "================================="
    appendLine("          BLANC COFFEE")
    appendLine("       Coffee - Green Tea - Nuts")
    appendLine(div)
    appendLine("Order : ${order.orderNumber}")
    appendLine("Date  : ${formatDateTime(order.createdAt)}")
    appendLine("Cust  : ${order.customerName.ifBlank { "Walk-in Customer" }}")
    appendLine(div)
    items.forEach { line ->
        val namePart = "${line.name} x${line.qty}"
        val amount = formatCurrency(line.amount)
        appendLine(namePart.take(31).padEnd(31) + amount)
    }
    appendLine(div)
    appendLine("TOTAL    : ${formatCurrency(order.totalAmount)}")
    appendLine("Payment  : ${paymentMethodName.replace("_", " ")}" +
        " (${paymentStatusName.uppercase()})")
    appendLine(div)
    append("  Thank you! Please come again.")
}
