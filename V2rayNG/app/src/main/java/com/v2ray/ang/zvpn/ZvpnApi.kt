package com.v2ray.ang.zvpn

import android.content.Context
import android.provider.Settings
import com.v2ray.ang.ui.wallet.WalletStore
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** Client for the ZVPN backend (app.php). All functions are blocking: call them from Dispatchers.IO. */
object ZvpnApi {
    /** Folder where the backend files are uploaded (without trailing slash). */
    const val BASE_URL = "https://zdot.ir/zvpn"

    class ApiException(val httpCode: Int, message: String, val code: String? = null) : Exception(message)

    data class Payment(
        val id: Long,
        val status: String,
        val payAmount: Long,
        val toman: Long,
        val unit: String,
        val cardNumber: String,
        val cardOwner: String,
        val cardBank: String,
        val expiresMs: Long,
    )

    private fun sp(c: Context) = c.getSharedPreferences("zvpn_auth", Context.MODE_PRIVATE)

    private fun deviceId(c: Context): String {
        val id = Settings.Secure.getString(c.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
        if (id.length >= 8) return id
        var gen = sp(c).getString("device_id", null)
        if (gen == null) {
            gen = UUID.randomUUID().toString().replace("-", "")
            sp(c).edit().putString("device_id", gen).apply()
        }
        return gen
    }

    private fun request(action: String, method: String, body: JSONObject?, token: String?, query: String): Pair<Int, String> {
        val conn = URL("$BASE_URL/app.php?a=$action$query").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "ZVPN/1.0")
            if (token != null) conn.setRequestProperty("X-Auth-Token", token)
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            return Pair(code, text)
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(code: Int, text: String): JSONObject {
        val json = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw ApiException(code, "پاسخ نامعتبر از سرور دریافت شد")
        }
        if (!json.optBoolean("ok", false)) {
            throw ApiException(code, json.optString("error", "خطای ناشناخته"), json.optString("code", "").ifEmpty { null })
        }
        return json
    }

    private fun register(c: Context): String {
        val res = request("register", "POST", JSONObject().put("device_id", deviceId(c)), null, "")
        val json = parse(res.first, res.second)
        val token = json.getString("token")
        sp(c).edit().putString("token", token).apply()
        WalletStore.save(c, json)
        return token
    }

    private fun token(c: Context): String = sp(c).getString("token", null) ?: register(c)

    private fun call(c: Context, action: String, method: String = "GET", body: JSONObject? = null, query: String = ""): JSONObject {
        var res = request(action, method, body, token(c), query)
        if (res.first == 401) {
            res = request(action, method, body, register(c), query)
        }
        return parse(res.first, res.second)
    }

    private fun paymentOf(o: JSONObject) = Payment(
        id = o.getLong("id"),
        status = o.getString("status"),
        payAmount = o.getLong("pay_amount"),
        toman = o.getLong("toman"),
        unit = o.optString("unit", "ریال"),
        cardNumber = o.getString("card_number"),
        cardOwner = o.getString("card_owner"),
        cardBank = o.getString("card_bank"),
        expiresMs = o.getLong("expires_ms"),
    )

    /** Refresh wallet/subscription cache. Returns the open (pending) payment if any. */
    fun refreshState(c: Context): Payment? {
        val json = call(c, "state")
        WalletStore.save(c, json)
        val p = json.optJSONObject("pending")
        return if (p != null) paymentOf(p) else null
    }

    fun topUp(c: Context, toman: Long): Payment {
        val json = call(c, "topup", "POST", JSONObject().put("amount", toman))
        return paymentOf(json.getJSONObject("payment"))
    }

    /** Poll a payment; refreshes the cache too (wallet changes when it is paid). */
    fun paymentStatus(c: Context, id: Long): Payment {
        val json = call(c, "payment", query = "&id=$id")
        WalletStore.save(c, json)
        return paymentOf(json.getJSONObject("payment"))
    }

    fun buyPlan(c: Context, planCode: String) {
        val json = call(c, "buy", "POST", JSONObject().put("plan", planCode))
        WalletStore.save(c, json)
    }

    /** Returns the vpn links (text) for users with an active subscription. */
    fun fetchConfig(c: Context): String {
        var res = request("config", "GET", null, token(c), "")
        if (res.first == 401) res = request("config", "GET", null, register(c), "")
        if (res.first == 200) return res.second
        val err = try {
            parse(res.first, res.second)
            null
        } catch (e: ApiException) {
            e
        }
        if (err != null && (err.code == "expired" || res.first == 402)) WalletStore.clearExpiry(c)
        throw err ?: ApiException(res.first, "خطا در دریافت کانفیگ")
    }
}
