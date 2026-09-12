package com.blanccoffee.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blanccoffee.app.data.model.OrderStatus
import com.blanccoffee.app.ui.ShopViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.blanccoffee.app.ui.screens.DashboardScreen
import com.blanccoffee.app.ui.screens.FinanceScreen
import com.blanccoffee.app.ui.screens.InventoryScreen
import com.blanccoffee.app.ui.screens.OrdersScreen
import com.blanccoffee.app.ui.screens.SettingsScreen
import com.blanccoffee.app.ui.theme.CoffeePrimary
import com.blanccoffee.app.ui.theme.MyApplicationTheme

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
    ),
    SETTINGS(
        title = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        testTag = "nav_settings"
    )
}

class MainActivity : ComponentActivity() {
    private val shopViewModel: ShopViewModel by viewModels()

    /** SAF file pickers for offline backup export / restore (no storage permission needed). */
    private val exportBackupDoc =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri == null) return@registerForActivityResult
            lifecycleScope.launch {
                try {
                    val json = withContext(Dispatchers.IO) { shopViewModel.exportBackupNow() }
                    withContext(Dispatchers.IO) {
                        contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(json.toByteArray(Charsets.UTF_8))
                        } ?: throw IllegalStateException("Cannot open file")
                    }
                    Toast.makeText(this@MainActivity, "Backup saved", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(
                        this@MainActivity,
                        "Backup failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

    private val importBackupDoc =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) return@registerForActivityResult
            lifecycleScope.launch {
                val json = try {
                    withContext(Dispatchers.IO) {
                        contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                    }
                } catch (e: Exception) {
                    null
                }
                if (json.isNullOrBlank()) {
                    Toast.makeText(this@MainActivity, "Cannot read file", Toast.LENGTH_LONG).show()
                } else {
                    shopViewModel.importBackup(json) {
                        Toast.makeText(this@MainActivity, "Backup restored", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

    /** Second picker: multi-format spreadsheet bundle (.zip with CSVs + backup + close-out). */
    private val exportSheetsDoc =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            if (uri == null) return@registerForActivityResult
            lifecycleScope.launch {
                try {
                    val bytes = withContext(Dispatchers.IO) { shopViewModel.exportSheetsNow() }
                    withContext(Dispatchers.IO) {
                        contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(bytes)
                        } ?: throw IllegalStateException("Cannot open file")
                    }
                    Toast.makeText(this@MainActivity, "Spreadsheets saved", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(
                        this@MainActivity,
                        "Export failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by shopViewModel.themeMode.collectAsState()
            MyApplicationTheme(
                darkTheme = when (themeMode) {
                    com.blanccoffee.app.data.local.ThemeMode.LIGHT -> false
                    com.blanccoffee.app.data.local.ThemeMode.DARK -> true
                    else -> androidx.compose.foundation.isSystemInDarkTheme()
                }
            ) {
                ShopApp(
                    viewModel = shopViewModel,
                    onExportBackup = {
                        val name = "blanc-coffee-backup-" +
                            SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) + ".json"
                        exportBackupDoc.launch(name)
                    },
                    onExportSheets = {
                        val name = "blanc-coffee-sheets-" +
                            SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) + ".zip"
                        exportSheetsDoc.launch(name)
                    },
                    onPickBackupFile = { importBackupDoc.launch("application/json") }
                )
            }
        }
    }
}

@Composable
fun ShopApp(
    viewModel: ShopViewModel = viewModel(),
    onExportBackup: () -> Unit = {},
    onExportSheets: () -> Unit = {},
    onPickBackupFile: () -> Unit = {},
) {
    var currentDestination by remember { mutableStateOf(ShopDestination.DASHBOARD) }
    var openNewOrderOnOrdersScreen by remember { mutableStateOf(false) }
    var openExpenseOnFinanceScreen by remember { mutableStateOf(false) }

    val orders by viewModel.orders.collectAsState()
    val products by viewModel.products.collectAsState()
    val rawMaterials by viewModel.rawMaterials.collectAsState()
    val isWriting by viewModel.isWriting.collectAsState()
    val oneShotError by viewModel.oneShotError.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Surface any repository write error (e.g. insufficient stock) as a one-shot snackbar.
    LaunchedEffect(oneShotError) {
        oneShotError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    val pendingOrdersCount = orders.count {
        it.order.status == OrderStatus.PENDING.name || it.order.status == OrderStatus.PREPARING.name
    }
    val lowStockCount = products.count { it.isLowStock || it.isOutOfStock } +
        rawMaterials.count { it.isLowStock || it.isOutOfStock }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
        Column(modifier = Modifier.fillMaxSize()) {
            // Subtle global loading bar while a database write is in progress
            if (isWriting) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("global_writing_indicator")
                )
            }
            Box(modifier = Modifier
                .weight(1f)
                .padding(innerPadding)) {
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
                        },
                        onExportBackup = onExportBackup,
                        onExportSheets = onExportSheets,
                        onPickBackupFile = onPickBackupFile
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
                    ShopDestination.SETTINGS -> SettingsScreen(
                        viewModel = viewModel,
                        onExportBackup = onExportBackup,
                        onExportSheets = onExportSheets,
                        onPickBackupFile = onPickBackupFile
                    )
                }
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
