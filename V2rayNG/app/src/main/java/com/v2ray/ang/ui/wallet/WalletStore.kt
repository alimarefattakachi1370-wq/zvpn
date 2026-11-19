package com.v2ray.ang.ui.wallet

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/** Local cache of the server state (wallet / subscription). The server is the source of truth. */
data class Plan(val code: String, val days: Int, val price: Long, val title: String)

data class WalletTx(val time: Long, val title: String, val amount: Long)

object WalletStore {
    private const val DAY = 24L * 60 * 60 * 1000

    private fun sp(c: Context) = c.getSharedPreferences("zvpn_cache", Context.MODE_PRIVATE)

    fun balance(c: Context): Long = sp(c).getLong("bal", 0L)

    /** Subscription end in the device clock (ms); 0 = none. */
    fun expiry(c: Context): Long = sp(c).getLong("exp", 0L)

    fun hasActiveSubscription(c: Context): Boolean = expiry(c) > System.currentTimeMillis()

    fun daysLeft(c: Context): Long {
        val left = expiry(c) - System.currentTimeMillis()
        return if (left <= 0) 0 else (left + DAY - 1) / DAY
    }

    fun clearExpiry(c: Context) {
        sp(c).edit().putLong("exp", 0L).apply()
    }

    fun plans(c: Context): List<Plan> = try {
        val arr = JSONArray(sp(c).getString("plans", "[]") ?: "[]")
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Plan(o.getString("code"), o.getInt("days"), o.getLong("price"), o.getString("title"))
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun history(c: Context): List<WalletTx> = try {
        val arr = JSONArray(sp(c).getString("hist", "[]") ?: "[]")
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            WalletTx(o.getLong("time_ms"), o.getString("title"), o.getLong("amount"))
        }
    } catch (e: Exception) {
        emptyList()
    }

    /** Store the state object returned by the server (register / state / payment / buy). */
    fun save(c: Context, json: JSONObject) {
        val offset = json.optLong("server_ms", System.currentTimeMillis()) - System.currentTimeMillis()
        val until = json.optLong("sub_until_ms", 0L)
        sp(c).edit()
            .putLong("bal", json.optLong("wallet", 0L))
            .putLong("exp", if (until > 0) until - offset else 0L)
            .putString("plans", (json.optJSONArray("plans") ?: JSONArray()).toString())
            .putString("hist", (json.optJSONArray("history") ?: JSONArray()).toString())
            .apply()
    }
}

fun String.toFa(): String = map { if (it in '0'..'9') '۰' + (it - '0') else it }.joinToString("")

fun Long.toFaNumber(): String = String.format(java.util.Locale.US, "%,d", this).replace(',', '٬').toFa()

fun Long.toToman(): String = toFaNumber() + " تومان"

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
