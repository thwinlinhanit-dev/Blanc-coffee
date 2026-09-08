package com.example.data.di

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.ExpenseDao
import com.example.data.local.IncomeDao
import com.example.data.local.InventoryDao
import com.example.data.local.OrderDao
import com.example.data.local.ProductDao
import com.example.data.local.TransactionDao
import com.example.data.repository.ShopRepository

/**
 * Service Locator / Dependency Injection container for BLANC COFFEE.
 * Provides singleton instances of AppDatabase, individual DAOs (Product, Income, Expense,
 * Inventory, Order, Transaction), and the centralized ShopRepository.
 */
object DatabaseModule {

    @Volatile
    private var database: AppDatabase? = null

    @Volatile
    private var shopRepository: ShopRepository? = null

    /**
     * Retrieves or initializes the singleton AppDatabase instance.
     */
    fun provideDatabase(context: Context): AppDatabase {
        return database ?: synchronized(this) {
            database ?: AppDatabase.getInstance(context.applicationContext).also {
                database = it
            }
        }
    }

    /**
     * Provides access to ProductDao.
     */
    fun provideProductDao(context: Context): ProductDao {
        return provideDatabase(context).productDao()
    }

    /**
     * Provides access to InventoryDao.
     */
    fun provideInventoryDao(context: Context): InventoryDao {
        return provideDatabase(context).inventoryDao()
    }

    /**
     * Provides access to IncomeDao.
     */
    fun provideIncomeDao(context: Context): IncomeDao {
        return provideDatabase(context).incomeDao()
    }

    /**
     * Provides access to ExpenseDao.
     */
    fun provideExpenseDao(context: Context): ExpenseDao {
        return provideDatabase(context).expenseDao()
    }

    /**
     * Provides access to OrderDao.
     */
    fun provideOrderDao(context: Context): OrderDao {
        return provideDatabase(context).orderDao()
    }

    /**
     * Provides access to TransactionDao.
     */
    fun provideTransactionDao(context: Context): TransactionDao {
        return provideDatabase(context).transactionDao()
    }

    /**
     * Provides the unified ShopRepository wired with all individual DAOs.
     */
    fun provideShopRepository(context: Context): ShopRepository {
        return shopRepository ?: synchronized(this) {
            shopRepository ?: ShopRepository(
                productDao = provideProductDao(context),
                inventoryDao = provideInventoryDao(context),
                incomeDao = provideIncomeDao(context),
                expenseDao = provideExpenseDao(context),
                orderDao = provideOrderDao(context),
                transactionDao = provideTransactionDao(context)
            ).also {
                shopRepository = it
            }
        }
    }
}
