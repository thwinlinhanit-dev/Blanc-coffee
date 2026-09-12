package com.blanccoffee.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.blanccoffee.app.data.model.ProductRecipe
import com.blanccoffee.app.data.model.RawMaterial
import com.blanccoffee.app.data.model.RawMaterialMovement
import kotlinx.coroutines.flow.Flow

/**
 * DAO for raw-ingredient stock tracking (buy / use / remaining).
 *
 * Raw materials live in their own tables so finished-product stock logic
 * ([ProductDao]) never changes. Bought/Used totals are computed from the
 * [RawMaterialMovement] ledger; [RawMaterial.stockQuantity] is the live
 * remaining balance.
 */
@Dao
interface RawMaterialDao {

    // --- Raw materials CRUD ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRawMaterial(material: RawMaterial): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRawMaterials(materials: List<RawMaterial>)

    @Update
    suspend fun updateRawMaterial(material: RawMaterial)

    @Delete
    suspend fun deleteRawMaterial(material: RawMaterial)

    @Query("DELETE FROM raw_materials WHERE id = :materialId")
    suspend fun deleteRawMaterialById(materialId: Long)

    @Query("SELECT * FROM raw_materials ORDER BY name ASC")
    fun getAllRawMaterials(): Flow<List<RawMaterial>>

    @Query("SELECT * FROM raw_materials WHERE id = :id LIMIT 1")
    suspend fun getRawMaterialById(id: Long): RawMaterial?

    @Query("SELECT * FROM raw_materials WHERE stockQuantity <= minThreshold ORDER BY stockQuantity ASC")
    fun getLowRawMaterials(): Flow<List<RawMaterial>>

    @Query("SELECT COUNT(*) FROM raw_materials")
    suspend fun getRawMaterialCount(): Int

    /** Ids of demo-seeded raw ingredients: flagged rows plus legacy seed SKUs. */
    @Query("SELECT id FROM raw_materials WHERE isSeed = 1 OR sku IN (:seedSkus)")
    suspend fun findSeedRawIds(seedSkus: List<String>): List<Long>

    @Query("DELETE FROM raw_movements WHERE materialId IN (:materialIds)")
    suspend fun deleteMovementsForMaterials(materialIds: List<Long>): Int

    @Query("DELETE FROM product_recipes WHERE materialId IN (:materialIds) OR productId IN (:productIds)")
    suspend fun deleteRecipesForIds(materialIds: List<Long>, productIds: List<Long>): Int

    @Query("DELETE FROM raw_materials WHERE id IN (:ids)")
    suspend fun deleteRawsByIds(ids: List<Long>): Int

    @Query("SELECT * FROM raw_materials ORDER BY id ASC")
    suspend fun getAllRawMaterialsOnce(): List<RawMaterial>

    @Query("SELECT * FROM raw_movements ORDER BY id ASC")
    suspend fun getAllMovementsOnce(): List<RawMaterialMovement>

    @Query("SELECT * FROM product_recipes ORDER BY productId ASC, materialId ASC")
    suspend fun getAllRecipesOnce(): List<ProductRecipe>

    @Query("DELETE FROM raw_materials")
    suspend fun deleteAllRawMaterials()

    @Query("DELETE FROM raw_movements")
    suspend fun deleteAllMovements()

    @Query("DELETE FROM product_recipes")
    suspend fun deleteAllRecipes()

    // --- Movements ledger ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovement(movement: RawMaterialMovement): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovements(movements: List<RawMaterialMovement>)

    @Query("SELECT * FROM raw_movements ORDER BY timestamp DESC")
    fun getAllMovements(): Flow<List<RawMaterialMovement>>

    @Query("SELECT * FROM raw_movements WHERE materialId = :materialId ORDER BY timestamp DESC")
    fun getMovementsForMaterial(materialId: Long): Flow<List<RawMaterialMovement>>

    @Query("SELECT * FROM raw_movements WHERE materialId = :materialId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMovementsForMaterial(materialId: Long, limit: Int = 50): List<RawMaterialMovement>

    /** Total bought (sum of PURCHASE qty) for one material — for bought/used/remaining cards. */
    @Query("SELECT COALESCE(SUM(quantity), 0) FROM raw_movements WHERE materialId = :materialId AND type = 'PURCHASE'")
    suspend fun getTotalPurchased(materialId: Long): Double

    /** Total used (sum of USAGE qty) for one material. */
    @Query("SELECT COALESCE(SUM(quantity), 0) FROM raw_movements WHERE materialId = :materialId AND type = 'USAGE'")
    suspend fun getTotalUsed(materialId: Long): Double

    @Query("DELETE FROM raw_movements WHERE materialId = :materialId")
    suspend fun deleteMovementsForMaterial(materialId: Long)

    @Query("SELECT COUNT(*) FROM raw_movements")
    suspend fun getMovementCount(): Int

    // --- Recipes (product -> raw linkage for auto-deduct) ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecipe(recipe: ProductRecipe)

    @Query("SELECT * FROM product_recipes WHERE productId = :productId")
    suspend fun getRecipesForProduct(productId: Long): List<ProductRecipe>

    @Query("SELECT * FROM product_recipes WHERE productId = :productId")
    fun observeRecipesForProduct(productId: Long): Flow<List<ProductRecipe>>

    @Query("SELECT * FROM product_recipes WHERE materialId = :materialId")
    suspend fun getRecipesForMaterial(materialId: Long): List<ProductRecipe>

    @Query("SELECT * FROM product_recipes")
    fun getAllRecipes(): Flow<List<ProductRecipe>>

    @Query("DELETE FROM product_recipes WHERE productId = :productId AND materialId = :materialId")
    suspend fun deleteRecipe(productId: Long, materialId: Long)

    @Query("DELETE FROM product_recipes WHERE productId = :productId")
    suspend fun deleteRecipesForProduct(productId: Long)

    @Query("DELETE FROM product_recipes WHERE materialId = :materialId")
    suspend fun deleteRecipesForMaterial(materialId: Long)
}
