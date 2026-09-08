package com.example.data.model

enum class ProductCategory(val displayName: String) {
    COFFEE("Coffee Products"),
    GREEN_TEA("Green Tea"),
    MACADAMIA_NUT("Macadamia Nut");

    companion object {
        fun fromString(value: String): ProductCategory {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: COFFEE
        }
    }
}
