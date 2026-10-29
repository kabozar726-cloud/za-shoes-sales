package com.zashoes.sales

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
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

fun startOfMonthMillis(): Long {
    val cal = Calendar.getInstance()
    cal.set(Calendar.DAY_OF_MONTH, 1)
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Receipt Share karein"))
}

// Shop address shown on the home screen and on every receipt.
const val SHOP_ADDRESS = "Wall Street Plaza, Shop No. 1, Basement, G-15 Markaz, Islamabad"

// One of the three shoe photos, cycled by article id, so every article
// row shows a real shoe image.
fun shoeImageFor(productId: Long): Int {
    return when ((productId % 3).toInt()) {
        0 -> R.drawable.shoe_sneakers
        1 -> R.drawable.shoe_sandal
        else -> R.drawable.shoe_formal
    }
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
        containerColor = Color(0xFFFFF8E1),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("ZA Shoes") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color(0xFF1B5E20),
                    titleContentColor = Color.White
                )
            )
        },
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
    val context = LocalContext.current
    var receiptSale by remember { mutableStateOf<Sale?>(null) }

    val start = remember { startOfDayMillis() }
    val monthStart = remember { startOfMonthMillis() }
    val todaySales = sales.filter { it.date >= start }
    val todayIds = todaySales.map { it.id }.toSet()
    val monthSales = sales.filter { it.date >= monthStart }
    val monthIds = monthSales.map { it.id }.toSet()
    val costMap = products.associate { it.id to it.costPrice }
    val nameMap = products.associate { it.id to it.name }
    val todayTotal = todaySales.sumOf { it.total }
    val todayProfit = items.filter { it.saleId in todayIds }
        .sumOf { (it.price - (costMap[it.productId] ?: 0)) * it.qty }
    val monthTotal = monthSales.sumOf { it.total }
    val monthProfit = items.filter { it.saleId in monthIds }
        .sumOf { (it.price - (costMap[it.productId] ?: 0)) * it.qty }
    val totalUdhaar = sales.sumOf { vm.balanceOf(it, payments) }
    val lowStock = stock.count { it.qty <= 2 }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.shoe_sneakers),
            contentDescription = "ZA Shoes",
            modifier = Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.height(8.dp))
        Text(SHOP_ADDRESS, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        Text("Aaj ka hisaab", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("Aaj ki Sale", "Rs $todayTotal", Modifier.weight(1f))
            StatCard("Aaj ka Profit", "Rs $todayProfit", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("Is Mahine ki Sale", "Rs $monthTotal", Modifier.weight(1f))
            StatCard("Is Mahine ka Profit", "Rs $monthProfit", Modifier.weight(1f))
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
        Text("Haal ki Salein (receipt dekhne ke liye dabayein)", style = MaterialTheme.typography.titleMedium)
        sales.take(10).forEach { s ->
            val bal = vm.balanceOf(s, payments)
            Card(
                onClick = { receiptSale = s },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
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

    val rs = receiptSale
    if (rs != null) {
        val bal = vm.balanceOf(rs, payments)
        val lines = items.filter { it.saleId == rs.id }.map { item ->
            "${nameMap[item.productId] ?: "Article"} (Size ${item.size}) x ${item.qty} = Rs ${item.price * item.qty}"
        }
        val text = buildReceiptText(rs.date, lines, rs.total, rs.paid, bal)
        AlertDialog(
            onDismissRequest = { receiptSale = null },
            containerColor = Color.White,
            title = { Text("Receipt — ZA Shoes") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(text, fontFamily = FontFamily.Monospace)
                }
            },
            confirmButton = {
                TextButton(onClick = { shareText(context, text) }) { Text("Share karein") }
            },
            dismissButton = {
                TextButton(onClick = { receiptSale = null }) { Text("Band karein") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaleScreen(vm: ShopViewModel) {
    val products by vm.products.collectAsState()
    val stock by vm.allStock.collectAsState()
    val context = LocalContext.current
    var selected by remember { mutableStateOf<Product?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var size by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf(1) }
    var customer by remember { mutableStateOf("") }
    var paid by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var lastReceipt by remember { mutableStateOf<Pair<Sale, List<CartLine>>?>(null) }
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
                    } else {
                        val existing = cart.indexOfFirst { it.product.id == p.id && it.size == size }
                        if (existing >= 0) {
                            val old = cart[existing]
                            cart[existing] = old.copy(qty = (old.qty + qty).coerceAtMost(stockQty))
                        } else {
                            cart.add(CartLine(p, size, qty))
                        }
                        message = ""
                    }
                }) { Text("Cart mein daalo") }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Cart:", style = MaterialTheme.typography.titleMedium)
        if (cart.isEmpty()) Text("Cart khaali hai.")
        cart.forEachIndexed { i, line ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = shoeImageFor(line.product.id)),
                    contentDescription = line.product.name,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Text(
                    "${line.product.name} (Size ${line.size}) x ${line.qty} = Rs ${line.product.salePrice * line.qty}",
                    Modifier.weight(1f).padding(start = 8.dp)
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
                } else {
                    var stockProblem: String? = null
                    for (line in cart) {
                        val left = stock.find { it.productId == line.product.id && it.size == line.size }?.qty ?: 0
                        if (line.qty > left) {
                            stockProblem = "Stock kam hai: ${line.product.name} (Size ${line.size})"
                        }
                    }
                    if (stockProblem != null) {
                        message = stockProblem
                    } else if (balance > 0 && customer.isBlank()) {
                        message = "Udhaar ke liye customer ka naam zaroori hai."
                    } else {
                        val savedTotal = total
                        val savedCart = cart.toList()
                        vm.makeSale(customer.trim(), paidInt, savedCart) { sale, lines ->
                            cart.clear()
                            customer = ""
                            paid = ""
                            lastReceipt = sale to lines
                            message = "Sale save ho gayi! Total Rs $savedTotal"
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Sale Save karo") }
        if (message.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(message, color = MaterialTheme.colorScheme.primary)
        }
        val receipt = lastReceipt
        if (receipt != null) {
            val (sale, lines) = receipt
            val bal = sale.total - sale.paid
            val text = buildReceiptText(
                sale.date,
                lines.map { "${it.product.name} (Size ${it.size}) x ${it.qty} = Rs ${it.product.salePrice * it.qty}" },
                sale.total,
                sale.paid,
                bal
            )
            Spacer(Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("Receipt — ZA Shoes", style = MaterialTheme.typography.titleMedium)
                    Text(text, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { shareText(context, text) }) { Text("Receipt Share karein") }
                        OutlinedButton(onClick = { lastReceipt = null }) { Text("Band karein") }
                    }
                }
            }
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
    var query by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    val filtered = products.filter { it.name.contains(query, ignoreCase = true) }

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
            } else {
                vm.addProduct(name.trim(), c, s)
                name = ""
                cost = ""
                sale = ""
                message = "Article add ho gaya."
            }
        }) { Text("Article Add karo") }
        if (message.isNotEmpty()) Text(message)
        Spacer(Modifier.height(8.dp))
        TextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Article dhoondhein") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(filtered, key = { it.id }) { p ->
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = shoeImageFor(p.id)),
                    contentDescription = p.name,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Column(Modifier.padding(start = 8.dp)) {
                    Text(p.name, style = MaterialTheme.typography.titleSmall)
                    Text("Kharid Rs ${p.costPrice}  |  Sale Rs ${p.salePrice}", style = MaterialTheme.typography.bodySmall)
                }
            }
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
                    if (size.isNotBlank() && q != null && q > 0) {
                        onAdd(size.trim(), q)
                        size = ""
                        qty = ""
                    }
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
                                                } else {
                                                    vm.recordPayment(s.id, amt)
                                                    paySaleId = null
                                                    payAmount = ""
                                                    message = "Wusooli save ho gayi."
                                                }
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
