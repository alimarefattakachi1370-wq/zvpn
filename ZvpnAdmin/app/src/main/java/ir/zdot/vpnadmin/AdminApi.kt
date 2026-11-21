package ir.zdot.vpnadmin

import org.json.JSONArray
import org.json.JSONObject

data class UserItem(
    val id: Int,
    val phone: String,
    val wallet: Long,
    val subUntilMs: Long,
    val active: Boolean,
    val status: String,
    val reason: String,
    val name: String = "",
)

data class Stats(
    val users: Int,
    val activeSubs: Int,
    val disabled: Int,
    val walletTotal: Long,
    val pending: Int,
    val unmatched: Int,
    val gatewayOnline: Boolean,
)

data class Tx(val timeMs: Long, val title: String, val amount: Long)

data class PendingPayment(val id: Int, val phone: String, val payAmount: Long, val toman: Long, val expiresMs: Long)

data class DepositItem(
    val id: Int,
    val toman: Long,
    val amount: Long,
    val date: String,
    val time: String,
    val matched: Boolean,
    val assigned: Boolean,
    val phone: String?,
)

data class GatewayInfo(val online: Boolean, val queued: Int, val inbox: List<Triple<String, String, String>>)

/** Client for admin.php. All functions are blocking: call them from Dispatchers.IO. */
class AdminApi(private val prefs: Prefs) {

    private fun call(action: String, method: String = "GET", body: JSONObject? = null, query: String = ""): JSONObject {
        val token = prefs.adminToken ?: throw Net.ApiException(401, "ابتدا وارد شوید", "login")
        val res = Net.request("${prefs.baseUrl}/admin.php?a=$action$query", method, mapOf("X-Admin-Token" to token), body)
        if (res.first == 401) {
            prefs.adminToken = null
            throw Net.ApiException(401, "نشست منقضی شده؛ دوباره وارد شوید", "login")
        }
        return Net.parse(res.first, res.second)
    }

    private fun user(o: JSONObject) = UserItem(
        o.getInt("id"), o.getString("phone"), o.getLong("wallet"), o.getLong("sub_until_ms"),
        o.getBoolean("active"), o.getString("status"), o.optString("disabled_reason", ""),
        o.optString("name", ""),
    )

    fun login(username: String, password: String) {
        val res = Net.request(
            "${prefs.baseUrl}/admin.php?a=login", "POST", emptyMap(),
            JSONObject().put("username", username).put("password", password)
        )
        prefs.adminToken = Net.parse(res.first, res.second).getString("token")
    }

    fun stats(): Stats {
        val o = call("stats")
        return Stats(
            o.getInt("users"), o.getInt("active_subs"), o.getInt("disabled"), o.getLong("wallet_total"),
            o.getInt("pending_payments"), o.getInt("unmatched_deposits"), o.getBoolean("gateway_online"),
        )
    }

    fun users(q: String): List<UserItem> {
        val arr = call("users", query = "&q=" + java.net.URLEncoder.encode(q, "UTF-8")).getJSONArray("users")
        return (0 until arr.length()).map { user(arr.getJSONObject(it)) }
    }

    fun userDetail(id: Int): Pair<UserItem, List<Tx>> {
        val o = call("user", query = "&id=$id")
        val h: JSONArray = o.getJSONArray("history")
        val txs = (0 until h.length()).map {
            val x = h.getJSONObject(it)
            Tx(x.getLong("time_ms"), x.getString("title"), x.getLong("amount"))
        }
        return Pair(user(o.getJSONObject("user")), txs)
    }

    fun charge(userId: Int, amount: Long, note: String) {
        call("charge", "POST", JSONObject().put("user_id", userId).put("amount", amount).put("note", note))
    }

    fun addDays(userId: Int, days: Int) {
        call("sub", "POST", JSONObject().put("user_id", userId).put("days", days))
    }

    fun setStatus(userId: Int, status: String, reason: String) {
        call("status", "POST", JSONObject().put("user_id", userId).put("status", status).put("reason", reason))
    }

    fun deposits(): List<DepositItem> {
        val arr = call("deposits").getJSONArray("deposits")
        return (0 until arr.length()).map {
            val x = arr.getJSONObject(it)
            DepositItem(
                x.getInt("id"), x.getLong("toman"), x.getLong("amount"), x.getString("date"), x.getString("time"),
                x.getBoolean("matched"), x.getBoolean("assigned"), if (x.isNull("phone")) null else x.getString("phone"),
            )
        }
    }

    fun assignDeposit(depositId: Int, userId: Int) {
        call("assign", "POST", JSONObject().put("deposit_id", depositId).put("user_id", userId))
    }

    fun pending(): List<PendingPayment> {
        val arr = call("pending").getJSONArray("payments")
        return (0 until arr.length()).map {
            val x = arr.getJSONObject(it)
            PendingPayment(x.getInt("id"), x.getString("phone"), x.getLong("pay_amount"), x.getLong("toman"), x.getLong("expires_ms"))
        }
    }

    fun confirmPayment(paymentId: Int) {
        call("confirm", "POST", JSONObject().put("payment_id", paymentId))
    }

    fun gateway(): GatewayInfo {
        val o = call("gateway")
        val arr = o.getJSONArray("inbox")
        val inbox = (0 until arr.length()).map {
            val x = arr.getJSONObject(it)
            Triple(x.getString("sender"), x.getString("body"), x.getString("status"))
        }
        return GatewayInfo(o.getBoolean("online"), o.getInt("queued"), inbox)
    }
}
