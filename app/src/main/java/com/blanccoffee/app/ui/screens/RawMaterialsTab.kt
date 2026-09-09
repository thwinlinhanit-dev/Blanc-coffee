package com.blanccoffee.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.blanccoffee.app.data.model.Product
import com.blanccoffee.app.data.model.ProductRecipe
import com.blanccoffee.app.data.model.RawMaterial
import com.blanccoffee.app.data.model.RawMaterialMovement
import com.blanccoffee.app.data.model.RawMovementType
import com.blanccoffee.app.ui.ShopViewModel
import com.blanccoffee.app.ui.components.EmptyStateView
import com.blanccoffee.app.ui.components.formatCurrency
import com.blanccoffee.app.ui.components.formatDateTime
import com.blanccoffee.app.ui.theme.CoffeePrimary
import com.blanccoffee.app.ui.theme.IncomeGreen
import com.blanccoffee.app.ui.theme.OutcomeRed
import com.blanccoffee.app.ui.theme.WarningOrange
import java.util.Locale

/** Formats raw quantities: whole numbers without decimals, else up to 2 decimals. */
fun formatRawQty(qty: Double, unit: String): String {
    val q = if (qty == kotlin.math.floor(qty) && !qty.isInfinite()) {
        String.format(Locale.US, "%.0f", qty)
    } else {
        String.format(Locale.US, "%.2f", qty).trimEnd('0').trimEnd('.')
    }
    return "$q $unit"
}

@Composable
fun RawMaterialsTab(
    viewModel: ShopViewModel,
    modifier: Modifier = Modifier
) {
    val rawMaterials by viewModel.rawMaterials.collectAsState()
    val movements by viewModel.rawMovements.collectAsState()
    val products by viewModel.products.collectAsState()
    val recipes by viewModel.recipes.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var materialToEdit by remember { mutableStateOf<RawMaterial?>(null) }
    var materialToBuy by remember { mutableStateOf<RawMaterial?>(null) }
    var materialToUse by remember { mutableStateOf<RawMaterial?>(null) }
    var materialToDelete by remember { mutableStateOf<RawMaterial?>(null) }
    var historyMaterial by remember { mutableStateOf<RawMaterial?>(null) }
    var recipeMaterial by remember { mutableStateOf<RawMaterial?>(null) }

    val lowCount = rawMaterials.count { it.isLowStock || it.isOutOfStock }

    Scaffold(
        modifier = modifier.testTag("raw_materials_tab"),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = CoffeePrimary,
                contentColor = Color.White,
                modifier = Modifier.testTag("fab_add_raw")
            ) {
                Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, contentDescription = "Add raw material")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Raw", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (lowCount > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "⚠ $lowCount raw ingredient${if (lowCount == 1) "" else "s"} low / out of stock",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            if (rawMaterials.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Default.Inventory,
                    title = "No Raw Ingredients Yet",
                    message = "Tap \"Add Raw\" to track e.g. raw macadamia bags — buying date, qty, used & remaining."
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(rawMaterials, key = { it.id }) { material ->
                        val matMovements = movements.filter { it.materialId == material.id }
                        val bought = matMovements
                            .filter { it.type == RawMovementType.PURCHASE.name }
                            .sumOf { it.quantity }
                        val used = matMovements
                            .filter { it.type == RawMovementType.USAGE.name }
                            .sumOf { it.quantity }
                        val linkedCount = recipes.count { it.materialId == material.id }
                        RawMaterialCard(
                            material = material,
                            totalBought = bought,
                            totalUsed = used,
                            linkedProductCount = linkedCount,
                            onBuy = { materialToBuy = material },
                            onUse = { materialToUse = material },
                            onHistory = { historyMaterial = material },
                            onRecipes = { recipeMaterial = material },
                            onEdit = { materialToEdit = material },
                            onDelete = { materialToDelete = material }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog || materialToEdit != null) {
        AddEditRawMaterialDialog(
            existing = materialToEdit,
            onDismiss = { showAddDialog = false; materialToEdit = null },
            onConfirm = { material ->
                viewModel.saveRawMaterial(material) {
                    showAddDialog = false
                    materialToEdit = null
                }
            }
        )
    }

    if (materialToBuy != null) {
        BuyRawDialog(
            material = materialToBuy!!,
            onDismiss = { materialToBuy = null },
            onConfirm = { qty, cost, note ->
                viewModel.purchaseRawMaterial(materialToBuy!!.id, qty, cost, note) {
                    materialToBuy = null
                }
            }
        )
    }

    if (materialToUse != null) {
        UseRawDialog(
            material = materialToUse!!,
            onDismiss = { materialToUse = null },
            onConfirm = { qty, note ->
                viewModel.useRawMaterial(materialToUse!!.id, qty, note) {
                    materialToUse = null
                }
            }
        )
    }

    if (materialToDelete != null) {
        AlertDialog(
            onDismissRequest = { materialToDelete = null },
            title = { Text("Delete ${materialToDelete?.name}?") },
            text = { Text("This removes the ingredient, its buy/use history and all product recipe links. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        materialToDelete?.let { viewModel.deleteRawMaterial(it) }
                        materialToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OutcomeRed),
                    modifier = Modifier.testTag("confirm_delete_raw_btn")
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { materialToDelete = null }) { Text("Cancel") }
            }
        )
    }

    if (historyMaterial != null) {
        val matMovements = movements.filter { it.materialId == historyMaterial!!.id }
        RawHistoryDialog(
            material = historyMaterial!!,
            movements = matMovements,
            onDismiss = { historyMaterial = null }
        )
    }

    if (recipeMaterial != null) {
        val links = recipes.filter { it.materialId == recipeMaterial!!.id }
        RecipeLinkDialog(
            material = recipeMaterial!!,
            products = products,
            existingLinks = links,
            onDismiss = { recipeMaterial = null },
            onSave = { productId, qtyPerUnit ->
                viewModel.setRecipe(productId, recipeMaterial!!.id, qtyPerUnit)
            }
        )
    }
}

@Composable
private fun RawMaterialCard(
    material: RawMaterial,
    totalBought: Double,
    totalUsed: Double,
    linkedProductCount: Int,
    onBuy: () -> Unit,
    onUse: () -> Unit,
    onHistory: () -> Unit,
    onRecipes: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val badgeText: String
    val badgeBg: Color
    val badgeFg: Color
    when {
        material.isOutOfStock -> {
            badgeText = "● Out of stock"
            badgeBg = OutcomeRed.copy(alpha = 0.12f)
            badgeFg = OutcomeRed
        }
        material.isLowStock -> {
            badgeText = "⚠ Low: ${formatRawQty(material.stockQuantity, material.unit)}"
            badgeBg = WarningOrange.copy(alpha = 0.15f)
            badgeFg = WarningOrange
        }
        else -> {
            badgeText = formatRawQty(material.stockQuantity, material.unit)
            badgeBg = IncomeGreen.copy(alpha = 0.12f)
            badgeFg = IncomeGreen
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("raw_card_${material.id}"),
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = material.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    if (material.sku.isNotBlank()) {
                        Text(
                            text = material.sku,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(shape = RoundedCornerShape(8.dp), color = badgeBg) {
                    Text(
                        text = badgeText,
                        color = badgeFg,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bought / Used / Remaining strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Bought", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        formatRawQty(totalBought, material.unit),
                        fontWeight = FontWeight.Bold, fontSize = 14.sp, color = IncomeGreen
                    )
                }
                Column {
                    Text("Used", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        formatRawQty(totalUsed, material.unit),
                        fontWeight = FontWeight.Bold, fontSize = 14.sp, color = OutcomeRed
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Remaining", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        formatRawQty(material.stockQuantity, material.unit),
                        fontWeight = FontWeight.ExtraBold, fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (material.note.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = material.note,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (linkedProductCount > 0) {
                    "🔗 Auto-deducts from $linkedProductCount product${if (linkedProductCount == 1) "" else "s"} on each sale"
                } else {
                    "Manual tracking only — link to products for auto-deduct"
                },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onBuy,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CoffeePrimary),
                    modifier = Modifier.testTag("btn_buy_raw_${material.id}")
                ) {
                    Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Buy", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onUse,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.testTag("btn_use_raw_${material.id}")
                ) {
                    Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Use", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onHistory, modifier = Modifier.testTag("btn_history_raw_${material.id}")) {
                    Icon(Icons.Default.History, contentDescription = "Buy/use history")
                }
                IconButton(onClick = onRecipes) {
                    Icon(Icons.Default.Link, contentDescription = "Link to products (auto-deduct)")
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.DeleteOutline, contentDescription = "Delete",
                        modifier = Modifier.size(18.dp), tint = OutcomeRed.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
private fun AddEditRawMaterialDialog(
    existing: RawMaterial?,
    onDismiss: () -> Unit,
    onConfirm: (RawMaterial) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var unit by remember { mutableStateOf(existing?.unit ?: "bags") }
    var stockText by remember {
        mutableStateOf(
            if (existing == null) "0"
            else if (existing.stockQuantity == kotlin.math.floor(existing.stockQuantity)) {
                String.format(Locale.US, "%.0f", existing.stockQuantity)
            } else existing.stockQuantity.toString()
        )
    }
    var minText by remember {
        mutableStateOf(
            if (existing == null) "5"
            else if (existing.minThreshold == kotlin.math.floor(existing.minThreshold)) {
                String.format(Locale.US, "%.0f", existing.minThreshold)
            } else existing.minThreshold.toString()
        )
    }
    var costText by remember {
        mutableStateOf(
            if (existing == null) "" else String.format(Locale.US, "%.0f", existing.costPerUnit)
        )
    }
    var sku by remember { mutableStateOf(existing?.sku ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }

    val valid = name.isNotBlank() &&
        (stockText.toDoubleOrNull() ?: -1.0) >= 0 &&
        (minText.toDoubleOrNull() ?: -1.0) >= 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add Raw Ingredient" else "Edit ${existing.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Name (e.g. Raw Macadamia Kernels)") },
                    singleLine = true, shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = unit, onValueChange = { unit = it },
                        label = { Text("Unit (bags, kg, pcs)") },
                        singleLine = true, shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = sku, onValueChange = { sku = it },
                        label = { Text("SKU (optional)") },
                        singleLine = true, shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = stockText, onValueChange = { stockText = it },
                        label = { Text("Opening stock") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true, shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = minText, onValueChange = { minText = it },
                        label = { Text("Low-stock alert at") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true, shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = costText, onValueChange = { costText = it },
                    label = { Text("Default cost per unit (MMK, optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("Supplier / note (optional)") },
                    singleLine = true, shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        (existing ?: RawMaterial(name = name.trim())).copy(
                            name = name.trim(),
                            unit = unit.trim().ifBlank { "units" },
                            stockQuantity = stockText.toDoubleOrNull() ?: 0.0,
                            minThreshold = minText.toDoubleOrNull() ?: 5.0,
                            costPerUnit = costText.toDoubleOrNull() ?: 0.0,
                            sku = sku.trim(),
                            note = note.trim()
                        )
                    )
                },
                enabled = valid,
                colors = ButtonDefaults.buttonColors(containerColor = CoffeePrimary)
            ) { Text(if (existing == null) "Add" else "Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun BuyRawDialog(
    material: RawMaterial,
    onDismiss: () -> Unit,
    onConfirm: (qty: Double, totalCost: Double, note: String) -> Unit
) {
    var qtyText by remember { mutableStateOf("10") }
    var costText by remember {
        mutableStateOf(
            if (material.costPerUnit > 0) {
                String.format(Locale.US, "%.0f", 10 * material.costPerUnit)
            } else ""
        )
    }
    var note by remember { mutableStateOf("Supplier delivery") }

    val qty = qtyText.toDoubleOrNull() ?: 0.0
    val cost = costText.toDoubleOrNull() ?: 0.0
    val valid = qty > 0 && cost >= 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buy: ${material.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Remaining now: ${formatRawQty(material.stockQuantity, material.unit)}",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = qtyText, onValueChange = { qtyText = it },
                    label = { Text("Quantity bought (${material.unit}) — date = today") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("buy_raw_qty_input")
                )
                OutlinedTextField(
                    value = costText, onValueChange = { costText = it },
                    label = { Text("Total cost (MMK) → expense") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("buy_raw_cost_input")
                )
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("Supplier / batch note") },
                    singleLine = true, shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(qty, cost, note) },
                enabled = valid,
                colors = ButtonDefaults.buttonColors(containerColor = CoffeePrimary),
                modifier = Modifier.testTag("confirm_buy_raw_btn")
            ) { Text("Confirm Buy") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun UseRawDialog(
    material: RawMaterial,
    onDismiss: () -> Unit,
    onConfirm: (qty: Double, note: String) -> Unit
) {
    var qtyText by remember { mutableStateOf("1") }
    var note by remember { mutableStateOf("Production batch") }
    val qty = qtyText.toDoubleOrNull() ?: 0.0
    val valid = qty > 0 && qty <= material.stockQuantity

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Use: ${material.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Remaining now: ${formatRawQty(material.stockQuantity, material.unit)}",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = qtyText, onValueChange = { qtyText = it },
                    label = { Text("Quantity used (${material.unit})") },
                    supportingText = {
                        if (!valid) Text(
                            "Must be > 0 and ≤ ${formatRawQty(material.stockQuantity, material.unit)}",
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    isError = !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("use_raw_qty_input")
                )
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("Reason (roast batch, spillage…)") },
                    singleLine = true, shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(qty, note) },
                enabled = valid,
                colors = ButtonDefaults.buttonColors(containerColor = CoffeePrimary),
                modifier = Modifier.testTag("confirm_use_raw_btn")
            ) { Text("Confirm Use") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun RawHistoryDialog(
    material: RawMaterial,
    movements: List<RawMaterialMovement>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${material.name} — history") },
        text = {
            if (movements.isEmpty()) {
                Text("No buy/use entries yet. Tap Buy to record the first purchase.")
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(movements, key = { it.id }) { m ->
                        val icon = when (m.type) {
                            RawMovementType.PURCHASE.name -> "🟢"
                            RawMovementType.USAGE.name -> "🔴"
                            else -> "⚪"
                        }
                        val kind = when (m.type) {
                            RawMovementType.PURCHASE.name -> "Bought"
                            RawMovementType.USAGE.name -> "Used"
                            else -> "Adjusted"
                        }
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "$icon $kind ${formatRawQty(m.quantity, material.unit)}",
                                    fontWeight = FontWeight.Bold, fontSize = 13.sp
                                )
                                if (m.totalCost > 0) {
                                    Text(
                                        formatCurrency(m.totalCost),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                "${formatDateTime(m.timestamp)}${if (m.note.isNotBlank()) " • ${m.note}" else ""}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(top = 8.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun RecipeLinkDialog(
    material: RawMaterial,
    products: List<Product>,
    existingLinks: List<ProductRecipe>,
    onDismiss: () -> Unit,
    onSave: (productId: Long, qtyPerUnit: Double) -> Unit
) {
    val qtyByProduct = remember(existingLinks) {
        mutableStateOf(existingLinks.associate { it.productId to it.quantityPerUnit.toString() }.toMutableMap())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Auto-deduct: ${material.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "How much ${material.unit} is used to make 1 unit of each product? " +
                        "Selling that product auto-deducts raw stock. Leave blank/0 to unlink.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(products, key = { it.id }) { product ->
                        var text by remember(product.id, existingLinks) {
                            mutableStateOf(
                                existingLinks.firstOrNull { it.productId == product.id }
                                    ?.quantityPerUnit?.toString() ?: ""
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(product.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Sells per ${product.unit}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedTextField(
                                value = text,
                                onValueChange = {
                                    text = it
                                    qtyByProduct.value[product.id] = it
                                },
                                placeholder = { Text("0") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.width(90.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    qtyByProduct.value.forEach { (productId, text) ->
                        val qty = text.toDoubleOrNull() ?: 0.0
                        val had = existingLinks.any { it.productId == productId }
                        if (qty > 0 || had) onSave(productId, qty)
                    }
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = CoffeePrimary)
            ) { Text("Save links") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
