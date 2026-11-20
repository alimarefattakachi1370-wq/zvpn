package ir.zdot.vpnadmin

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Keeps polling the server for login-code SMS jobs and sends them from this phone's SIM. */
class GatewayService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loopJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        if (loopJob == null) loopJob = scope.launch { loop() }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startForegroundCompat() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel("gateway", "درگاه پیامک", NotificationManager.IMPORTANCE_LOW))
        }
        val n: Notification = NotificationCompat.Builder(this, "gateway")
            .setContentTitle("درگاه پیامک ZVPN فعال است")
            .setContentText("ارسال خودکار کد ورود کاربران")
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setOngoing(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, 41, n, type)
    }

    private suspend fun loop() {
        val prefs = Prefs.get(this)
        while (scope.isActive) {
            if (!prefs.gatewayOn) {
                stopSelf()
                return
            }
            try {
                val res = Net.request("${prefs.baseUrl}/api.php?a=claim", "POST", mapOf("X-API-Key" to prefs.apiKey), JSONObject())
                val jobs = Net.parse(res.first, res.second).getJSONArray("jobs")
                for (i in 0 until jobs.length()) {
                    val j = jobs.getJSONObject(i)
                    val ok = sendSms(j.getString("phone"), j.getString("body"))
                    Net.request(
                        "${prefs.baseUrl}/api.php?a=done", "POST", mapOf("X-API-Key" to prefs.apiKey),
                        JSONObject().put("id", j.getInt("id")).put("ok", ok)
                    )
                    prefs.log(if (ok) "کد ورود به ${j.getString("phone")} ارسال شد" else "ارسال پیامک به ${j.getString("phone")} ناموفق بود")
                    delay(1500)
                }
            } catch (e: Exception) {
                // network / server problem: try again on the next round
            }
            delay(4000)
        }
    }

    private fun sendSms(phone: String, text: String): Boolean {
        return try {
            val sm: SmsManager = if (Build.VERSION.SDK_INT >= 31) {
                getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            val parts = sm.divideMessage(text)
            if (parts.size > 1) sm.sendMultipartTextMessage(phone, null, parts, null, null)
            else sm.sendTextMessage(phone, null, text, null, null)
            true
        } catch (e: Exception) {
            false
        }
    }
}
