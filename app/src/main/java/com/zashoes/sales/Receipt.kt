package com.zashoes.sales

// Builds a plain-text receipt that can be shared (e.g. to WhatsApp)
// or printed from any app that accepts shared text.
fun buildReceiptText(
    date: Long,
    lines: List<String>,
    total: Int,
    paid: Int,
    balance: Int
): String {
    val sb = StringBuilder()
    sb.appendLine("ZA SHOES")
    sb.appendLine("Wall Street Plaza, Shop No. 1, Basement")
    sb.appendLine("G-15 Markaz, Islamabad")
    sb.appendLine("Sale Receipt")
    sb.appendLine(fmtDate(date))
    sb.appendLine("------------------------------")
    lines.forEach { sb.appendLine(it) }
    sb.appendLine("------------------------------")
    sb.appendLine("Total: Rs $total")
    sb.appendLine("Jama: Rs $paid")
    if (balance > 0) {
        sb.appendLine("Baqi (Udhaar): Rs $balance")
    } else {
        sb.appendLine("Baqi: Rs 0")
    }
    sb.appendLine("Shukriya! Dobara zaroor aayein.")
    return sb.toString()
}
