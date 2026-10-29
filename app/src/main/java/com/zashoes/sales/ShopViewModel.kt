package com.zashoes.sales

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

// One line in the sale cart: an article, a size, and how many pairs.
data class CartLine(val product: Product, val size: String, val qty: Int)

class ShopViewModel(app: Application) : AndroidViewModel(app) {
    private val db = ShopDatabase.get(app)

    val products: StateFlow<List<Product>> =
        db.productDao().getAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allStock: StateFlow<List<Stock>> =
        db.stockDao().getAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sales: StateFlow<List<Sale>> =
        db.saleDao().getAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allItems: StateFlow<List<SaleItem>> =
        db.saleDao().getAllItems().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val payments: StateFlow<List<Payment>> =
        db.paymentDao().getAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addProduct(name: String, cost: Int, sale: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            db.productDao().insert(Product(name = name, costPrice = cost, salePrice = sale))
        }
    }

    fun addStock(productId: Long, size: String, qty: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val cur = db.stockDao().getOne(productId, size)
            db.stockDao().upsert(Stock(productId, size, (cur?.qty ?: 0) + qty))
        }
    }

    // Saves the bill, its items, and decreases stock — all in one transaction
    // so a sale can never be half-saved. Returns the saved bill + its lines
    // so the UI can show the receipt right away.
    fun makeSale(customer: String, paid: Int, discount: Int, cart: List<CartLine>, onDone: (Sale, List<CartLine>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val total = (cart.sumOf { it.product.salePrice * it.qty } - discount).coerceAtLeast(0)
            var savedSale = Sale(date = now, customer = customer, total = total, paid = paid, discount = discount)
            db.withTransaction {
                val saleId = db.saleDao().insertSale(savedSale)
                savedSale = savedSale.copy(id = saleId)
                db.saleDao().insertItems(
                    cart.map {
                        SaleItem(
                            saleId = saleId,
                            productId = it.product.id,
                            size = it.size,
                            qty = it.qty,
                            price = it.product.salePrice
                        )
                    }
                )
                for (line in cart) {
                    val cur = db.stockDao().getOne(line.product.id, line.size)
                    val left = (cur?.qty ?: 0) - line.qty
                    db.stockDao().upsert(Stock(line.product.id, line.size, left.coerceAtLeast(0)))
                }
            }
            withContext(Dispatchers.Main) { onDone(savedSale, cart) }
        }
    }

    fun recordPayment(saleId: Long, amount: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            db.paymentDao().insert(Payment(saleId = saleId, amount = amount, date = System.currentTimeMillis()))
        }
    }

    // Remaining udhaar on one bill = total - paid at sale time - recovered.
    fun balanceOf(sale: Sale, payments: List<Payment>): Int {
        val recovered = payments.filter { it.saleId == sale.id }.sumOf { it.amount }
        return (sale.total - sale.paid - recovered).coerceAtLeast(0)
    }

    fun updateProduct(p: Product) {
        viewModelScope.launch(Dispatchers.IO) { db.productDao().update(p) }
    }

    fun deleteProduct(productId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            db.stockDao().deleteForProduct(productId)
            db.productDao().delete(productId)
        }
    }

    // Cancels a bill: stock goes back, its items and payments are removed.
    fun deleteSale(saleId: Long, onDone: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            db.withTransaction {
                val items = db.saleDao().getItems(saleId)
                for (item in items) {
                    val cur = db.stockDao().getOne(item.productId, item.size)
                    db.stockDao().upsert(Stock(item.productId, item.size, (cur?.qty ?: 0) + item.qty))
                }
                db.saleDao().deleteItemsForSale(saleId)
                db.paymentDao().deleteForSale(saleId)
                db.saleDao().deleteSale(saleId)
            }
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    // Saves the shopkeeper's own article photo into the app's private files.
    fun saveProductPhoto(productId: Long, uri: Uri, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(context.filesDir, "product_$productId.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(file).use { output -> input.copyTo(output) }
                }
                val p = db.productDao().getById(productId)
                if (p != null) db.productDao().update(p.copy(imagePath = file.absolutePath))
            } catch (e: Exception) {
                // Photo save failed: keep the old photo (or none). No crash.
            }
        }
    }

    // CSV of every bill, for sharing (opens in Excel/Sheets).
    fun exportSalesCsv(): String {
        val nameMap = products.value.associate { it.id to it.name }
        val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH)
        val sb = StringBuilder()
        sb.appendLine("Date,Customer,Article,Size,Qty,Price,Amount,Bill Total,Paid,Balance")
        for (sale in sales.value) {
            val itemsForSale = allItems.value.filter { it.saleId == sale.id }
            val bal = balanceOf(sale, payments.value)
            if (itemsForSale.isEmpty()) {
                sb.appendLine("${dateFmt.format(sale.date)},${sale.customer},,,,,,${sale.total},${sale.paid},$bal")
            } else {
                for (item in itemsForSale) {
                    sb.appendLine("${dateFmt.format(sale.date)},${sale.customer},${nameMap[item.productId] ?: "Article"},${item.size},${item.qty},${item.price},${item.price * item.qty},${sale.total},${sale.paid},$bal")
                }
            }
        }
        return sb.toString()
    }

    // Full backup of every table as JSON text.
    fun backupJson(): String {
        val root = JSONObject()
        val prods = JSONArray()
        for (p in products.value) {
            prods.put(
                JSONObject().put("id", p.id).put("name", p.name)
                    .put("costPrice", p.costPrice).put("salePrice", p.salePrice)
                    .put("imagePath", p.imagePath)
            )
        }
        val stocks = JSONArray()
        for (s in allStock.value) {
            stocks.put(JSONObject().put("productId", s.productId).put("size", s.size).put("qty", s.qty))
        }
        val saleList = JSONArray()
        for (s in sales.value) {
            saleList.put(
                JSONObject().put("id", s.id).put("date", s.date)
                    .put("customer", s.customer).put("total", s.total)
                    .put("paid", s.paid).put("discount", s.discount)
            )
        }
        val itemList = JSONArray()
        for (i in allItems.value) {
            itemList.put(
                JSONObject().put("id", i.id).put("saleId", i.saleId)
                    .put("productId", i.productId).put("size", i.size)
                    .put("qty", i.qty).put("price", i.price)
            )
        }
        val payList = JSONArray()
        for (p in payments.value) {
            payList.put(
                JSONObject().put("id", p.id).put("saleId", p.saleId)
                    .put("amount", p.amount).put("date", p.date)
            )
        }
        root.put("products", prods)
        root.put("stock", stocks)
        root.put("sales", saleList)
        root.put("items", itemList)
        root.put("payments", payList)
        return root.toString()
    }

    // Restores a backup made by backupJson(). Returns false if the file is bad.
    fun restoreFromJson(json: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = try {
                val root = JSONObject(json)
                val prods = mutableListOf<Product>()
                val stocks = mutableListOf<Stock>()
                val saleList = mutableListOf<Sale>()
                val itemList = mutableListOf<SaleItem>()
                val payList = mutableListOf<Payment>()
                val ja = root.optJSONArray("products")
                if (ja != null) {
                    for (i in 0 until ja.length()) {
                        val o = ja.getJSONObject(i)
                        prods.add(
                            Product(
                                id = o.optLong("id"),
                                name = o.optString("name"),
                                costPrice = o.optInt("costPrice"),
                                salePrice = o.optInt("salePrice"),
                                imagePath = o.optString("imagePath")
                            )
                        )
                    }
                }
                val js = root.optJSONArray("stock")
                if (js != null) {
                    for (i in 0 until js.length()) {
                        val o = js.getJSONObject(i)
                        stocks.add(Stock(o.optLong("productId"), o.optString("size"), o.optInt("qty")))
                    }
                }
                val jsa = root.optJSONArray("sales")
                if (jsa != null) {
                    for (i in 0 until jsa.length()) {
                        val o = jsa.getJSONObject(i)
                        saleList.add(
                            Sale(
                                id = o.optLong("id"),
                                date = o.optLong("date"),
                                customer = o.optString("customer"),
                                total = o.optInt("total"),
                                paid = o.optInt("paid"),
                                discount = o.optInt("discount")
                            )
                        )
                    }
                }
                val ji = root.optJSONArray("items")
                if (ji != null) {
                    for (i in 0 until ji.length()) {
                        val o = ji.getJSONObject(i)
                        itemList.add(
                            SaleItem(
                                id = o.optLong("id"),
                                saleId = o.optLong("saleId"),
                                productId = o.optLong("productId"),
                                size = o.optString("size"),
                                qty = o.optInt("qty"),
                                price = o.optInt("price")
                            )
                        )
                    }
                }
                val jp = root.optJSONArray("payments")
                if (jp != null) {
                    for (i in 0 until jp.length()) {
                        val o = jp.getJSONObject(i)
                        payList.add(
                            Payment(
                                id = o.optLong("id"),
                                saleId = o.optLong("saleId"),
                                amount = o.optInt("amount"),
                                date = o.optLong("date")
                            )
                        )
                    }
                }
                db.withTransaction {
                    db.productDao().clear()
                    db.stockDao().clear()
                    db.saleDao().clear()
                    db.saleDao().clearItems()
                    db.paymentDao().clear()
                    db.productDao().insertAll(prods)
                    db.stockDao().insertAll(stocks)
                    db.saleDao().insertSales(saleList)
                    db.saleDao().insertItems(itemList)
                    db.paymentDao().insertAll(payList)
                }
                true
            } catch (e: Exception) {
                false
            }
            withContext(Dispatchers.Main) { onResult(ok) }
        }
    }
}
