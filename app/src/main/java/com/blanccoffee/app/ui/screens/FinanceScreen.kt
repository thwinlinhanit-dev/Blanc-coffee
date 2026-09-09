package com.blanccoffee.app.ui.screens

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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.blanccoffee.app.data.model.Transaction
import com.blanccoffee.app.data.model.TransactionCategory
import com.blanccoffee.app.data.model.TransactionType
import com.blanccoffee.app.ui.ShopViewModel
import com.blanccoffee.app.ui.components.EmptyStateView
import com.blanccoffee.app.ui.components.EditTransactionDialog
import com.blanccoffee.app.ui.components.EntryDialog
import com.blanccoffee.app.ui.components.EntryType
import com.blanccoffee.app.ui.components.formatCurrency
import com.blanccoffee.app.ui.components.formatDateTime
import com.blanccoffee.app.ui.theme.CoffeePrimary
import com.blanccoffee.app.ui.theme.IncomeGreen
import com.blanccoffee.app.ui.theme.IncomeGreenBg
import com.blanccoffee.app.ui.theme.OutcomeRed
import com.blanccoffee.app.ui.theme.OutcomeRedBg

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceScreen(
    viewModel: ShopViewModel,
    initialOpenExpenseDialog: Boolean = false,
    onDialogDismissed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val transactions by viewModel.transactions.collectAsState()
    var selectedTypeFilter by remember { mutableStateOf<TransactionType?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    var showAddDialog by remember { mutableStateOf(initialOpenExpenseDialog) }
    var initialDialogType by remember {
        mutableStateOf(if (initialOpenExpenseDialog) TransactionType.OUTCOME else TransactionType.INCOME)
    }
    var transactionToDelete by remember { mutableStateOf<Transaction?>(null) }
    var transactionToEdit by remember { mutableStateOf<Transaction?>(null) }

    val filteredTransactions = transactions.filter { tx ->
        val matchesType = selectedTypeFilter == null || tx.type == selectedTypeFilter?.name
        val matchesSearch = searchQuery.isBlank() ||
                tx.title.contains(searchQuery, ignoreCase = true) ||
                tx.category.contains(searchQuery, ignoreCase = true) ||
                tx.note.contains(searchQuery, ignoreCase = true)
        matchesType && matchesSearch
    }

    // Running totals for the currently filtered list (refunds count as negative income)
    val totalIncome = filteredTransactions
        .filter { it.type == TransactionType.INCOME.name }
        .sumOf { it.amount }

    val totalOutcome = filteredTransactions
        .filter { it.type == TransactionType.OUTCOME.name }
        .sumOf { it.amount }

    val netCashFlow = totalIncome - totalOutcome

    Scaffold(
        modifier = modifier.testTag("finance_screen"),
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    initialDialogType = TransactionType.INCOME
                    showAddDialog = true
                },
                containerColor = CoffeePrimary,
                contentColor = Color.White,
                modifier = Modifier.testTag("fab_add_transaction")
            ) {
                Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, contentDescription = "Add Entry")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Entry", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Financial Ledger Hero Summary
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Cash Flow Ledger",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Income Box
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = IncomeGreenBg,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.ArrowUpward,
                                            contentDescription = null,
                                            tint = IncomeGreen,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Income", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = IncomeGreen)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "+${formatCurrency(totalIncome)}",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 16.sp,
                                        color = IncomeGreen
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Outcome Box
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = OutcomeRedBg,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.ArrowDownward,
                                            contentDescription = null,
                                            tint = OutcomeRed,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Outcome", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = OutcomeRed)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "-${formatCurrency(totalOutcome)}",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 16.sp,
                                        color = OutcomeRed
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Net Box
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Net", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = formatCurrency(netCashFlow),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 16.sp,
                                        color = if (netCashFlow >= 0) IncomeGreen else OutcomeRed
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search transactions, vendors, notes...") },
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
                        .testTag("search_finance_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Filter Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedTypeFilter == null,
                        onClick = { selectedTypeFilter = null },
                        label = { Text("All (${transactions.size})") },
                        modifier = Modifier.testTag("filter_finance_all")
                    )
                    FilterChip(
                        selected = selectedTypeFilter == TransactionType.INCOME,
                        onClick = {
                            selectedTypeFilter =
                                if (selectedTypeFilter == TransactionType.INCOME) null else TransactionType.INCOME
                        },
                        label = { Text("Income (${transactions.count { it.type == TransactionType.INCOME.name }})") },
                        modifier = Modifier.testTag("filter_finance_income")
                    )
                    FilterChip(
                        selected = selectedTypeFilter == TransactionType.OUTCOME,
                        onClick = {
                            selectedTypeFilter =
                                if (selectedTypeFilter == TransactionType.OUTCOME) null else TransactionType.OUTCOME
                        },
                        label = { Text("Expenses (${transactions.count { it.type == TransactionType.OUTCOME.name }})") },
                        modifier = Modifier.testTag("filter_finance_outcome")
                    )
                }

                // Running totals for the currently filtered list
                if (filteredTransactions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    val filteredIncome = filteredTransactions
                        .filter { it.type == TransactionType.INCOME.name }
                        .sumOf { it.amount }
                    val filteredExpense = filteredTransactions
                        .filter { it.type == TransactionType.OUTCOME.name }
                        .sumOf { it.amount }
                    val filteredNet = filteredIncome - filteredExpense

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("finance_filtered_totals")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${filteredTransactions.size} entries",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "In +${formatCurrency(filteredIncome)}  ·  Out -${formatCurrency(filteredExpense)}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Net: ${formatCurrency(filteredNet)}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (filteredNet >= 0) IncomeGreen else OutcomeRed
                                )
                            }
                        }
                    }
                }
                // Running totals for the currently filtered/searched list
                val filteredIncome = filteredTransactions
                    .filter { it.type == TransactionType.INCOME.name }
                    .sumOf { it.amount }
                val filteredOutcome = filteredTransactions
                    .filter { it.type == TransactionType.OUTCOME.name }
                    .sumOf { it.amount }
                val filteredNet = filteredIncome - filteredOutcome

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth().testTag("finance_filtered_totals")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${filteredTransactions.size} entries",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "+${formatCurrency(filteredIncome)}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = IncomeGreen
                                )
                                Text(
                                    text = "-${formatCurrency(filteredOutcome)}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OutcomeRed
                                )
                            }
                            Text(
                                text = "Net: ${formatCurrency(filteredNet)}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (filteredNet >= 0) IncomeGreen else OutcomeRed
                            )
                        }
                    }
                }
            }

            if (filteredTransactions.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Default.Receipt,
                    title = "No Transactions Found",
                    message = if (searchQuery.isNotEmpty()) "No entries match your search query." else "Tap '+ Add Entry' to record custom income or business expenses."
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = filteredTransactions,
                        key = { it.id }
                    ) { transaction ->
                        TransactionCard(
                            transaction = transaction,
                            onEdit = { transactionToEdit = transaction },
                            onDelete = { transactionToDelete = transaction }
                        )
                    }
                }
            }
        }
    }

    // Reusable Input Dialog for Financial Ledger
    if (showAddDialog) {
        EntryDialog(
            viewModel = viewModel,
            initialType = if (initialDialogType == TransactionType.OUTCOME) EntryType.EXPENSE else EntryType.INCOME,
            allowedTypes = setOf(EntryType.INCOME, EntryType.EXPENSE),
            onDismiss = {
                showAddDialog = false
                onDialogDismissed()
            }
        )
    }

    // Edit Transaction Dialog
    if (transactionToEdit != null) {
        EditTransactionDialog(
            transaction = transactionToEdit!!,
            onDismiss = { transactionToEdit = null },
            onConfirm = { amount, title, note, category ->
                viewModel.updateTransaction(
                    transactionToEdit!!.copy(
                        amount = amount,
                        title = title,
                        note = note,
                        category = category.name
                    )
                )
                transactionToEdit = null
            }
        )
    }

    // Delete Confirmation Dialog
    if (transactionToDelete != null) {
        AlertDialog(
            onDismissRequest = { transactionToDelete = null },
            title = { Text("Delete Transaction?") },
            text = { Text("Are you sure you want to delete '${transactionToDelete?.title}' (${formatCurrency(transactionToDelete?.amount ?: 0.0)})?") },
            confirmButton = {
                Button(
                    onClick = {
                        transactionToDelete?.let { viewModel.deleteTransaction(it) }
                        transactionToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OutcomeRed),
                    modifier = Modifier.testTag("confirm_delete_tx_btn")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { transactionToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun TransactionCard(
    transaction: Transaction,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isIncome = transaction.type == TransactionType.INCOME.name
    // Auto-generated order income/refunds keep their amounts in sync with the order itself.
    val canEdit = transaction.referenceOrderId == null

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("tx_card_${transaction.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(if (isIncome) IncomeGreenBg else OutcomeRedBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isIncome) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                        contentDescription = null,
                        tint = if (isIncome) IncomeGreen else OutcomeRed,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = transaction.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = transaction.category.replace("_", " "),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Text(
                            text = formatDateTime(transaction.timestamp),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }

                    if (transaction.note.isNotBlank()) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = transaction.note,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${if (isIncome) "+" else "-"}${formatCurrency(transaction.amount)}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = if (isIncome) IncomeGreen else OutcomeRed
                )

                IconButton(
                    onClick = onEdit,
                    enabled = canEdit,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = if (canEdit) "Edit entry" else "Auto-generated from an order — cannot edit",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = if (canEdit) 0.7f else 0.2f
                        )
                    )
                }

                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit entry",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Delete entry",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

