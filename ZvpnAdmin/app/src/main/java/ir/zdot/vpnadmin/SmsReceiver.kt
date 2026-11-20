package ir.zdot.vpnadmin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/** Receives every incoming SMS and uploads the bank messages to the server (retrying until delivered). */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val prefs = Prefs.get(context)
        if (!prefs.smsForward || prefs.apiKey.isEmpty()) return
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (msgs == null || msgs.isEmpty()) return

        val sender = msgs[0].originatingAddress ?: ""
        val body = msgs.joinToString("") { it.messageBody ?: "" }
        if (body.isBlank()) return
        val tsSec = msgs[0].timestampMillis / 1000

        val filters = prefs.senderFilter.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (filters.isNotEmpty() && filters.none { sender.contains(it, ignoreCase = true) }) return

        val req = OneTimeWorkRequestBuilder<SmsForwardWorker>()
            .setInputData(workDataOf("sender" to sender, "body" to body, "ts" to tsSec))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueue(req)
        prefs.log("پیامک از $sender دریافت شد؛ در صف ارسال به سرور")
    }
}
