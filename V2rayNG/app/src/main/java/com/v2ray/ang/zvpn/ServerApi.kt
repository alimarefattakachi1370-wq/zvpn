package com.v2ray.ang.zvpn

import android.content.Context
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager

object ServerApi {
    data class Result(val ok: Boolean, val error: String? = null)

    /** Blocking call; run it on a background thread. Downloads the config of the user's subscription and selects it. */
    fun fetchAndImport(context: Context): Result {
        val hasCached = !MmkvManager.getSelectServer().isNullOrEmpty()
        var result: Result = Result(false)
        try {
            val text = ZvpnApi.fetchConfig(context)
            val imported = AngConfigManager.importBatchConfig(text, "", false)
            if (imported.first <= 0) {
                result = if (hasCached) Result(true) else Result(false, "کانفیگ دریافتی معتبر نیست")
            } else {
                val guid = MmkvManager.decodeServerList("").firstOrNull()
                if (guid != null) MmkvManager.setSelectServer(guid)
                result = Result(true)
            }
        } catch (e: ZvpnApi.ApiException) {
            result = if (e.code == "expired" || e.httpCode == 402) {
                Result(false, "اعتبار شما به پایان رسیده است")
            } else if (e.code == "disabled" || e.code == "login") {
                Result(false, e.message ?: "دسترسی مجاز نیست")
            } else {
                Result(false, e.message ?: "خطا در دریافت کانفیگ")
            }
        } catch (e: Exception) {
            // Network problem: keep working with the last downloaded config
            result = if (hasCached) Result(true) else Result(false, "اتصال به سرور برقرار نشد")
        }
        return result
    }
}
