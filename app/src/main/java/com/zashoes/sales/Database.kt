package com.zashoes.sales

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

// One article, e.g. "Peshawari Chappal". Prices are in Rs per pair.
// imagePath: the shopkeeper's own photo for this article (empty = stock photo).
@Entity(tableName = "products")
data class Product(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val costPrice: Int,
    val salePrice: Int,
    val imagePath: String = ""
)

// Stock is tracked per article AND per size (7, 8, 9, 10, 11 ...).
@Entity(tableName = "stock", primaryKeys = ["productId", "size"])
data class Stock(
    val productId: Long,
    val size: String,
    val qty: Int
)

// One bill. paid < total means the rest is udhaar for that customer.
// total is AFTER discount; discount is the Rs discount given on the bill.
@Entity(tableName = "sales")
data class Sale(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val customer: String,
    val total: Int,
    val paid: Int,
    val discount: Int = 0
)

@Entity(tableName = "sale_items")
data class SaleItem(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val saleId: Long,
    val productId: Long,
    val size: String,
    val qty: Int,
    val price: Int
)

// A recovered (wusooli) payment against one sale.
@Entity(tableName = "payments")
data class Payment(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val saleId: Long,
    val amount: Int,
    val date: Long
)

@Dao
interface ProductDao {
    @Insert
    fun insert(p: Product): Long

    @Insert
    fun insertAll(list: List<Product>)

    @Update
    suspend fun update(p: Product)

    @Query("SELECT * FROM products ORDER BY name")
    fun getAll(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getById(id: Long): Product?

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM products")
    suspend fun clear()
}

@Dao
interface StockDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(s: Stock)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(list: List<Stock>)

    @Query("SELECT * FROM stock WHERE productId = :pid")
    fun getByProduct(pid: Long): Flow<List<Stock>>

    @Query("SELECT * FROM stock")
    fun getAll(): Flow<List<Stock>>

    @Query("SELECT * FROM stock WHERE productId = :pid AND size = :size")
    suspend fun getOne(pid: Long, size: String): Stock?

    @Query("DELETE FROM stock WHERE productId = :pid")
    suspend fun deleteForProduct(pid: Long)

    @Query("DELETE FROM stock")
    suspend fun clear()
}

@Dao
interface SaleDao {
    @Insert
    fun insertSale(s: Sale): Long

    @Insert
    fun insertSales(list: List<Sale>)

    @Insert
    fun insertItems(items: List<SaleItem>)

    @Query("SELECT * FROM sales ORDER BY date DESC")
    fun getAll(): Flow<List<Sale>>

    @Query("SELECT * FROM sale_items")
    fun getAllItems(): Flow<List<SaleItem>>

    @Query("SELECT * FROM sale_items WHERE saleId = :saleId")
    suspend fun getItems(saleId: Long): List<SaleItem>

    @Query("DELETE FROM sales WHERE id = :id")
    suspend fun deleteSale(id: Long)

    @Query("DELETE FROM sale_items WHERE saleId = :saleId")
    suspend fun deleteItemsForSale(saleId: Long)

    @Query("DELETE FROM sales")
    suspend fun clear()

    @Query("DELETE FROM sale_items")
    suspend fun clearItems()
}

@Dao
interface PaymentDao {
    @Insert
    suspend fun insert(p: Payment)

    @Insert
    fun insertAll(list: List<Payment>)

    @Query("SELECT * FROM payments")
    fun getAll(): Flow<List<Payment>>

    @Query("DELETE FROM payments WHERE saleId = :saleId")
    suspend fun deleteForSale(saleId: Long)

    @Query("DELETE FROM payments")
    suspend fun clear()
}

// v1 -> v2: per-article photo + bill discount. Data is PRESERVED:
// the new columns are added with defaults, nothing is dropped.
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE products ADD COLUMN imagePath TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE sales ADD COLUMN discount INTEGER NOT NULL DEFAULT 0")
    }
}

@Database(
    entities = [Product::class, Stock::class, Sale::class, SaleItem::class, Payment::class],
    version = 2,
    exportSchema = false
)
abstract class ShopDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun stockDao(): StockDao
    abstract fun saleDao(): SaleDao
    abstract fun paymentDao(): PaymentDao

    companion object {
        @Volatile
        private var INSTANCE: ShopDatabase? = null

        fun get(context: Context): ShopDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ShopDatabase::class.java,
                    "za_shoes.db"
                ).addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
