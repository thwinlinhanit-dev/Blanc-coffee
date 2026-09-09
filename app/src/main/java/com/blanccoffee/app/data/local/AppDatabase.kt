package com.blanccoffee.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.blanccoffee.app.data.model.CustomerOrder
import com.blanccoffee.app.data.model.CustomerPayment
import com.blanccoffee.app.data.model.OrderItem
import com.blanccoffee.app.data.model.Product
import com.blanccoffee.app.data.model.ProductRecipe
import com.blanccoffee.app.data.model.RawMaterial
import com.blanccoffee.app.data.model.RawMaterialMovement
import com.blanccoffee.app.data.model.Transaction

/**
 * Migration 1 -> 2: adds raw-material stock tracking tables without touching
 * existing product / order / transaction data.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `raw_materials` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `unit` TEXT NOT NULL,
                `stockQuantity` REAL NOT NULL,
                `minThreshold` REAL NOT NULL,
                `costPerUnit` REAL NOT NULL,
                `sku` TEXT NOT NULL,
                `note` TEXT NOT NULL,
                `lastUpdated` INTEGER NOT NULL)"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_raw_materials_name` ON `raw_materials` (`name`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_raw_materials_sku` ON `raw_materials` (`sku`)")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `raw_movements` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `materialId` INTEGER NOT NULL,
                `type` TEXT NOT NULL,
                `quantity` REAL NOT NULL,
                `totalCost` REAL NOT NULL,
                `note` TEXT NOT NULL,
                `linkedOrderId` INTEGER,
                `timestamp` INTEGER NOT NULL)"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_raw_movements_materialId` ON `raw_movements` (`materialId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_raw_movements_timestamp` ON `raw_movements` (`timestamp`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_raw_movements_type` ON `raw_movements` (`type`)")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `product_recipes` (
                `productId` INTEGER NOT NULL,
                `materialId` INTEGER NOT NULL,
                `quantityPerUnit` REAL NOT NULL,
                PRIMARY KEY(`productId`, `materialId`))"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_product_recipes_materialId` ON `product_recipes` (`materialId`)")
    }
}

/**
 * Migration 2 -> 3: credit tabs (customer_payments), order discounts and
 * expiry dates for products + raw materials. All additive — no data touched.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `orders` ADD COLUMN `discountAmount` REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE `orders` ADD COLUMN `discountReason` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `products` ADD COLUMN `expiryDate` INTEGER")
        db.execSQL("ALTER TABLE `raw_materials` ADD COLUMN `expiryDate` INTEGER")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `customer_payments` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `orderId` INTEGER NOT NULL,
                `amount` REAL NOT NULL,
                `method` TEXT NOT NULL,
                `note` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL)"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_customer_payments_orderId` ON `customer_payments` (`orderId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_customer_payments_timestamp` ON `customer_payments` (`timestamp`)")
    }
}

@Database(
    entities = [
        Product::class,
        CustomerOrder::class,
        OrderItem::class,
        Transaction::class,
        RawMaterial::class,
        RawMaterialMovement::class,
        ProductRecipe::class,
        CustomerPayment::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun incomeDao(): IncomeDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun orderDao(): OrderDao
    abstract fun transactionDao(): TransactionDao
    abstract fun rawMaterialDao(): RawMaterialDao
    abstract fun customerPaymentDao(): CustomerPaymentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "blanc_coffee.db"
                )
                    // Migrations preserve real shop data (additive only).
                    // fallbackToDestructiveMigration stays as a last-resort safety net.
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
