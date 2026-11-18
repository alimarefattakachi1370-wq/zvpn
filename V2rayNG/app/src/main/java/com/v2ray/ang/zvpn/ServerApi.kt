package com.v2ray.ang.zvpn

import android.content.Context
import android.provider.Settings
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import java.net.HttpURLConnection
import java.net.URL

object ServerConfig {
    /**
     * Backend endpoint that returns the user's config: vless/vmess/trojan/ss links
     * (one per line, plain text or base64). The request carries the header X-Device-Id.
     * Leave empty until the backend is ready; the app then uses the config already stored.
     */
    const val CONFIG_URL = ""
}

object ServerApi {
    data class Result(val ok: Boolean, val error: String? = null)

    /** Blocking call; run it on a background thread. */
    fun fetchAndImport(context: Context): Result {
        val hasCached = !MmkvManager.getSelectServer().isNullOrEmpty()
        if (ServerConfig.CONFIG_URL.isBlank()) {
            return if (hasCached) Result(true) else Result(false, "آدرس سرور هنوز تنظیم نشده است")
        }
        return try {
            val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
            val conn = URL(ServerConfig.CONFIG_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("X-Device-Id", deviceId)
            conn.setRequestProperty("User-Agent", "ZVPN/1.0")
            val text = try {
                if (conn.responseCode != 200) throw java.io.IOException("HTTP ${conn.responseCode}")
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
            val (count, _) = AngConfigManager.importBatchConfig(text, "", false)
            if (count <= 0) {
                return if (hasCached) Result(true) else Result(false, "کانفیگ دریافتی معتبر نیست")
            }
            val guid = MmkvManager.decodeServerList("").firstOrNull()
            if (guid != null) MmkvManager.setSelectServer(guid)
            Result(true)
        } catch (e: Exception) {
            if (hasCached) Result(true) else Result(false, "اتصال به سرور برقرار نشد")
        }
    }
}
