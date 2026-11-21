package com.v2ray.ang.zvpn

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

/** Self-update: checks update.php on the host, downloads the APK and hands it to the system installer. */
object AppUpdater {
    class Info(val build: Int, val name: String, val url: String, val notes: String, val size: Long)

    /** Last install failure (full text), shown in a dialog by UpdateButton. */
    var lastError by mutableStateOf<String?>(null)

    private const val ACTION = "com.v2ray.ang.zvpn.UPDATE_RESULT"
    private var receiver: BroadcastReceiver? = null

    /** Build number written into assets/zvpn_build.txt by the GitHub workflow. */
    fun currentBuild(ctx: Context): Int = try {
        ctx.assets.open("zvpn_build.txt").bufferedReader().use { it.readText().trim().toInt() }
    } catch (e: Exception) {
        0
    }

    /** e.g. "1.0.23" (same scheme the workflow publishes). */
    fun versionName(ctx: Context): String {
        val b = currentBuild(ctx)
        return if (b > 0) "1.0.$b" else "dev"
    }

    fun canInstall(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || ctx.applicationContext.packageManager.canRequestPackageInstalls()

    fun openInstallSettings(ctx: Context) {
        val app = ctx.applicationContext
        app.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun certHash(sigs: Array<Signature>?): String {
        if (sigs == null || sigs.isEmpty()) return "?"
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(sigs[0].toByteArray()).joinToString("") { "%02x".format(it) }.take(10)
    }

    @Suppress("DEPRECATION")
    private fun sigsOf(pi: PackageInfo): Array<Signature>? =
        if (Build.VERSION.SDK_INT >= 28) pi.signingInfo?.apkContentsSigners else pi.signatures

    @Suppress("DEPRECATION")
    private fun versionOf(pi: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()

    /** Returns a readable reason when Android would refuse this APK, otherwise null. */
    @Suppress("DEPRECATION")
    fun diagnose(ctx: Context, apk: File): String? {
        return try {
            val pm = ctx.packageManager
            val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
            val neu = pm.getPackageArchiveInfo(apk.absolutePath, flags) ?: return "فایل APK دانلودشده معتبر نیست."
            val cur = pm.getPackageInfo(ctx.packageName, flags)
            if (neu.packageName != ctx.packageName) {
                return "نام بستهٔ APK جدید (${neu.packageName}) با برنامهٔ نصب‌شده (${ctx.packageName}) فرق دارد."
            }
            val a = certHash(sigsOf(cur))
            val b = certHash(sigsOf(neu))
            if (a != "?" && b != "?" && a != b) {
                return "امضای نسخهٔ جدید ($b) با نسخهٔ نصب‌شده ($a) فرق دارد.\n" +
                    "یک بار این برنامه را پاک کن و فایل APK آن را از پوشهٔ Download نصب کن؛ از آن به بعد بروزرسانی کار می‌کند."
            }
            if (versionOf(neu) < versionOf(cur)) {
                return "شمارهٔ نسخهٔ APK جدید (${versionOf(neu)}) از نسخهٔ نصب‌شده (${versionOf(cur)}) کمتر است."
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /** Blocking. Returns null when the server is unreachable or nothing was published yet. */
    fun fetch(baseUrl: String, app: String): Info? {
        val c = URL("${baseUrl.trimEnd('/')}/update.php?app=$app&t=${System.currentTimeMillis()}")
            .openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 15000
        try {
            if (c.responseCode != 200) return null
            val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            if (!j.optBoolean("ok")) return null
            return Info(j.getInt("build"), j.optString("version"), j.getString("url"), j.optString("notes"), j.optLong("size"))
        } finally {
            c.disconnect()
        }
    }

    /** Blocking. Downloads the APK into the cache dir; onProgress(done, total, bytesPerSecond). */
    fun download(ctx: Context, info: Info, onProgress: (Long, Long, Long) -> Unit): File {
        val out = File(ctx.cacheDir, "zvpn-update.apk")
        val c = URL(info.url).openConnection() as HttpURLConnection
        c.connectTimeout = 20000
        c.readTimeout = 30000
        c.setRequestProperty("Accept-Encoding", "identity")
        try {
            if (c.responseCode != 200) throw IOException("HTTP ${c.responseCode}")
            val total = if (c.contentLengthLong > 0) c.contentLengthLong else info.size
            c.inputStream.use { inp ->
                out.outputStream().use { o ->
                    val buf = ByteArray(256 * 1024)
                    var done = 0L
                    var tickBytes = 0L
                    var tickTime = System.currentTimeMillis()
                    var speed = 0L
                    while (true) {
                        val n = inp.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n)
                        done += n
                        val now = System.currentTimeMillis()
                        if (now - tickTime >= 300) {
                            speed = (done - tickBytes) * 1000 / (now - tickTime)
                            tickBytes = done
                            tickTime = now
                            onProgress(done, total, speed)
                        }
                    }
                    onProgress(done, if (total > 0) total else done, speed)
                }
            }
        } finally {
            c.disconnect()
        }
        return out
    }

    /** Returns false when "install unknown apps" is still not allowed for this app. */
    fun install(ctx: Context, apk: File): Boolean {
        val app = ctx.applicationContext
        diagnose(app, apk)?.let { lastError = it; return true }
        if (!canInstall(app)) return false
        registerReceiver(app)
        val installer = app.packageManager.packageInstaller
        val id = installer.createSession(PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL))
        installer.openSession(id).use { session ->
            session.openWrite("zvpn.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(app, id, Intent(ACTION).setPackage(app.packageName), flags)
            session.commit(pending.intentSender)
        }
        return true
    }

    private fun registerReceiver(app: Context) {
        if (receiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                when (i.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
                    PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                        @Suppress("DEPRECATION")
                        val confirm = i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                        confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        if (confirm != null) c.startActivity(confirm)
                    }
                    PackageInstaller.STATUS_SUCCESS -> {}
                    else -> lastError = "کد خطا: " + i.getIntExtra(PackageInstaller.EXTRA_STATUS, -1) + "\n" +
                        (i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "")
                }
            }
        }
        val filter = IntentFilter(ACTION)
        if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(r, filter, Context.RECEIVER_NOT_EXPORTED)
        else app.registerReceiver(r, filter)
        receiver = r
    }
}

private sealed class UState {
    object Idle : UState()
    object Checking : UState()
    class Available(val info: AppUpdater.Info) : UState()
    class NeedPermission(val info: AppUpdater.Info) : UState()
    class Downloading(val done: Long, val total: Long, val speed: Long) : UState()
    object Installing : UState()
    class Message(val text: String) : UState()
}

private fun mb(b: Long): String = String.format(Locale.US, "%.1f MB", b / 1_048_576.0)

@Composable
fun UpdateButton(baseUrl: String, app: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var st by remember { mutableStateOf<UState>(UState.Idle) }

    val check: () -> Unit = {
        if (st is UState.Idle || st is UState.Message) {
            st = UState.Checking
            scope.launch {
                val info = withContext(Dispatchers.IO) { runCatching { AppUpdater.fetch(baseUrl, app) }.getOrNull() }
                st = when {
                    info == null -> UState.Message("اتصال به سرور بروزرسانی ممکن نشد یا هنوز نسخه‌ای منتشر نشده است.")
                    info.build > AppUpdater.currentBuild(ctx) -> UState.Available(info)
                    else -> UState.Message("برنامه به‌روز است.\nنسخهٔ فعلی: v${AppUpdater.versionName(ctx)}")
                }
            }
        }
    }

    fun startDownload(info: AppUpdater.Info) {
        st = UState.Downloading(0, info.size, 0)
        scope.launch {
            try {
                val f = withContext(Dispatchers.IO) {
                    AppUpdater.download(ctx, info) { d, t, s -> st = UState.Downloading(d, t, s) }
                }
                st = UState.Installing
                val started = withContext(Dispatchers.IO) { AppUpdater.install(ctx, f) }
                st = if (started) UState.Idle else UState.NeedPermission(info)
            } catch (e: Exception) {
                st = UState.Message("خطا: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    Box(
        modifier = modifier
            .shadow(12.dp, RoundedCornerShape(50), ambientColor = Color(0xFF22C55E), spotColor = Color(0xFF22C55E))
            .clip(RoundedCornerShape(50))
            .background(Brush.verticalGradient(listOf(Color(0xFF5DE08F), Color(0xFF16A34A), Color(0xFF065F46))))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.5f), Color.Black.copy(alpha = 0.35f))), RoundedCornerShape(50))
            .clickable(onClick = check)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "بروزرسانی برنامه",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }

    AppUpdater.lastError?.let { err ->
        AlertDialog(
            onDismissRequest = { AppUpdater.lastError = null },
            title = { Text("نصب بروزرسانی ناموفق بود") },
            text = { Text(err) },
            confirmButton = { TextButton(onClick = { AppUpdater.lastError = null }) { Text("باشه") } }
        )
    }

    when (val s = st) {
        is UState.Checking -> AlertDialog(
            onDismissRequest = {},
            title = { Text("بررسی بروزرسانی") },
            text = {
                Column {
                    Text("در حال بررسی نسخهٔ جدید…")
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {}
        )
        is UState.Available -> AlertDialog(
            onDismissRequest = { st = UState.Idle },
            title = { Text("نسخهٔ جدید موجود است") },
            text = {
                Column {
                    Text("نسخهٔ فعلی: v${AppUpdater.versionName(ctx)}")
                    Text("نسخهٔ جدید: v${s.info.name}" + if (s.info.size > 0) "  ·  ${mb(s.info.size)}" else "")
                    if (s.info.notes.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(s.info.notes, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (!AppUpdater.canInstall(ctx)) st = UState.NeedPermission(s.info) else startDownload(s.info)
                }) { Text("بروزرسانی") }
            },
            dismissButton = { TextButton(onClick = { st = UState.Idle }) { Text("بعداً") } }
        )
        is UState.NeedPermission -> AlertDialog(
            onDismissRequest = { st = UState.Idle },
            title = { Text("فعال‌سازی اجازهٔ نصب (فقط یک بار)") },
            text = {
                Column {
                    Text("چون این برنامه از گوگل پلی نصب نشده، اندروید باید اجازه بدهد خودش بروزرسانی را نصب کند:", fontSize = 14.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("۱) روی «رفتن به تنظیمات» بزن.", fontSize = 14.sp)
                    Text("۲) گزینهٔ «اجازه نصب از این منبع» (Allow from this source) را روشن کن.", fontSize = 14.sp)
                    Text("۳) با دکمهٔ برگشت به برنامه برگرد و دوباره «بروزرسانی برنامه» را بزن.", fontSize = 14.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "بعد از فعال شدن، این راهنما دیگر نمایش داده نمی‌شود.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    AppUpdater.openInstallSettings(ctx)
                    st = UState.Idle
                }) { Text("رفتن به تنظیمات") }
            },
            dismissButton = { TextButton(onClick = { st = UState.Idle }) { Text("بعداً") } }
        )
        is UState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("در حال دانلود بروزرسانی…") },
            text = {
                Column {
                    if (s.total > 0) {
                        val f = (s.done.toFloat() / s.total).coerceIn(0f, 1f)
                        LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${(f * 100).toInt()}%", fontWeight = FontWeight.Bold)
                            Text("${mb(s.done)} / ${mb(s.total)}", fontSize = 13.sp)
                        }
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(10.dp))
                        Text(mb(s.done), fontSize = 13.sp)
                    }
                    if (s.speed > 0) Text("سرعت: ${mb(s.speed)}/s", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("لطفاً صبر کن و برنامه را نبند.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {}
        )
        is UState.Installing -> AlertDialog(
            onDismissRequest = {},
            title = { Text("آماده‌سازی نصب") },
            text = {
                Column {
                    Text("دانلود تمام شد؛ در حال آماده‌سازی نصب…")
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {}
        )
        is UState.Message -> AlertDialog(
            onDismissRequest = { st = UState.Idle },
            text = { Text(s.text) },
            confirmButton = { TextButton(onClick = { st = UState.Idle }) { Text("باشه") } }
        )
        else -> {}
    }
}
