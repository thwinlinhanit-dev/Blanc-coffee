package com.blanccoffee.app.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blanccoffee.app.BuildConfig
import com.blanccoffee.app.data.local.ThemeMode
import com.blanccoffee.app.data.model.PaymentMethod
import com.blanccoffee.app.data.repository.ShopRepository
import com.blanccoffee.app.ui.ShopViewModel
import com.blanccoffee.app.ui.theme.CoffeePrimary
import com.blanccoffee.app.ui.theme.OutcomeRed
import com.blanccoffee.app.ui.theme.WarningOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ShopViewModel,
    onExportBackup: () -> Unit = {},
    onExportSheets: () -> Unit = {},
    onPickBackupFile: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val shopName by viewModel.shopName.collectAsState()
    val shopAddress by viewModel.shopAddress.collectAsState()
    val shopPhone by viewModel.shopPhone.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val defaultPayment by viewModel.defaultPayment.collectAsState()

    var nameText by remember(shopName) { mutableStateOf(shopName) }
    var addressText by remember(shopAddress) { mutableStateOf(shopAddress) }
    var phoneText by remember(shopPhone) { mutableStateOf(shopPhone) }

    var seedPreview by remember { mutableStateOf<ShopRepository.SeedClearCounts?>(null) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var clearAllArmed by remember { mutableStateOf(false) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Shop profile — printed on receipts and close-outs
        item {
            SettingsSectionCard(title = "🏪 Shop Profile") {
                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    label = { Text("Shop name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_shop_name")
                )
                OutlinedTextField(
                    value = addressText,
                    onValueChange = { addressText = it },
                    label = { Text("Address (optional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phoneText,
                    onValueChange = { phoneText = it },
                    label = { Text("Phone (optional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        viewModel.saveShopProfile(nameText, addressText, phoneText)
                        toast("Shop profile saved")
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CoffeePrimary),
                    modifier = Modifier.testTag("btn_save_shop_profile")
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Profile", fontWeight = FontWeight.Bold)
                }
            }
        }

        // 2. Appearance
        item {
            SettingsSectionCard(title = "🎨 Appearance") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            label = {
                                Text(
                                    when (mode) {
                                        ThemeMode.LIGHT -> "☀️ Light"
                                        ThemeMode.DARK -> "🌙 Dark"
                                        ThemeMode.SYSTEM -> "📱 System"
                                    }
                                )
                            },
                            modifier = Modifier.testTag("theme_${mode.name.lowercase()}")
                        )
                    }
                }
            }
        }

        // 3. Backup shortcuts (same exporters as the Dashboard card)
        item {
            BackupCard(
                onExportJson = onExportBackup,
                onExportSheets = onExportSheets,
                onImport = onPickBackupFile
            )
        }

        // 4. Demo data — remove only sample rows, never real shop rows
        item {
            SettingsSectionCard(title = "🧪 Demo Data") {
                Text(
                    text = "Remove the built-in sample products, orders and ledger entries. " +
                        "Anything you added yourself is never touched.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = {
                        viewModel.previewSeedClear { preview ->
                            seedPreview = preview
                            if (preview.total == 0) toast("No demo data found — already using real data")
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("btn_preview_demo_clear")
                ) {
                    Icon(Icons.Default.Science, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Remove Demo Data…", fontWeight = FontWeight.Bold)
                }
            }
        }

        // 5. Defaults — payment method pre-selected in new orders
        item {
            SettingsSectionCard(title = "💳 New Order Defaults") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    PaymentMethod.entries.forEach { method ->
                        FilterChip(
                            selected = defaultPayment == method.name,
                            onClick = {
                                viewModel.setDefaultPayment(method)
                                toast("Default: ${method.displayName}")
                            },
                            label = { Text(method.displayName.substringBefore(" /"), fontSize = 12.sp) },
                            modifier = Modifier.testTag("default_pay_${method.name.lowercase()}")
                        )
                    }
                }
            }
        }

        // 6. Danger zone — wipe everything (settings are kept)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = OutcomeRed.copy(alpha = 0.08f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("danger_zone_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "☢️ Danger Zone",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = OutcomeRed
                    )
                    Text(
                        text = "Permanently deletes ALL products, orders, ledger, raw stock and tabs. " +
                            "Export a backup first — this cannot be undone.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { showClearAllConfirm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = OutcomeRed),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("btn_clear_all_data")
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Clear All Data…", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 7. About
        item {
            SettingsSectionCard(title = "ℹ️ About") {
                Text(
                    text = "BLANC COFFEE Shop Manager v${BuildConfig.VERSION_NAME}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = "Offline-first shop manager: orders, tabs, inventory, raw-ingredient " +
                        "tracking, finance ledger, close-outs and backups — all on this device, " +
                        "no account, no cloud.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = "Tip: export a backup after each busy day. If this phone is lost, " +
                        "the backup file is the shop's memory.",
                    fontSize = 12.sp,
                    color = WarningOrange,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    // Demo-clear confirm dialog (shows exact matched counts)
    if (seedPreview != null && seedPreview!!.total > 0) {
        val preview = seedPreview!!
        AlertDialog(
            onDismissRequest = { seedPreview = null },
            title = { Text("Remove ${preview.total} demo rows?") },
            text = {
                Text(
                    "${preview.products} products, ${preview.orders} orders " +
                        "(+${preview.orderItems} items, +${preview.payments} tab payments), " +
                        "${preview.transactions} ledger entries, ${preview.rawMaterials} raw ingredients " +
                        "(+${preview.movements} movements, +${preview.recipes} recipes).\n\n" +
                        "Your own data is untouched."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearSeedData { result ->
                            seedPreview = null
                            toast("Removed ${result.total} demo rows — now 100% real data")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoffeePrimary),
                    modifier = Modifier.testTag("confirm_clear_demo_btn")
                ) {
                    Text("Remove Demo")
                }
            },
            dismissButton = {
                TextButton(onClick = { seedPreview = null }) { Text("Keep") }
            }
        )
    }

    // Clear-all double confirm
    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = {
                showClearAllConfirm = false
                clearAllArmed = false
            },
            title = { Text("Clear ALL shop data?") },
            text = {
                Text(
                    if (!clearAllArmed) {
                        "This deletes every product, order, payment, ledger entry and raw " +
                            "movement. Your shop profile and theme stay. Continue?"
                    } else {
                        "Last chance — tap DELETE to wipe everything permanently."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!clearAllArmed) {
                            clearAllArmed = true
                        } else {
                            viewModel.clearAllData {
                                showClearAllConfirm = false
                                clearAllArmed = false
                                toast("All shop data cleared")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OutcomeRed),
                    modifier = Modifier.testTag("confirm_clear_all_btn")
                ) {
                    Text(if (clearAllArmed) "DELETE EVERYTHING" else "Continue")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showClearAllConfirm = false
                        clearAllArmed = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            content()
        }
    }
}
