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
import kotlinx.coroutines.flow.Flow

// One article, e.g. "Peshawari Chappal". Prices are in Rs per pair.
@Entity(tableName = "products")
data class Product(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val costPrice: Int,
    val salePrice: Int
)

// Stock is tracked per article AND per size (7, 8, 9, 10, 11 ...).
@Entity(tableName = "stock", primaryKeys = ["productId", "size"])
data class Stock(
    val productId: Long,
    val size: String,
    val qty: Int
)

// One bill. paid < total means the rest is udhaar for that customer.
@Entity(tableName = "sales")
data class Sale(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val customer: String,
    val total: Int,
    val paid: Int
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

    @Query("SELECT * FROM products ORDER BY name")
    fun getAll(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getById(id: Long): Product?
}

@Dao
interface StockDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(s: Stock)

    @Query("SELECT * FROM stock WHERE productId = :pid")
    fun getByProduct(pid: Long): Flow<List<Stock>>

    @Query("SELECT * FROM stock")
    fun getAll(): Flow<List<Stock>>

    @Query("SELECT * FROM stock WHERE productId = :pid AND size = :size")
    suspend fun getOne(pid: Long, size: String): Stock?
}

@Dao
interface SaleDao {
    @Insert
    fun insertSale(s: Sale): Long

    @Insert
    fun insertItems(items: List<SaleItem>)

    @Query("SELECT * FROM sales ORDER BY date DESC")
    fun getAll(): Flow<List<Sale>>

    @Query("SELECT * FROM sale_items")
    fun getAllItems(): Flow<List<SaleItem>>
}

@Dao
interface PaymentDao {
    @Insert
    suspend fun insert(p: Payment)

    @Query("SELECT * FROM payments")
    fun getAll(): Flow<List<Payment>>
}

@Database(
    entities = [Product::class, Stock::class, Sale::class, SaleItem::class, Payment::class],
    version = 1,
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
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
