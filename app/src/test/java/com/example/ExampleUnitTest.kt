package com.example

import com.example.data.model.ProductCategory
import com.example.data.model.TransactionCategory
import com.example.data.model.TransactionType
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun transactionCategory_matchesType() {
        val incomeCategories = TransactionCategory.entries.filter { it.type == TransactionType.INCOME }
        val outcomeCategories = TransactionCategory.entries.filter { it.type == TransactionType.OUTCOME }

        assertTrue(incomeCategories.contains(TransactionCategory.ORDER_SALE))
        assertTrue(outcomeCategories.contains(TransactionCategory.RESTOCKING))
        assertTrue(outcomeCategories.contains(TransactionCategory.SALARY))
    }

    @Test
    fun productCategory_resolvesFromString() {
        assertEquals(ProductCategory.COFFEE, ProductCategory.fromString("COFFEE"))
        assertEquals(ProductCategory.GREEN_TEA, ProductCategory.fromString("GREEN_TEA"))
        assertEquals(ProductCategory.MACADAMIA_NUT, ProductCategory.fromString("MACADAMIA_NUT"))
        assertEquals(ProductCategory.COFFEE, ProductCategory.fromString("UNKNOWN"))
    }

    @Test
    fun amountValidation_handlesPositiveAndInvalid() {
        val validAmount = "45000.0".toDoubleOrNull()
        val invalidAmount = "abc".toDoubleOrNull()
        val negativeAmount = "-100.0".toDoubleOrNull()

        assertNotNull(validAmount)
        assertTrue(validAmount!! > 0)
        assertNull(invalidAmount)
        assertNotNull(negativeAmount)
        assertFalse(negativeAmount!! > 0)
    }
}
