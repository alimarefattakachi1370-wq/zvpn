package ir.zdot.vpnadmin

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private fun has(ctx: android.content.Context, perm: String): Boolean =
    ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED

@Composable
fun GatewayTab(api: AdminApi, onAuth: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs.get(ctx) }
    var apiKey by remember { mutableStateOf(prefs.apiKey) }
    var filter by remember { mutableStateOf(prefs.senderFilter) }
    var smsForward by remember { mutableStateOf(prefs.smsForward) }
    var gatewayOn by remember { mutableStateOf(prefs.gatewayOn) }
    var info by remember { mutableStateOf<GatewayInfo?>(null) }
    var infoError by remember { mutableStateOf<String?>(null) }
    var logTick by remember { mutableStateOf(0) }
    var pendingToggle by remember { mutableStateOf("") }
    var note by remember { mutableStateOf<String?>(null) }

    fun applyForward(on: Boolean) {
        prefs.smsForward = on
        smsForward = on
    }

    fun applyGateway(on: Boolean) {
        prefs.gatewayOn = on
        gatewayOn = on
        val i = Intent(ctx, GatewayService::class.java)
        if (on) ContextCompat.startForegroundService(ctx, i) else ctx.stopService(i)
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val allOk = result.values.all { it }
        if (pendingToggle == "forward") {
            if (has(ctx, Manifest.permission.RECEIVE_SMS)) applyForward(true) else note = "اجازه دریافت پیامک داده نشد."
        } else if (pendingToggle == "gateway") {
            if (has(ctx, Manifest.permission.SEND_SMS)) applyGateway(true) else note = "اجازه ارسال پیامک داده نشد."
        }
        if (!allOk && note == null) note = "برخی اجازه‌ها داده نشد."
        pendingToggle = ""
    }

    LaunchedEffect(logTick) {
        try {
            info = withContext(Dispatchers.IO) { api.gateway() }
            infoError = null
        } catch (e: Exception) {
            if (e.isLogin()) onAuth() else infoError = e.userMessage()
        }
        delay(5000)
        logTick++
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionTitle("اتصال به سرور")
        OutlinedTextField(
            value = apiKey, onValueChange = { apiKey = it.trim() }, label = { Text("کلید API (api_key در config.php)") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = filter, onValueChange = { filter = it }, label = { Text("فیلتر فرستنده پیامک بانک (اختیاری، با ویرگول)") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { prefs.apiKey = apiKey; prefs.senderFilter = filter; note = "ذخیره شد." },
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
        ) { Text("ذخیره") }
        if (note != null) Text(note ?: "", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)

        SectionTitle("دریافت پیامک بانک")
        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("ارسال پیامک‌های واریز به سرور", fontWeight = FontWeight.Bold)
                    Text("پیامک‌ها خودکار خوانده می‌شود و شارژ کاربر انجام می‌شود.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = smsForward, onCheckedChange = { on ->
                    if (!on) applyForward(false)
                    else if (apiKey.isEmpty()) { note = "ابتدا کلید API را وارد و ذخیره کنید." }
                    else {
                        prefs.apiKey = apiKey; prefs.senderFilter = filter
                        if (has(ctx, Manifest.permission.RECEIVE_SMS)) applyForward(true)
                        else { pendingToggle = "forward"; permLauncher.launch(arrayOf(Manifest.permission.RECEIVE_SMS)) }
                    }
                })
            }
        }

        SectionTitle("درگاه ارسال کد ورود")
        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("ارسال پیامک کد ورود از این گوشی", fontWeight = FontWeight.Bold)
                    Text("تا وقتی روشن است، کاربران می‌توانند با شماره موبایل وارد شوند. گوشی باید سیم‌کارت و اینترنت داشته باشد.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = gatewayOn, onCheckedChange = { on ->
                    if (!on) applyGateway(false)
                    else if (apiKey.isEmpty()) { note = "ابتدا کلید API را وارد و ذخیره کنید." }
                    else {
                        prefs.apiKey = apiKey
                        val need = ArrayList<String>()
                        if (!has(ctx, Manifest.permission.SEND_SMS)) need.add(Manifest.permission.SEND_SMS)
                        if (Build.VERSION.SDK_INT >= 33 && !has(ctx, Manifest.permission.POST_NOTIFICATIONS)) need.add(Manifest.permission.POST_NOTIFICATIONS)
                        if (need.isEmpty()) applyGateway(true)
                        else { pendingToggle = "gateway"; permLauncher.launch(need.toTypedArray()) }
                    }
                })
            }
        }
        OutlinedButton(
            onClick = { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("خاموش کردن بهینه‌سازی باتری (برای پایداری)") }

        SectionTitle("وضعیت سرور")
        val g = info
        if (g != null) {
            Text(
                if (g.online) "درگاه پیامک: آنلاین ✔" else "درگاه پیامک: آفلاین (درگاه را روشن کنید)",
                color = if (g.online) Color(0xFF16A34A) else MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold
            )
            Text("پیامک‌های در صف ارسال: ${g.queued.toLong().fa()}", fontSize = 13.sp)
            if (g.inbox.isNotEmpty()) {
                SectionTitle("پیامک‌های دریافتی اخیر")
                g.inbox.forEach { (sender, body, status) ->
                    Text("$sender · ${statusFa(status)}", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text(body.replace("\n", " "), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        ErrorText(infoError)

        SectionTitle("گزارش این گوشی")
        val logs = remember(logTick) { prefs.logs() }
        if (logs.isEmpty()) Text("هنوز رویدادی ثبت نشده.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        logs.forEach { (t, line) ->
            Text("${jalali(t, true)} — $line", fontSize = 11.sp)
        }
    }
}

private fun statusFa(s: String): String = when (s) {
    "matched" -> "تطبیق داده شد"
    "unmatched" -> "بدون تطبیق"
    "ignored" -> "نادیده گرفته شد"
    else -> s
}
