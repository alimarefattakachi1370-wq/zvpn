package ir.zdot.vpnadmin

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Net {
    class ApiException(val httpCode: Int, message: String, val code: String? = null) : Exception(message)

    fun request(url: String, method: String, headers: Map<String, String>, body: JSONObject?): Pair<Int, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 25_000
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "ZVPN-Admin/1.0")
            for ((k, v) in headers) conn.setRequestProperty(k, v)
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

    fun parse(code: Int, text: String): JSONObject {
        val json = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw ApiException(code, "پاسخ نامعتبر از سرور (کد $code)")
        }
        if (!json.optBoolean("ok", false)) {
            throw ApiException(code, json.optString("error", "خطای ناشناخته"), json.optString("code", "").ifEmpty { null })
        }
        return json
    }
}
