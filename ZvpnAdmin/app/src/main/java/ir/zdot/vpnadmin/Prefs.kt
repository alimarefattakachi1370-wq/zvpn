package ir.zdot.vpnadmin

import android.content.Context
import org.json.JSONArray

class Prefs private constructor(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("zvpn_admin", Context.MODE_PRIVATE)

    var baseUrl: String
        get() = (sp.getString("base_url", null) ?: "https://zdot.ir/zvpn").trimEnd('/')
        set(v) = sp.edit().putString("base_url", v.trim().trimEnd('/')).apply()

    var adminToken: String?
        get() = sp.getString("admin_token", null)
        set(v) = sp.edit().putString("admin_token", v).apply()

    /** Key for the machine endpoints (api.php): bank SMS upload + login-code SMS gateway. */
    var apiKey: String
        get() = sp.getString("api_key", "") ?: ""
        set(v) = sp.edit().putString("api_key", v.trim()).apply()

    var smsForward: Boolean
        get() = sp.getBoolean("sms_forward", false)
        set(v) = sp.edit().putBoolean("sms_forward", v).apply()

    var gatewayOn: Boolean
        get() = sp.getBoolean("gateway_on", false)
        set(v) = sp.edit().putBoolean("gateway_on", v).apply()

    /** Comma separated sender filters (e.g. "MELLAT,1000"); empty = every sender. */
    var senderFilter: String
        get() = sp.getString("sender_filter", "") ?: ""
        set(v) = sp.edit().putString("sender_filter", v).apply()

    fun log(line: String) {
        synchronized(this) {
            val arr = try { JSONArray(sp.getString("log", "[]")) } catch (e: Exception) { JSONArray() }
            val items = ArrayList<String>()
            items.add(System.currentTimeMillis().toString() + "|" + line)
            for (i in 0 until minOf(arr.length(), 29)) items.add(arr.getString(i))
            sp.edit().putString("log", JSONArray(items).toString()).apply()
        }
    }

    fun logs(): List<Pair<Long, String>> = try {
        val arr = JSONArray(sp.getString("log", "[]"))
        (0 until arr.length()).map {
            val s = arr.getString(it)
            val i = s.indexOf('|')
            Pair(s.substring(0, i).toLongOrNull() ?: 0L, s.substring(i + 1))
        }
    } catch (e: Exception) {
        emptyList()
    }

    companion object {
        @Volatile private var inst: Prefs? = null
        fun get(ctx: Context): Prefs = inst ?: synchronized(this) { inst ?: Prefs(ctx).also { inst = it } }
    }
}
