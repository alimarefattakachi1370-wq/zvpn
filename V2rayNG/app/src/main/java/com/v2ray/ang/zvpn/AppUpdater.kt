package com.v2ray.ang.zvpn

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Self-update: checks update.php on the host, downloads the APK and hands it to the system installer. */
object AppUpdater {
    class Info(val build: Int, val name: String, val url: String, val notes: String, val size: Long)

    private const val ACTION = "com.v2ray.ang.zvpn.UPDATE_RESULT"
    private var receiver: BroadcastReceiver? = null

    /** Build number written into assets/zvpn_build.txt by the GitHub workflow. */
    fun currentBuild(ctx: Context): Int = try {
        ctx.assets.open("zvpn_build.txt").bufferedReader().use { it.readText().trim().toInt() }
    } catch (e: Exception) {
        0
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

    /** Blocking. Downloads the APK into the cache dir. */
    fun download(ctx: Context, info: Info, onProgress: (Int) -> Unit): File {
        val out = File(ctx.cacheDir, "zvpn-update.apk")
        val c = URL(info.url).openConnection() as HttpURLConnection
        c.connectTimeout = 20000
        c.readTimeout = 30000
        try {
            if (c.responseCode != 200) throw IOException("HTTP ${c.responseCode}")
            val total = if (c.contentLengthLong > 0) c.contentLengthLong else info.size
            c.inputStream.use { inp ->
                out.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var last = -1
                    while (true) {
                        val n = inp.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val p = (done * 100 / total).toInt()
                            if (p != last) {
                                last = p
                                onProgress(p)
                            }
                        }
                    }
                }
            }
        } finally {
            c.disconnect()
        }
        return out
    }

    /** Returns false when the user still has to allow "install unknown apps" (the settings page is opened). */
    fun install(ctx: Context, apk: File): Boolean {
        val app = ctx.applicationContext
        if (Build.VERSION.SDK_INT >= 26 && !app.packageManager.canRequestPackageInstalls()) {
            app.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
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
                    else -> Toast.makeText(
                        c,
                        "نصب بروزرسانی ناموفق بود: " + (i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""),
                        Toast.LENGTH_LONG
                    ).show()
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
    class Downloading(val progress: Int) : UState()
    class Message(val text: String) : UState()
}

private fun mb(b: Long): String = String.format(Locale.US, "%.1f MB", b / 1_048_576.0)

@Composable
fun UpdateButton(baseUrl: String, app: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var st by remember { mutableStateOf<UState>(UState.Idle) }

    val check: () -> Unit = {
        if (st !is UState.Checking && st !is UState.Downloading) {
            st = UState.Checking
            scope.launch {
                val info = withContext(Dispatchers.IO) { runCatching { AppUpdater.fetch(baseUrl, app) }.getOrNull() }
                st = when {
                    info == null -> UState.Message("اتصال به سرور بروزرسانی ممکن نشد یا هنوز نسخه‌ای منتشر نشده است.")
                    info.build > AppUpdater.currentBuild(ctx) -> UState.Available(info)
                    else -> UState.Message("برنامه به‌روز است.")
                }
            }
        }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Brush.linearGradient(listOf(Color(0xFF22C55E), Color(0xFF047857))))
            .clickable(onClick = check)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (st is UState.Checking) "در حال بررسی…" else "بروزرسانی برنامه",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }

    when (val s = st) {
        is UState.Available -> AlertDialog(
            onDismissRequest = { st = UState.Idle },
            title = { Text("نسخهٔ جدید موجود است") },
            text = {
                Column {
                    Text("نسخه ${s.info.name}" + if (s.info.size > 0) " · ${mb(s.info.size)}" else "")
                    if (s.info.notes.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(s.info.notes)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    st = UState.Downloading(0)
                    scope.launch {
                        try {
                            val f = withContext(Dispatchers.IO) {
                                AppUpdater.download(ctx, s.info) { p -> st = UState.Downloading(p) }
                            }
                            val started = withContext(Dispatchers.IO) { AppUpdater.install(ctx, f) }
                            st = if (started) UState.Idle
                            else UState.Message("اجازهٔ «نصب برنامه‌های ناشناس» را برای این برنامه فعال کن، برگرد و دوباره بروزرسانی را بزن.")
                        } catch (e: Exception) {
                            st = UState.Message("دانلود ناموفق بود: ${e.message ?: ""}")
                        }
                    }
                }) { Text("بروزرسانی") }
            },
            dismissButton = { TextButton(onClick = { st = UState.Idle }) { Text("بعداً") } }
        )
        is UState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("در حال دانلود…") },
            text = {
                Column {
                    LinearProgressIndicator(progress = { s.progress / 100f })
                    Spacer(Modifier.height(8.dp))
                    Text("${s.progress}%")
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
