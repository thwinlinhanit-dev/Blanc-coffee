package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.Product
import com.example.data.model.ProductCategory
import com.example.ui.ShopViewModel
import com.example.ui.components.CategoryBadge
import com.example.ui.components.EmptyStateView
import com.example.ui.components.EntryDialog
import com.example.ui.components.EntryType
import com.example.ui.components.StockBadge
import com.example.ui.components.StockQuickAdjuster
import com.example.ui.components.formatCurrency
import com.example.ui.theme.CoffeePrimary
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.OutcomeRed
import com.example.ui.theme.WarningOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    viewModel: ShopViewModel,
    modifier: Modifier = Modifier
) {
    val products by viewModel.products.collectAsState()
    var selectedCategoryFilter by remember { mutableStateOf<ProductCategory?>(null) }
    var showOnlyLowStock by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    var productToEdit by remember { mutableStateOf<Product?>(null) }
    var showAddProductDialog by remember { mutableStateOf(false) }
    var productToRestock by remember { mutableStateOf<Product?>(null) }
    var productToDelete by remember { mutableStateOf<Product?>(null) }

    val filteredProducts = products.filter { product ->
        val matchesCategory = selectedCategoryFilter == null || product.category == selectedCategoryFilter?.name
        val matchesLowStock = !showOnlyLowStock || product.isLowStock || product.isOutOfStock
        val matchesSearch = searchQuery.isBlank() ||
                product.name.contains(searchQuery, ignoreCase = true) ||
                product.sku.contains(searchQuery, ignoreCase = true)
        matchesCategory && matchesLowStock && matchesSearch
    }

    val lowStockCount = products.count { it.isLowStock || it.isOutOfStock }

    Scaffold(
        modifier = modifier.testTag("inventory_screen"),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddProductDialog = true },
                containerColor = CoffeePrimary,
                contentColor = Color.White,
                modifier = Modifier.testTag("fab_add_product")
            ) {
                Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, contentDescription = "Add Product")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Item", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search Bar & Filter Row
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search coffee, green tea, macadamia...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_inventory_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Category Chips Row
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategoryFilter == null && !showOnlyLowStock,
                            onClick = {
                                selectedCategoryFilter = null
                                showOnlyLowStock = false
                            },
                            label = { Text("All (${products.size})") }
                        )
                    }

                    items(ProductCategory.entries) { cat ->
                        val count = products.count { it.category == cat.name }
                        FilterChip(
                            selected = selectedCategoryFilter == cat && !showOnlyLowStock,
                            onClick = {
                                selectedCategoryFilter = if (selectedCategoryFilter == cat) null else cat
                                showOnlyLowStock = false
                            },
                            label = { Text("${cat.displayName} ($count)") },
                            modifier = Modifier.testTag("filter_cat_${cat.name.lowercase()}")
                        )
                    }

                    item {
                        FilterChip(
                            selected = showOnlyLowStock,
                            onClick = {
                                showOnlyLowStock = !showOnlyLowStock
                                if (showOnlyLowStock) selectedCategoryFilter = null
                            },
                            label = { Text("⚠ Low Stock ($lowStockCount)") },
                            modifier = Modifier.testTag("filter_inventory_low_stock")
                        )
                    }
                }
            }

            if (filteredProducts.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Default.Inventory,
                    title = "No Products Found",
                    message = if (searchQuery.isNotEmpty() || showOnlyLowStock) "No inventory items match the selected filters." else "No products found in the catalog."
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = filteredProducts,
                        key = { it.id }
                    ) { product ->
                        ProductInventoryCard(
                            product = product,
                            onStockAdjust = { newStock -> viewModel.updateStock(product.id, newStock) },
                            onRestock = { productToRestock = product },
                            onEdit = { productToEdit = product },
                            onDelete = { productToDelete = product }
                        )
                    }
                }
            }
        }
    }

    // Add or Edit Product Dialog via reusable EntryDialog
    if (showAddProductDialog || productToEdit != null) {
        EntryDialog(
            viewModel = viewModel,
            initialType = EntryType.PRODUCT,
            allowedTypes = setOf(EntryType.PRODUCT),
            existingProduct = productToEdit,
            onDismiss = {
                showAddProductDialog = false
                productToEdit = null
            }
        )
    }

    // Restock Dialog (adds inventory + records outcome expense)
    if (productToRestock != null) {
        RestockProductDialog(
            product = productToRestock!!,
            onDismiss = { productToRestock = null },
            onConfirmRestock = { qty, totalCost, note ->
                viewModel.restockProduct(productToRestock!!.id, qty, totalCost, note)
                productToRestock = null
            }
        )
    }

    // Delete Product Confirmation Dialog
    if (productToDelete != null) {
        AlertDialog(
            onDismissRequest = { productToDelete = null },
            title = { Text("Delete ${productToDelete?.name}?") },
            text = { Text("This will permanently remove this product from your inventory catalog.") },
            confirmButton = {
                Button(
                    onClick = {
                        productToDelete?.let { viewModel.deleteProduct(it) }
                        productToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OutcomeRed),
                    modifier = Modifier.testTag("confirm_delete_product_btn")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { productToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ProductInventoryCard(
    product: Product,
    onStockAdjust: (Int) -> Unit,
    onRestock: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("product_card_${product.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Category Badge + SKU + Stock Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryBadge(categoryName = product.category)
                    if (product.sku.isNotBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = product.sku,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                StockBadge(
                    quantity = product.stockQuantity,
                    minThreshold = product.minStockThreshold,
                    unit = product.unit
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Product Name and Description
            Text(
                text = product.name,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )

            if (product.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = product.description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(12.dp))

            // Financial Info (Cost, Price, Profit Margin)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Selling Price",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatCurrency(product.sellingPrice),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Column {
                    Text(
                        text = "Cost Price",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatCurrency(product.costPrice),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Margin",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = String.format(java.util.Locale.US, "%.1f%%", product.profitMargin),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = IncomeGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Controls Row: Quick Stock Adjuster + Restock + Edit/Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StockQuickAdjuster(
                    currentStock = product.stockQuantity,
                    onAdjust = onStockAdjust
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = onRestock,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CoffeePrimary),
                        modifier = Modifier.testTag("btn_restock_${product.id}")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Restock", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit product",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Delete product",
                            modifier = Modifier.size(18.dp),
                            tint = OutcomeRed.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RestockProductDialog(
    product: Product,
    onDismiss: () -> Unit,
    onConfirmRestock: (qty: Int, totalCost: Double, note: String) -> Unit
) {
    var quantityText by remember { mutableStateOf("10") }
    var totalCostText by remember {
        val estimatedCost = (10 * product.costPrice)
        mutableStateOf(String.format(java.util.Locale.US, "%.2f", estimatedCost))
    }
    var note by remember { mutableStateOf("Supplier delivery") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restock: ${product.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Current Stock: ${product.stockQuantity} ${product.unit}",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = quantityText,
                    onValueChange = {
                        quantityText = it
                        val q = it.toIntOrNull() ?: 0
                        totalCostText = String.format(java.util.Locale.US, "%.2f", q * product.costPrice)
                    },
                    label = { Text("Quantity to Add (${product.unit})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("restock_quantity_input")
                )

                OutlinedTextField(
                    value = totalCostText,
                    onValueChange = { totalCostText = it },
                    label = { Text("Total Expense Cost (MMK)") },
                    supportingText = { Text("Automatically logs an Outcome transaction") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("restock_cost_input")
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Supplier / Invoice Note") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val qty = quantityText.toIntOrNull() ?: 0
                    val cost = totalCostText.toDoubleOrNull() ?: 0.0
                    if (qty > 0) {
                        onConfirmRestock(qty, cost, note)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CoffeePrimary),
                modifier = Modifier.testTag("confirm_restock_btn")
            ) {
                Text("Confirm Restock")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
