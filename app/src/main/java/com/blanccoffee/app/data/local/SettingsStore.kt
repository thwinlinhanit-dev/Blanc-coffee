package com.blanccoffee.app.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Light / dark / follow-system appearance choice. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

/**
 * Tiny offline settings store (SharedPreferences, no new dependencies).
 *
 * Holds shop identity (printed on receipts + close-outs), appearance,
 * the default payment method for new orders, and nothing else — shop DATA
 * (products, orders, ledger) stays in Room and is managed by [ShopRepository].
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _shopName = MutableStateFlow(
        prefs.getString(KEY_SHOP_NAME, DEFAULT_SHOP_NAME) ?: DEFAULT_SHOP_NAME
    )
    val shopName: StateFlow<String> = _shopName.asStateFlow()

    private val _shopAddress = MutableStateFlow(
        prefs.getString(KEY_SHOP_ADDRESS, "") ?: ""
    )
    val shopAddress: StateFlow<String> = _shopAddress.asStateFlow()

    private val _shopPhone = MutableStateFlow(
        prefs.getString(KEY_SHOP_PHONE, "") ?: ""
    )
    val shopPhone: StateFlow<String> = _shopPhone.asStateFlow()

    private val _themeMode = MutableStateFlow(
        runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME_MODE, null) ?: "SYSTEM") }
            .getOrDefault(ThemeMode.SYSTEM)
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _defaultPayment = MutableStateFlow(
        prefs.getString(KEY_DEFAULT_PAYMENT, "CASH") ?: "CASH"
    )
    val defaultPayment: StateFlow<String> = _defaultPayment.asStateFlow()

    /** Saves the shop identity block in one atomic edit. */
    fun saveShopProfile(name: String, address: String, phone: String) {
        val cleanName = name.trim().ifBlank { DEFAULT_SHOP_NAME }
        prefs.edit()
            .putString(KEY_SHOP_NAME, cleanName)
            .putString(KEY_SHOP_ADDRESS, address.trim())
            .putString(KEY_SHOP_PHONE, phone.trim())
            .apply()
        _shopName.value = cleanName
        _shopAddress.value = address.trim()
        _shopPhone.value = phone.trim()
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    fun setDefaultPayment(methodName: String) {
        prefs.edit().putString(KEY_DEFAULT_PAYMENT, methodName).apply()
        _defaultPayment.value = methodName
    }

    companion object {
        const val PREFS_NAME = "blanc_settings"
        const val DEFAULT_SHOP_NAME = "BLANC COFFEE"

        private const val KEY_SHOP_NAME = "shop_name"
        private const val KEY_SHOP_ADDRESS = "shop_address"
        private const val KEY_SHOP_PHONE = "shop_phone"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_DEFAULT_PAYMENT = "default_payment"
    }
}
