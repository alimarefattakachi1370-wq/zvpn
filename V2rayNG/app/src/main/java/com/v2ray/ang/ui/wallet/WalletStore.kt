package com.v2ray.ang.ui.wallet

import android.content.Context
import java.util.Calendar

/** Demo-only local wallet. Replace with real backend calls later. */
data class Plan(val days: Int, val price: Long, val title: String)

data class WalletTx(val time: Long, val title: String, val amount: Long)

object WalletStore {
    private const val DAY = 24L * 60 * 60 * 1000

    val plans = listOf(
        Plan(30, 120_000, "اشتراک ۳۰ روزه"),
        Plan(90, 300_000, "اشتراک ۹۰ روزه"),
        Plan(180, 540_000, "اشتراک ۱۸۰ روزه"),
    )

    private fun sp(c: Context) = c.getSharedPreferences("zvpn_wallet", Context.MODE_PRIVATE)

    fun balance(c: Context): Long = sp(c).getLong("balance", 0L)
    fun expiry(c: Context): Long = sp(c).getLong("expiry", 0L)
    fun hasActiveSubscription(c: Context): Boolean = expiry(c) > System.currentTimeMillis()

    fun daysLeft(c: Context): Long {
        val left = expiry(c) - System.currentTimeMillis()
        return if (left <= 0) 0 else (left + DAY - 1) / DAY
    }

    fun history(c: Context): List<WalletTx> {
        val raw = sp(c).getString("history", "") ?: ""
        return raw.split("\n").mapNotNull { line ->
            val p = line.split("|")
            if (p.size == 3) {
                val t = p[0].toLongOrNull()
                val a = p[2].toLongOrNull()
                if (t != null && a != null) WalletTx(t, p[1], a) else null
            } else null
        }
    }

    private fun addHistory(c: Context, tx: WalletTx) {
        val list = (listOf(tx) + history(c)).take(15)
        sp(c).edit().putString("history", list.joinToString("\n") { "${it.time}|${it.title}|${it.amount}" }).apply()
    }

    fun topUp(c: Context, amount: Long) {
        sp(c).edit().putLong("balance", balance(c) + amount).apply()
        addHistory(c, WalletTx(System.currentTimeMillis(), "شارژ کیف پول", amount))
    }

    /** Returns false when the balance is not enough. */
    fun buyPlan(c: Context, plan: Plan): Boolean {
        val bal = balance(c)
        if (bal < plan.price) return false
        val now = System.currentTimeMillis()
        val base = maxOf(now, expiry(c))
        sp(c).edit()
            .putLong("balance", bal - plan.price)
            .putLong("expiry", base + plan.days * DAY)
            .apply()
        addHistory(c, WalletTx(now, plan.title, -plan.price))
        return true
    }
}

fun String.toFa(): String = map { if (it in '0'..'9') '۰' + (it - '0') else it }.joinToString("")

fun Long.toToman(): String = String.format(java.util.Locale.US, "%,d", this).replace(',', '٬').toFa() + " تومان"

fun jalaliDate(millis: Long, withTime: Boolean = false): String {
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    val gy = cal.get(Calendar.YEAR)
    val gm = cal.get(Calendar.MONTH) + 1
    val gd = cal.get(Calendar.DAY_OF_MONTH)
    val gdm = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
    val gy2 = if (gm > 2) gy + 1 else gy
    var days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) + ((gy2 + 399) / 400) + gd + gdm[gm - 1]
    var jy = -1595 + (33 * (days / 12053))
    days %= 12053
    jy += 4 * (days / 1461)
    days %= 1461
    if (days > 365) {
        jy += (days - 1) / 365
        days = (days - 1) % 365
    }
    val jm = if (days < 186) 1 + days / 31 else 7 + (days - 186) / 30
    val jd = 1 + if (days < 186) days % 31 else (days - 186) % 30
    val date = String.format(java.util.Locale.US, "%d/%02d/%02d", jy, jm, jd)
    val out = if (withTime) date + String.format(java.util.Locale.US, " %02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)) else date
    return out.toFa()
}
