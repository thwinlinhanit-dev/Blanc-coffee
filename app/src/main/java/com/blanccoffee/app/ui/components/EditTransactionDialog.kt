package com.blanccoffee.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.blanccoffee.app.data.model.Transaction
import com.blanccoffee.app.data.model.TransactionCategory
import com.blanccoffee.app.data.model.TransactionType
import com.blanccoffee.app.ui.theme.CoffeePrimary
import com.blanccoffee.app.ui.theme.OutcomeRed

/**
 * Dialog for editing an existing manual ledger entry (amount, title, note, category).
 *
 * Auto-generated transactions (those with a [Transaction.referenceOrderId]) are NOT editable
 * here because their amounts are derived from real orders; the finance flow protects those.
 */
@Composable
fun EditTransactionDialog(
    transaction: Transaction,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, title: String, note: String, category: TransactionCategory) -> Unit
) {
    var amountText by remember { mutableStateOf(trimAmount(transaction.amount)) }
    var title by remember { mutableStateOf(transaction.title) }
    var note by remember { mutableStateOf(transaction.note) }
    var category by remember {
        mutableStateOf(TransactionCategory.fromString(transaction.category))
    }

    val parsedAmount = amountText.replace(",", "").toDoubleOrNull()
    val isAmountValid = parsedAmount != null && parsedAmount > 0
    val isTitleValid = title.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Entry") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    isError = !isTitleValid,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("edit_tx_title_input")
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (MMK)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = !isAmountValid,
                    supportingText = { if (!isAmountValid) Text("Enter an amount greater than 0") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("edit_tx_amount_input")
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("edit_tx_note_input")
                )

                Text(
                    text = "Category",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val type = TransactionType.valueOf(transaction.type)
                    TransactionCategory.entries
                        .filter { it.type == type && it != TransactionCategory.ORDER_REFUND }
                        .chunked(3).forEach { rowCategories ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                rowCategories.forEach { cat ->
                                    FilterChip(
                                        selected = category == cat,
                                        onClick = { category = cat },
                                        label = { Text(cat.displayName, maxLines = 1) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = parsedAmount ?: return@Button
                    if (!isAmountValid || !isTitleValid) return@Button
                    onConfirm(amount, title.trim(), note.trim(), category)
                },
                enabled = isAmountValid && isTitleValid,
                colors = ButtonDefaults.buttonColors(containerColor = CoffeePrimary),
                modifier = Modifier.testTag("confirm_edit_tx_btn")
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = OutcomeRed)
            }
        }
    )
}

/** Formats an amount without trailing fraction for text-field editing. */
private fun trimAmount(amount: Double): String =
    if (amount % 1.0 == 0.0) amount.toLong().toString() else amount.toString()
