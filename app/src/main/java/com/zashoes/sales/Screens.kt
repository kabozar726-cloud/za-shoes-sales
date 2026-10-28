package com.zashoes.sales

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun fmtDate(millis: Long): String =
    SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH).format(Date(millis))

fun startOfDayMillis(): Long {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopApp(vm: ShopViewModel) {
    val nav = rememberNavController()
    var current by remember { mutableStateOf("home") }
    val go: (String) -> Unit = { route ->
        current = route
        nav.navigate(route) { launchSingleTop = true }
    }
    val tabs = listOf(
        Triple("home", "🏠", "Home"),
        Triple("sale", "🛒", "Sale"),
        Triple("stock", "📦", "Stock"),
        Triple("khata", "📒", "Khata")
    )
    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { (route, icon, label) ->
                    NavigationBarItem(
                        selected = current == route,
                        onClick = { go(route) },
                        icon = { Text(icon) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(navController = nav, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") { HomeScreen(vm, go) }
            composable("sale") { SaleScreen(vm) }
            composable("stock") { StockScreen(vm) }
            composable("khata") { KhataScreen(vm) }
        }
    }
}

@Composable
fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier.padding(2.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
fun HomeScreen(vm: ShopViewModel, go: (String) -> Unit) {
    val sales by vm.sales.collectAsState()
    val payments by vm.payments.collectAsState()
    val items by vm.allItems.collectAsState()
    val products by vm.products.collectAsState()
    val stock by vm.allStock.collectAsState()
    val start = remember { startOfDayMillis() }
    val todaySales = sales.filter { it.date >= start }
    val todayIds = todaySales.map { it.id }.toSet()
    val costMap = products.associate { it.id to it.costPrice }
    val todayTotal = todaySales.sumOf { it.total }
    val todayProfit = items.filter { it.saleId in todayIds }
        .sumOf { (it.price - (costMap[it.productId] ?: 0)) * it.qty }
    val totalUdhaar = sales.sumOf { vm.balanceOf(it, payments) }
    val lowStock = stock.count { it.qty <= 2 }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)
    ) {
        Text("ZA Shoes", style = MaterialTheme.typography.headlineMedium)
        Text("Aaj ka hisaab", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("Aaj ki Sale", "Rs $todayTotal", Modifier.weight(1f))
            StatCard("Aaj ka Profit", "Rs $todayProfit", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("Total Udhaar", "Rs $totalUdhaar", Modifier.weight(1f))
            StatCard("Low Stock", "$lowStock items", Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { go("sale") }) { Text("Nayi Sale") }
            OutlinedButton(onClick = { go("stock") }) { Text("Stock") }
            OutlinedButton(onClick = { go("khata") }) { Text("Khata") }
        }
        Spacer(Modifier.height(12.dp))
        Text("Haal ki Salein", style = MaterialTheme.typography.titleMedium)
        sales.take(10).forEach { s ->
            val bal = vm.balanceOf(s, payments)
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(10.dp)) {
                    Text(if (s.customer.isBlank()) "Walk-in" else s.customer, style = MaterialTheme.typography.titleSmall)
                    Text(fmtDate(s.date), style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Total Rs ${s.total}  |  Jama Rs ${s.paid}" +
                            if (bal > 0) "  |  Udhaar Rs $bal" else ""
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaleScreen(vm: ShopViewModel) {
    val products by vm.products.collectAsState()
    val stock by vm.allStock.collectAsState()
    var selected by remember { mutableStateOf<Product?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var size by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf(1) }
    var customer by remember { mutableStateOf("") }
    var paid by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    val cart = remember { mutableStateListOf<CartLine>() }

    val sizes = selected?.let { p ->
        stock.filter { it.productId == p.id && it.qty > 0 }.map { it.size }.sorted()
    } ?: emptyList()
    val stockQty = selected?.let { p ->
        stock.find { it.productId == p.id && it.size == size }?.qty ?: 0
    } ?: 0
    val total = cart.sumOf { it.product.salePrice * it.qty }
    val paidInt = paid.toIntOrNull() ?: 0
    val balance = (total - paidInt).coerceAtLeast(0)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)
    ) {
        Text("Nayi Sale", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            TextField(
                value = selected?.name ?: "Article chunein",
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                products.forEach { p ->
                    DropdownMenuItem(
                        text = { Text("${p.name} — Rs ${p.salePrice}") },
                        onClick = { selected = p; size = ""; qty = 1; expanded = false }
                    )
                }
            }
        }
        if (selected != null) {
            Spacer(Modifier.height(8.dp))
            Text("Size chunein (stock mein):", style = MaterialTheme.typography.bodyMedium)
            Row {
                sizes.forEach { s ->
                    FilterChip(
                        selected = size == s,
                        onClick = { size = s; qty = 1 },
                        label = { Text(s) },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
            }
            if (sizes.isEmpty()) Text("Is article ka koi size stock mein nahi hai.")
            Text("Stock: $stockQty joray")
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { if (qty > 1) qty-- }) { Text("−") }
                Text("  $qty  ", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { if (qty < stockQty) qty++ }) { Text("+") }
                Spacer(Modifier.weight(1f))
                Button(onClick = {
                    val p = selected
                    if (p == null || size.isEmpty() || qty < 1) {
                        message = "Pehle article, size aur miqdaar chunein."
                        return@onClick
                    }
                    val existing = cart.indexOfFirst { it.product.id == p.id && it.size == size }
                    if (existing >= 0) {
                        val old = cart[existing]
                        cart[existing] = old.copy(qty = (old.qty + qty).coerceAtMost(stockQty))
                    } else {
                        cart.add(CartLine(p, size, qty))
                    }
                    message = ""
                }) { Text("Cart mein daalo") }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Cart:", style = MaterialTheme.typography.titleMedium)
        if (cart.isEmpty()) Text("Cart khaali hai.")
        cart.forEachIndexed { i, line ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${line.product.name} (Size ${line.size}) x ${line.qty} = Rs ${line.product.salePrice * line.qty}",
                    Modifier.weight(1f)
                )
                OutlinedButton(onClick = { cart.removeAt(i) }) { Text("✕") }
            }
        }
        Spacer(Modifier.height(8.dp))
        TextField(
            value = customer,
            onValueChange = { customer = it },
            label = { Text("Customer ka naam (udhaar ho to zaroori)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        TextField(
            value = paid,
            onValueChange = { paid = it.filter { c -> c.isDigit() } },
            label = { Text("Jama raqam (Rs)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Text("Total: Rs $total", style = MaterialTheme.typography.titleMedium)
        if (balance > 0) Text("Udhaar banega: Rs $balance", color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                if (cart.isEmpty()) {
                    message = "Cart khaali hai."
                    return@onClick
                }
                for (line in cart) {
                    val left = stock.find { it.productId == line.product.id && it.size == line.size }?.qty ?: 0
                    if (line.qty > left) {
                        message = "Stock kam hai: ${line.product.name} (Size ${line.size})"
                        return@onClick
                    }
                }
                if (balance > 0 && customer.isBlank()) {
                    message = "Udhaar ke liye customer ka naam zaroori hai."
                    return@onClick
                }
                val savedTotal = total
                vm.makeSale(customer.trim(), paidInt, cart.toList()) {
                    cart.clear()
                    customer = ""
                    paid = ""
                    message = "Sale save ho gayi! Total Rs $savedTotal"
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Sale Save karo") }
        if (message.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(message, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun StockScreen(vm: ShopViewModel) {
    val products by vm.products.collectAsState()
    val stock by vm.allStock.collectAsState()
    var name by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var sale by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("Stock", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        TextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Article ka naam") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(
                value = cost,
                onValueChange = { cost = it.filter { c -> c.isDigit() } },
                label = { Text("Kharid price (Rs)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            TextField(
                value = sale,
                onValueChange = { sale = it.filter { c -> c.isDigit() } },
                label = { Text("Sale price (Rs)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            val c = cost.toIntOrNull()
            val s = sale.toIntOrNull()
            if (name.isBlank() || c == null || s == null || c <= 0 || s <= 0) {
                message = "Naam aur dono price sahi likhein."
                return@onClick
            }
            vm.addProduct(name.trim(), c, s)
            name = ""
            cost = ""
            sale = ""
            message = "Article add ho gaya."
        }) { Text("Article Add karo") }
        if (message.isNotEmpty()) Text(message)
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(products, key = { it.id }) { p ->
                ProductStockCard(p, stock.filter { it.productId == p.id }) { sz, q ->
                    vm.addStock(p.id, sz, q)
                }
            }
        }
    }
}

@Composable
fun ProductStockCard(p: Product, stock: List<Stock>, onAdd: (String, Int) -> Unit) {
    var size by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("") }
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(10.dp)) {
            Text(p.name, style = MaterialTheme.typography.titleSmall)
            Text("Kharid Rs ${p.costPrice}  |  Sale Rs ${p.salePrice}", style = MaterialTheme.typography.bodySmall)
            if (stock.isEmpty()) Text("Stock mein kuch nahi.")
            stock.forEach { s -> Text("Size ${s.size}: ${s.qty} joray") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = size,
                    onValueChange = { size = it },
                    label = { Text("Size") },
                    modifier = Modifier.weight(1f)
                )
                TextField(
                    value = qty,
                    onValueChange = { qty = it.filter { c -> c.isDigit() } },
                    label = { Text("Miqdaar") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = {
                    val q = qty.toIntOrNull()
                    if (size.isBlank() || q == null || q <= 0) return@onClick
                    onAdd(size.trim(), q)
                    size = ""
                    qty = ""
                }) { Text("+") }
            }
        }
    }
}

@Composable
fun KhataScreen(vm: ShopViewModel) {
    val sales by vm.sales.collectAsState()
    val payments by vm.payments.collectAsState()
    var paySaleId by remember { mutableStateOf<Long?>(null) }
    var payAmount by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    val byCustomer = sales.filter { it.customer.isNotBlank() }.groupBy { it.customer }
    val withBalance = byCustomer
        .mapValues { (_, list) -> list.sumOf { vm.balanceOf(it, payments) } }
        .filterValues { it > 0 }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("Khata (Udhaar)", style = MaterialTheme.typography.headlineMedium)
        if (message.isNotEmpty()) Text(message)
        if (withBalance.isEmpty()) Text("Kisi ka udhaar baaqi nahi hai.")
        LazyColumn(Modifier.fillMaxSize()) {
            withBalance.forEach { (customer, totalBal) ->
                item(key = customer) {
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.padding(10.dp)) {
                            Text(customer, style = MaterialTheme.typography.titleSmall)
                            Text("Total Udhaar: Rs $totalBal", color = MaterialTheme.colorScheme.error)
                            byCustomer[customer]!!
                                .filter { vm.balanceOf(it, payments) > 0 }
                                .forEach { s ->
                                    val bal = vm.balanceOf(s, payments)
                                    Text(
                                        "${fmtDate(s.date)} — Bill Rs ${s.total}, Jama Rs ${s.total - bal}, Baqi Rs $bal",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    if (paySaleId == s.id) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            TextField(
                                                value = payAmount,
                                                onValueChange = { payAmount = it.filter { c -> c.isDigit() } },
                                                label = { Text("Wusooli (Rs)") },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                modifier = Modifier.weight(1f)
                                            )
                                            Button(onClick = {
                                                val amt = payAmount.toIntOrNull()
                                                if (amt == null || amt <= 0 || amt > bal) {
                                                    message = "Raqam 1 se $bal ke darmiyan likhein."
                                                    return@onClick
                                                }
                                                vm.recordPayment(s.id, amt)
                                                paySaleId = null
                                                payAmount = ""
                                                message = "Wusooli save ho gayi."
                                            }) { Text("Jama karo") }
                                        }
                                    } else {
                                        OutlinedButton(onClick = { paySaleId = s.id; payAmount = "" }) {
                                            Text("Wusooli darj karein")
                                        }
                                    }
                                }
                        }
                    }
                }
            }
        }
    }
}
