package com.blanccoffee.app.data.di

import android.content.Context
import com.blanccoffee.app.data.local.AppDatabase
import com.blanccoffee.app.data.local.ExpenseDao
import com.blanccoffee.app.data.local.IncomeDao
import com.blanccoffee.app.data.local.InventoryDao
import com.blanccoffee.app.data.local.OrderDao
import com.blanccoffee.app.data.local.ProductDao
import com.blanccoffee.app.data.local.RawMaterialDao
import com.blanccoffee.app.data.local.TransactionDao
import com.blanccoffee.app.data.repository.ShopRepository

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
     * Provides access to RawMaterialDao (raw buy/use/remaining ledger + recipes).
     */
    fun provideRawMaterialDao(context: Context): RawMaterialDao {
        return provideDatabase(context).rawMaterialDao()
    }

    /**
     * Provides the unified ShopRepository wired with the database handle and the
     * individual DAOs it actually needs (Product, Inventory, Order, Transaction).
     * Income/expense queries run through TransactionDao directly — no anonymous
     * DAO adapters needed anymore.
     */
    fun provideShopRepository(context: Context): ShopRepository {
        return shopRepository ?: synchronized(this) {
            shopRepository ?: ShopRepository(
                database = provideDatabase(context),
                productDao = provideProductDao(context),
                inventoryDao = provideInventoryDao(context),
                orderDao = provideOrderDao(context),
                transactionDao = provideTransactionDao(context),
                rawMaterialDao = provideRawMaterialDao(context)
            ).also {
                shopRepository = it
            }
        }
    }
}
