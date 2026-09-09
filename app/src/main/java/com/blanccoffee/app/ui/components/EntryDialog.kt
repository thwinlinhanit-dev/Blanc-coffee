package com.blanccoffee.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.blanccoffee.app.data.model.Product
import com.blanccoffee.app.data.model.ProductCategory
import com.blanccoffee.app.data.model.TransactionCategory
import com.blanccoffee.app.data.model.TransactionType
import com.blanccoffee.app.ui.ShopViewModel
import com.blanccoffee.app.ui.theme.CoffeePrimary
import com.blanccoffee.app.ui.theme.IncomeGreen
import com.blanccoffee.app.ui.theme.IncomeGreenBg
import com.blanccoffee.app.ui.theme.MacadamiaTertiary
import com.blanccoffee.app.ui.theme.MatchaSecondary
import com.blanccoffee.app.ui.theme.OutcomeRed
import com.blanccoffee.app.ui.theme.OutcomeRedBg

/**
 * Types of business ledger entries that can be recorded.
 */
enum class EntryType(val displayName: String) {
    INCOME("Income"),
    EXPENSE("Expense"),
    PRODUCT("Product")
}

/**
 * Result emitted when an entry is successfully validated and confirmed.
 */
sealed class EntryResult {
    data class Income(
        val category: TransactionCategory,
        val amount: Double,
        val title: String,
        val note: String
    ) : EntryResult()

    data class Expense(
        val category: TransactionCategory,
        val amount: Double,
        val title: String,
        val note: String
    ) : EntryResult()

    data class ProductEntry(
        val product: Product
    ) : EntryResult()
}

/**
 * High-level overload of [EntryDialog] that directly writes entries to the Room database
 * via [ShopViewModel].
 */
@Composable
fun EntryDialog(
    viewModel: ShopViewModel,
    initialType: EntryType = EntryType.INCOME,
    allowedTypes: Set<EntryType> = setOf(EntryType.INCOME, EntryType.EXPENSE, EntryType.PRODUCT),
    existingProduct: Product? = null,
    onDismiss: () -> Unit,
    onSuccess: (EntryResult) -> Unit = {}
) {
    EntryDialog(
        initialType = initialType,
        allowedTypes = allowedTypes,
        existingProduct = existingProduct,
        onDismiss = onDismiss,
        onConfirm = { result ->
            when (result) {
                is EntryResult.Income -> {
                    viewModel.addTransaction(
                        type = TransactionType.INCOME,
                        category = result.category,
                        amount = result.amount,
                        title = result.title,
                        note = result.note,
                        onSuccess = { onSuccess(result) }
                    )
                }
                is EntryResult.Expense -> {
                    viewModel.addTransaction(
                        type = TransactionType.OUTCOME,
                        category = result.category,
                        amount = result.amount,
                        title = result.title,
                        note = result.note,
                        onSuccess = { onSuccess(result) }
                    )
                }
                is EntryResult.ProductEntry -> {
                    viewModel.saveProduct(
                        product = result.product,
                        onSuccess = { onSuccess(result) }
                    )
                }
            }
            onDismiss()
        }
    )
}

/**
 * Reusable input dialog component for adding new income, expense, or product entries
 * into the local Room database, featuring real-time validation for amount and numeric fields.
 */
@Composable
fun EntryDialog(
    initialType: EntryType = EntryType.INCOME,
    allowedTypes: Set<EntryType> = setOf(EntryType.INCOME, EntryType.EXPENSE, EntryType.PRODUCT),
    existingProduct: Product? = null,
    onDismiss: () -> Unit,
    onConfirm: (EntryResult) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedType by remember {
        mutableStateOf(
            if (existingProduct != null) EntryType.PRODUCT
            else if (allowedTypes.contains(initialType)) initialType
            else allowedTypes.first()
        )
    }

    // --- State for Income / Expense ---
    var transactionAmountText by remember { mutableStateOf("") }
    var transactionTitle by remember { mutableStateOf("") }
    var transactionNote by remember { mutableStateOf("") }
    var selectedIncomeCategory by remember { mutableStateOf(TransactionCategory.ORDER_SALE) }
    var selectedExpenseCategory by remember { mutableStateOf(TransactionCategory.RESTOCKING) }

    // --- State for Product ---
    var productName by remember { mutableStateOf(existingProduct?.name ?: "") }
    var productCategory by remember {
        mutableStateOf(
            existingProduct?.let { ProductCategory.fromString(it.category) } ?: ProductCategory.COFFEE
        )
    }
    var stockQuantityText by remember { mutableStateOf(existingProduct?.stockQuantity?.toString() ?: "0") }
    var unit by remember { mutableStateOf(existingProduct?.unit ?: "bags") }
    var costPriceText by remember {
        mutableStateOf(
            existingProduct?.costPrice?.let {
                if (it % 1.0 == 0.0) it.toLong().toString() else it.toString()
            } ?: ""
        )
    }
    var sellingPriceText by remember {
        mutableStateOf(
            existingProduct?.sellingPrice?.let {
                if (it % 1.0 == 0.0) it.toLong().toString() else it.toString()
            } ?: ""
        )
    }
    var minThresholdText by remember {
        mutableStateOf(existingProduct?.minStockThreshold?.toString() ?: "10")
    }
    var sku by remember { mutableStateOf(existingProduct?.sku ?: "") }
    var productDescription by remember { mutableStateOf(existingProduct?.description ?: "") }
    var expiryText by remember {
        mutableStateOf(
            existingProduct?.expiryDate?.let {
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                    .format(java.util.Date(it))
            } ?: ""
        )
    }
    // Made-to-order: produced fresh per order, skips finished-stock tracking.
    var madeToOrder by remember { mutableStateOf(existingProduct?.madeToOrder ?: false) }

    /** Parses an optional YYYY-MM-DD date; null when blank. */
    fun parseExpiryOrNull(text: String): Long? {
        val t = text.trim()
        if (t.isEmpty()) return null
        return try {
            val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            fmt.isLenient = false
            fmt.parse(t)?.time
        } catch (e: Exception) {
            null
        }
    }

    // --- Validation Error States ---
    var hasAttemptedSubmit by remember { mutableStateOf(false) }

    // Validation logic for Income/Expense Amount
    val amountValidationResult = remember(transactionAmountText) {
        val text = transactionAmountText.trim()
        when {
            text.isEmpty() -> "Amount is required"
            text.toDoubleOrNull() == null -> "Enter a valid numeric amount"
            (text.toDoubleOrNull() ?: 0.0) <= 0.0 -> "Amount must be greater than 0 MMK"
            else -> null
        }
    }

    val titleValidationResult = remember(transactionTitle) {
        if (transactionTitle.trim().isEmpty()) "Title / Description is required" else null
    }

    // Validation logic for Product Fields
    val productNameValidationResult = remember(productName) {
        if (productName.trim().isEmpty()) "Product name is required" else null
    }

    val productStockValidationResult = remember(stockQuantityText) {
        val text = stockQuantityText.trim()
        when {
            text.isEmpty() -> "Stock quantity is required"
            text.toIntOrNull() == null -> "Enter a valid whole number"
            (text.toIntOrNull() ?: 0) < 0 -> "Stock quantity cannot be negative"
            else -> null
        }
    }

    val productCostValidationResult = remember(costPriceText) {
        val text = costPriceText.trim()
        when {
            text.isEmpty() -> "Cost price is required"
            text.toDoubleOrNull() == null -> "Enter a valid numeric price"
            (text.toDoubleOrNull() ?: 0.0) < 0.0 -> "Cost price cannot be negative"
            else -> null
        }
    }

    val productSellingPriceValidationResult = remember(sellingPriceText) {
        val text = sellingPriceText.trim()
        when {
            text.isEmpty() -> "Selling price is required"
            text.toDoubleOrNull() == null -> "Enter a valid numeric price"
            (text.toDoubleOrNull() ?: 0.0) <= 0.0 -> "Selling price must be greater than 0 MMK"
            else -> null
        }
    }

    val productMinThresholdValidationResult = remember(minThresholdText) {
        val text = minThresholdText.trim()
        if (text.isNotEmpty() && (text.toIntOrNull() == null || (text.toIntOrNull() ?: 0) < 0)) {
            "Must be a positive whole number"
        } else null
    }

    // Overall Validity Check
    val isFormValid = when (selectedType) {
        EntryType.INCOME, EntryType.EXPENSE ->
            amountValidationResult == null && titleValidationResult == null
        EntryType.PRODUCT ->
            productNameValidationResult == null &&
                    productStockValidationResult == null &&
                    productCostValidationResult == null &&
                    productSellingPriceValidationResult == null &&
                    productMinThresholdValidationResult == null &&
                    (expiryText.isBlank() || parseExpiryOrNull(expiryText) != null)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .testTag("reusable_entry_dialog")
                .testTag("entry_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = when (selectedType) {
                                EntryType.INCOME -> "Record Income"
                                EntryType.EXPENSE -> "Record Expense"
                                EntryType.PRODUCT -> if (existingProduct == null) "New Product" else "Edit Product"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "BLANC COFFEE Local Ledger",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("entry_dialog_close")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close dialog")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Type Toggle Bar (Only shown if more than one type is allowed and not editing existing product)
                if (allowedTypes.size > 1 && existingProduct == null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (allowedTypes.contains(EntryType.INCOME)) {
                            EntryTypeSelectorTab(
                                type = EntryType.INCOME,
                                isSelected = selectedType == EntryType.INCOME,
                                activeColor = IncomeGreen,
                                icon = Icons.Default.TrendingUp,
                                onClick = { selectedType = EntryType.INCOME },
                                modifier = Modifier.weight(1f).testTag("dialog_tab_income")
                            )
                        }

                        if (allowedTypes.contains(EntryType.EXPENSE)) {
                            EntryTypeSelectorTab(
                                type = EntryType.EXPENSE,
                                isSelected = selectedType == EntryType.EXPENSE,
                                activeColor = OutcomeRed,
                                icon = Icons.Default.TrendingDown,
                                onClick = { selectedType = EntryType.EXPENSE },
                                modifier = Modifier.weight(1f).testTag("dialog_tab_expense")
                            )
                        }

                        if (allowedTypes.contains(EntryType.PRODUCT)) {
                            EntryTypeSelectorTab(
                                type = EntryType.PRODUCT,
                                isSelected = selectedType == EntryType.PRODUCT,
                                activeColor = CoffeePrimary,
                                icon = Icons.Default.Coffee,
                                onClick = { selectedType = EntryType.PRODUCT },
                                modifier = Modifier.weight(1f).testTag("dialog_tab_product")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Form Content
                LazyColumn(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (selectedType) {
                        EntryType.INCOME, EntryType.EXPENSE -> {
                            // Amount Field with Real-Time Validation
                            item {
                                val amountVal = transactionAmountText.toDoubleOrNull() ?: 0.0
                                val isError = hasAttemptedSubmit && amountValidationResult != null

                                OutlinedTextField(
                                    value = transactionAmountText,
                                    onValueChange = { input ->
                                        if (input.isEmpty() || input.matches(Regex("""^\d*\.?\d*$"""))) {
                                            transactionAmountText = input
                                        }
                                    },
                                    label = { Text("Amount (MMK) *") },
                                    placeholder = { Text("e.g. 50000") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Payments,
                                            contentDescription = null,
                                            tint = if (selectedType == EntryType.INCOME) IncomeGreen else OutcomeRed
                                        )
                                    },
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Decimal,
                                        imeAction = ImeAction.Next
                                    ),
                                    singleLine = true,
                                    isError = isError,
                                    supportingText = {
                                        if (isError) {
                                            Text(
                                                text = amountValidationResult ?: "",
                                                color = MaterialTheme.colorScheme.error,
                                                fontSize = 12.sp,
                                                modifier = Modifier.testTag("amount_error_text")
                                            )
                                        } else if (amountVal > 0) {
                                            Text(
                                                text = "Preview: ${formatCurrency(amountVal)}",
                                                color = if (selectedType == EntryType.INCOME) IncomeGreen else OutcomeRed,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 12.sp
                                            )
                                        } else {
                                            Text(
                                                text = "Enter positive amount in Myanmar Kyat",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("entry_amount_input")
                                        .testTag("tx_amount_input")
                                )
                            }

                            // Title / Source Field
                            item {
                                val isError = hasAttemptedSubmit && titleValidationResult != null

                                OutlinedTextField(
                                    value = transactionTitle,
                                    onValueChange = { transactionTitle = it },
                                    label = {
                                        Text(
                                            if (selectedType == EntryType.INCOME) "Title / Income Source *"
                                            else "Title / Payee & Purpose *"
                                        )
                                    },
                                    placeholder = {
                                        Text(
                                            if (selectedType == EntryType.INCOME) "e.g. Catering for Gallery Event"
                                            else "e.g. Coffee bean batch restocking"
                                        )
                                    },
                                    singleLine = true,
                                    isError = isError,
                                    supportingText = if (isError) {
                                        {
                                            Text(
                                                text = titleValidationResult ?: "",
                                                color = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.testTag("title_error_text")
                                            )
                                        }
                                    } else null,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("entry_title_input")
                                        .testTag("tx_title_input")
                                )
                            }

                            // Category Selector
                            item {
                                val categories = TransactionCategory.entries.filter {
                                    if (selectedType == EntryType.INCOME) it.type == TransactionType.INCOME && it != TransactionCategory.ORDER_REFUND
                                    else it.type == TransactionType.OUTCOME
                                }

                                Text(
                                    text = "Category",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(categories) { category ->
                                        val isSelected = if (selectedType == EntryType.INCOME) {
                                            selectedIncomeCategory == category
                                        } else {
                                            selectedExpenseCategory == category
                                        }

                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                if (selectedType == EntryType.INCOME) {
                                                    selectedIncomeCategory = category
                                                } else {
                                                    selectedExpenseCategory = category
                                                }
                                            },
                                            label = { Text(category.displayName, fontSize = 12.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = if (selectedType == EntryType.INCOME) IncomeGreen else OutcomeRed,
                                                selectedLabelColor = Color.White
                                            ),
                                            modifier = Modifier.testTag("category_chip_${category.name.lowercase()}")
                                        )
                                    }
                                }
                            }

                            // Note / Reference Field
                            item {
                                OutlinedTextField(
                                    value = transactionNote,
                                    onValueChange = { transactionNote = it },
                                    label = { Text("Invoice / Notes (Optional)") },
                                    placeholder = { Text("Reference invoice #, supplier name, etc.") },
                                    maxLines = 2,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("entry_note_input")
                                )
                            }
                        }

                        EntryType.PRODUCT -> {
                            // Product Name
                            item {
                                val isError = hasAttemptedSubmit && productNameValidationResult != null
                                OutlinedTextField(
                                    value = productName,
                                    onValueChange = { productName = it },
                                    label = { Text("Product Name *") },
                                    placeholder = { Text("e.g. Shan Highlands Espresso Blend") },
                                    singleLine = true,
                                    isError = isError,
                                    supportingText = if (isError) {
                                        {
                                            Text(
                                                text = productNameValidationResult ?: "",
                                                color = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.testTag("product_name_error_text")
                                            )
                                        }
                                    } else null,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("entry_product_name_input")
                                        .testTag("product_name_input")
                                )
                            }

                            // Product Category
                            item {
                                Text(
                                    text = "Catalog Category",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ProductCategory.entries.forEach { cat ->
                                        val isSelected = productCategory == cat
                                        val badgeColor = when (cat) {
                                            ProductCategory.COFFEE -> CoffeePrimary
                                            ProductCategory.GREEN_TEA -> MatchaSecondary
                                            ProductCategory.MACADAMIA_NUT -> MacadamiaTertiary
                                        }

                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { productCategory = cat },
                                            label = { Text(cat.displayName, fontSize = 12.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = badgeColor,
                                                selectedLabelColor = Color.White
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("product_category_${cat.name.lowercase()}")
                                        )
                                    }
                                }
                            }

                            // Stock Quantity & Unit
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    val isStockError = hasAttemptedSubmit && productStockValidationResult != null
                                    OutlinedTextField(
                                        value = stockQuantityText,
                                        onValueChange = { input ->
                                            if (input.isEmpty() || input.all { it.isDigit() }) {
                                                stockQuantityText = input
                                            }
                                        },
                                        label = { Text("Stock Qty *") },
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Number,
                                            imeAction = ImeAction.Next
                                        ),
                                        singleLine = true,
                                        isError = isStockError,
                                        supportingText = if (isStockError) {
                                            {
                                                Text(
                                                    text = productStockValidationResult ?: "",
                                                    color = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.testTag("stock_error_text")
                                                )
                                            }
                                        } else null,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("entry_stock_qty_input")
                                            .testTag("product_stock_input")
                                    )

                                    OutlinedTextField(
                                        value = unit,
                                        onValueChange = { unit = it },
                                        label = { Text("Unit") },
                                        placeholder = { Text("bags / tins") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("entry_unit_input")
                                    )
                                }
                            }

                            // Cost Price & Selling Price (With real-time Amount validation)
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    val isCostError = hasAttemptedSubmit && productCostValidationResult != null
                                    OutlinedTextField(
                                        value = costPriceText,
                                        onValueChange = { input ->
                                            if (input.isEmpty() || input.matches(Regex("""^\d*\.?\d*$"""))) {
                                                costPriceText = input
                                            }
                                        },
                                        label = { Text("Cost (MMK) *") },
                                        placeholder = { Text("0") },
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Decimal,
                                            imeAction = ImeAction.Next
                                        ),
                                        singleLine = true,
                                        isError = isCostError,
                                        supportingText = if (isCostError) {
                                            {
                                                Text(
                                                    text = productCostValidationResult ?: "",
                                                    color = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.testTag("cost_error_text")
                                                )
                                            }
                                        } else null,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("entry_cost_price_input")
                                            .testTag("product_cost_input")
                                    )

                                    val isPriceError = hasAttemptedSubmit && productSellingPriceValidationResult != null
                                    OutlinedTextField(
                                        value = sellingPriceText,
                                        onValueChange = { input ->
                                            if (input.isEmpty() || input.matches(Regex("""^\d*\.?\d*$"""))) {
                                                sellingPriceText = input
                                            }
                                        },
                                        label = { Text("Selling (MMK) *") },
                                        placeholder = { Text("0") },
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Decimal,
                                            imeAction = ImeAction.Next
                                        ),
                                        singleLine = true,
                                        isError = isPriceError,
                                        supportingText = if (isPriceError) {
                                            {
                                                Text(
                                                    text = productSellingPriceValidationResult ?: "",
                                                    color = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.testTag("selling_price_error_text")
                                                )
                                            }
                                        } else null,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("entry_selling_price_input")
                                            .testTag("product_price_input")
                                    )
                                }
                            }

                            // Profit Preview Banner
                            item {
                                val cost = costPriceText.toDoubleOrNull() ?: 0.0
                                val selling = sellingPriceText.toDoubleOrNull() ?: 0.0
                                if (cost > 0 && selling > 0) {
                                    val profit = selling - cost
                                    val margin = if (selling > 0) (profit / selling) * 100 else 0.0
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (profit >= 0) IncomeGreenBg else OutcomeRedBg,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = if (profit >= 0) "Est. Margin: +${formatCurrency(profit)}" else "Loss: ${formatCurrency(profit)}",
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 12.sp,
                                                color = if (profit >= 0) IncomeGreen else OutcomeRed
                                            )
                                            Text(
                                                text = "%.1f%%".format(margin),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = if (profit >= 0) IncomeGreen else OutcomeRed
                                            )
                                        }
                                    }
                                }
                            }

                            // Low Stock Alert Limit & SKU
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    val isMinError = hasAttemptedSubmit && productMinThresholdValidationResult != null
                                    OutlinedTextField(
                                        value = minThresholdText,
                                        onValueChange = { input ->
                                            if (input.isEmpty() || input.all { it.isDigit() }) {
                                                minThresholdText = input
                                            }
                                        },
                                        label = { Text("Low Stock Alert") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        isError = isMinError,
                                        supportingText = if (isMinError) {
                                            { Text(productMinThresholdValidationResult ?: "", color = MaterialTheme.colorScheme.error) }
                                        } else null,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("entry_min_threshold_input")
                                    )

                                    OutlinedTextField(
                                        value = sku,
                                        onValueChange = { sku = it },
                                        label = { Text("SKU / Code") },
                                        placeholder = { Text("BLANC-...") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("entry_sku_input")
                                    )
                                }
                            }

                            // Description
                            item {
                                OutlinedTextField(
                                    value = productDescription,
                                    onValueChange = { productDescription = it },
                                    label = { Text("Tasting Notes / Description") },
                                    placeholder = { Text("Origin, roast profile, processing method...") },
                                    maxLines = 3,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("entry_product_desc_input")
                                )
                            }

                            // Expiry date (optional, YYYY-MM-DD)
                            item {
                                val expiryInvalid = hasAttemptedSubmit &&
                                    expiryText.isNotBlank() && parseExpiryOrNull(expiryText) == null
                                OutlinedTextField(
                                    value = expiryText,
                                    onValueChange = { expiryText = it },
                                    label = { Text("Expiry Date (optional)") },
                                    placeholder = { Text("YYYY-MM-DD, e.g. 2026-12-31") },
                                    singleLine = true,
                                    isError = expiryInvalid,
                                    supportingText = if (expiryInvalid) {
                                        { Text("Use YYYY-MM-DD or leave blank", color = MaterialTheme.colorScheme.error) }
                                    } else {
                                        { Text("Leave blank for non-perishables") }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("entry_expiry_input")
                                )
                            }

                            // Made-to-order toggle (fresh production, no stock tracking)
                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    FilterChip(
                                        selected = madeToOrder,
                                        onClick = { madeToOrder = !madeToOrder },
                                        label = { Text("🔥 Made to order") },
                                        modifier = Modifier.testTag("entry_madetoorder_chip")
                                    )
                                    Text(
                                        text = if (madeToOrder) {
                                            "Produced fresh per order — stock never blocks sales"
                                        } else {
                                            "Stocked — sales deduct finished stock"
                                        },
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("entry_dialog_cancel")
                    ) {
                        Text("Cancel")
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    val confirmButtonColor = when (selectedType) {
                        EntryType.INCOME -> IncomeGreen
                        EntryType.EXPENSE -> OutcomeRed
                        EntryType.PRODUCT -> CoffeePrimary
                    }

                    Button(
                        onClick = {
                            hasAttemptedSubmit = true
                            if (isFormValid) {
                                when (selectedType) {
                                    EntryType.INCOME -> {
                                        val amount = transactionAmountText.toDoubleOrNull() ?: 0.0
                                        onConfirm(
                                            EntryResult.Income(
                                                category = selectedIncomeCategory,
                                                amount = amount,
                                                title = transactionTitle.trim(),
                                                note = transactionNote.trim()
                                            )
                                        )
                                    }
                                    EntryType.EXPENSE -> {
                                        val amount = transactionAmountText.toDoubleOrNull() ?: 0.0
                                        onConfirm(
                                            EntryResult.Expense(
                                                category = selectedExpenseCategory,
                                                amount = amount,
                                                title = transactionTitle.trim(),
                                                note = transactionNote.trim()
                                            )
                                        )
                                    }
                                    EntryType.PRODUCT -> {
                                        val stock = stockQuantityText.toIntOrNull() ?: 0
                                        val cost = costPriceText.toDoubleOrNull() ?: 0.0
                                        val selling = sellingPriceText.toDoubleOrNull() ?: 0.0
                                        val minThreshold = minThresholdText.toIntOrNull() ?: 10
                                        val generatedSku = sku.ifBlank {
                                            existingProduct?.sku?.ifBlank { null }
                                                ?: "BC-${productCategory.name.take(3)}-${(100..999).random()}"
                                        }

                                        onConfirm(
                                            EntryResult.ProductEntry(
                                                product = Product(
                                                    id = existingProduct?.id ?: 0L,
                                                    name = productName.trim(),
                                                    category = productCategory.name,
                                                    stockQuantity = stock,
                                                    unit = unit.ifBlank { "bags" },
                                                    costPrice = cost,
                                                    sellingPrice = selling,
                                                    minStockThreshold = minThreshold,
                                                    sku = generatedSku,
                                                    description = productDescription.trim(),
                                                    expiryDate = parseExpiryOrNull(expiryText),
                                                    madeToOrder = madeToOrder
                                                )
                                            )
                                        )
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = confirmButtonColor),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .testTag("entry_dialog_confirm")
                            .testTag("confirm_save_tx_btn")
                            .testTag("confirm_save_product_btn")
                            .testTag("save_product_btn")
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when (selectedType) {
                                EntryType.INCOME -> "Save Income"
                                EntryType.EXPENSE -> "Save Expense"
                                EntryType.PRODUCT -> if (existingProduct == null) "Add Product" else "Update Product"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryTypeSelectorTab(
    type: EntryType,
    isSelected: Boolean,
    activeColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) activeColor else MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = type.displayName,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
