package ir.zdot.vpnadmin

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONObject

class SmsForwardWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val prefs = Prefs.get(applicationContext)
        val sender = inputData.getString("sender") ?: ""
        val body = inputData.getString("body") ?: return Result.failure()
        val ts = inputData.getLong("ts", System.currentTimeMillis() / 1000)
        return try {
            val res = Net.request(
                "${prefs.baseUrl}/api.php", "POST", mapOf("X-API-Key" to prefs.apiKey),
                JSONObject().put("sender", sender).put("body", body).put("ts", ts)
            )
            val code = res.first
            when {
                code == 200 -> {
                    val j = try { JSONObject(res.second) } catch (e: Exception) { JSONObject() }
                    val msg = when {
                        j.optBoolean("duplicate", false) -> "تکراری (قبلاً ثبت شده)"
                        !j.optBoolean("parsed", true) -> "واریز نبود؛ نادیده گرفته شد"
                        j.optBoolean("matched", false) -> "واریز تطبیق داده شد و کیف پول شارژ شد ✔"
                        else -> "واریز ثبت شد ولی با پرداختی تطبیق نداشت"
                    }
                    prefs.log("پیامک $sender → $msg")
                    Result.success()
                }
                code == 401 || code == 403 -> {
                    prefs.log("خطا: کلید API اشتباه است")
                    Result.failure()
                }
                code in 400..499 && code != 429 -> {
                    prefs.log("سرور پیامک را نپذیرفت (کد $code)")
                    Result.failure()
                }
                else -> retryOrFail(prefs, "خطای سرور (کد $code)")
            }
        } catch (e: Exception) {
            retryOrFail(prefs, "عدم اتصال به سرور")
        }
    }

    private fun retryOrFail(prefs: Prefs, why: String): Result {
        return if (runAttemptCount < 40) {
            Result.retry()
        } else {
            prefs.log("ارسال پیامک به سرور ناموفق ماند: $why")
            Result.failure()
        }
    }
}
