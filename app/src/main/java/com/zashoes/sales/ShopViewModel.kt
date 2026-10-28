package com.zashoes.sales

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    fun makeSale(customer: String, paid: Int, cart: List<CartLine>, onDone: (Sale, List<CartLine>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val total = cart.sumOf { it.product.salePrice * it.qty }
            var savedSale = Sale(date = now, customer = customer, total = total, paid = paid)
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
}
