package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.OrderStatus
import com.example.ui.ShopViewModel
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.FinanceScreen
import com.example.ui.screens.InventoryScreen
import com.example.ui.screens.OrdersScreen
import com.example.ui.theme.CoffeePrimary
import com.example.ui.theme.MyApplicationTheme

enum class ShopDestination(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    DASHBOARD(
        title = "Dashboard",
        selectedIcon = Icons.Filled.Dashboard,
        unselectedIcon = Icons.Outlined.Dashboard,
        testTag = "nav_dashboard"
    ),
    ORDERS(
        title = "Orders",
        selectedIcon = Icons.Filled.ShoppingCart,
        unselectedIcon = Icons.Outlined.ShoppingCart,
        testTag = "nav_orders"
    ),
    INVENTORY(
        title = "Inventory",
        selectedIcon = Icons.Filled.Inventory2,
        unselectedIcon = Icons.Outlined.Inventory2,
        testTag = "nav_inventory"
    ),
    FINANCE(
        title = "Finance",
        selectedIcon = Icons.Filled.Payments,
        unselectedIcon = Icons.Outlined.Payments,
        testTag = "nav_finance"
    )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                ShopApp()
            }
        }
    }
}

@Composable
fun ShopApp(viewModel: ShopViewModel = viewModel()) {
    var currentDestination by remember { mutableStateOf(ShopDestination.DASHBOARD) }
    var openNewOrderOnOrdersScreen by remember { mutableStateOf(false) }
    var openExpenseOnFinanceScreen by remember { mutableStateOf(false) }

    val orders by viewModel.orders.collectAsState()
    val products by viewModel.products.collectAsState()

    val pendingOrdersCount = orders.count {
        it.order.status == OrderStatus.PENDING.name || it.order.status == OrderStatus.PREPARING.name
    }
    val lowStockCount = products.count { it.isLowStock || it.isOutOfStock }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier.testTag("main_navigation_bar")
            ) {
                ShopDestination.entries.forEach { destination ->
                    val isSelected = currentDestination == destination

                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentDestination = destination },
                        icon = {
                            when (destination) {
                                ShopDestination.ORDERS -> {
                                    BadgedBox(
                                        badge = {
                                            if (pendingOrdersCount > 0) {
                                                Badge { Text("$pendingOrdersCount") }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                                            contentDescription = destination.title
                                        )
                                    }
                                }
                                ShopDestination.INVENTORY -> {
                                    BadgedBox(
                                        badge = {
                                            if (lowStockCount > 0) {
                                                Badge(containerColor = MaterialTheme.colorScheme.error) {
                                                    Text("$lowStockCount")
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                                            contentDescription = destination.title
                                        )
                                    }
                                }
                                else -> {
                                    Icon(
                                        imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                                        contentDescription = destination.title
                                    )
                                }
                            }
                        },
                        label = {
                            Text(
                                text = destination.title,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag(destination.testTag)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (currentDestination) {
                ShopDestination.DASHBOARD -> DashboardScreen(
                    viewModel = viewModel,
                    onNavigateToOrders = { openNewOrderDialog ->
                        openNewOrderOnOrdersScreen = openNewOrderDialog
                        currentDestination = ShopDestination.ORDERS
                    },
                    onNavigateToInventory = {
                        currentDestination = ShopDestination.INVENTORY
                    },
                    onNavigateToFinance = { openExpenseDialog ->
                        openExpenseOnFinanceScreen = openExpenseDialog
                        currentDestination = ShopDestination.FINANCE
                    }
                )
                ShopDestination.ORDERS -> OrdersScreen(
                    viewModel = viewModel,
                    initialOpenCreateDialog = openNewOrderOnOrdersScreen,
                    onDialogDismissed = { openNewOrderOnOrdersScreen = false }
                )
                ShopDestination.INVENTORY -> InventoryScreen(
                    viewModel = viewModel
                )
                ShopDestination.FINANCE -> FinanceScreen(
                    viewModel = viewModel,
                    initialOpenExpenseDialog = openExpenseOnFinanceScreen,
                    onDialogDismissed = { openExpenseOnFinanceScreen = false }
                )
            }
        }
    }
}

// Keep Greeting for backward-compatibility with GreetingScreenshotTest
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MyApplicationTheme { Greeting("Android") }
}
