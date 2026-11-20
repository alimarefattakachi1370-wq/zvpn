package com.v2ray.ang.ui.main

import android.net.TrafficStats
import android.os.Process
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.ui.wallet.WalletStore
import com.v2ray.ang.ui.wallet.jalaliDate
import com.v2ray.ang.ui.wallet.toFa
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun HomeScreen(
    isRunning: Boolean,
    busy: Boolean,
    refreshKey: Int,
    onConnectClick: () -> Unit,
    onWalletClick: () -> Unit,
    onExpired: () -> Unit,
) {
    val ctx = LocalContext.current
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            now = System.currentTimeMillis()
        }
    }
    val expiry = remember(refreshKey, now) { WalletStore.expiry(ctx) }
    val daysLeft = remember(refreshKey, now) { WalletStore.daysLeft(ctx) }
    val disabled = remember(refreshKey, now) { WalletStore.isDisabled(ctx) }
    val reason = remember(refreshKey, now) { WalletStore.disabledReason(ctx) }
    val active = !disabled && expiry > now

    LaunchedEffect(isRunning, active) {
        if (isRunning && !active) onExpired()
    }

    val green = MaterialTheme.colorScheme.primary
    val enabled = (active || isRunning) && !busy

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .systemBarsPadding()
        ) {
            // Top row: wallet + remaining subscription
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopCenter),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable(onClick = onWalletClick)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painterResource(R.drawable.ic_wallet_24dp),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "کیف پول",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            if (disabled) "حساب غیرفعال" else if (active) "${daysLeft.toString().toFa()} روز اعتبار باقی مانده" else "بدون اعتبار",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Text("ZVPN", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = green)
            }

            // Center: connect button
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(250.dp)) {
                    if (isRunning) {
                        Box(
                            Modifier
                                .size(250.dp)
                                .clip(CircleShape)
                                .background(green.copy(alpha = 0.12f))
                        )
                        Box(
                            Modifier
                                .size(222.dp)
                                .clip(CircleShape)
                                .background(green.copy(alpha = 0.22f))
                        )
                    }
                    val circleBrush = if (enabled || isRunning || busy) {
                        Brush.linearGradient(listOf(Color(0xFF22C55E), Color(0xFF047857)))
                    } else {
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.surfaceContainerHighest,
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(188.dp)
                            .clip(CircleShape)
                            .background(circleBrush)
                            .clickable(enabled = enabled, onClick = onConnectClick),
                        contentAlignment = Alignment.Center
                    ) {
                        if (busy) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(64.dp))
                        } else {
                            Icon(
                                painterResource(R.drawable.ic_power_24dp),
                                contentDescription = null,
                                tint = if (enabled || isRunning) Color.White else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(84.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                when {
                    busy -> StatusText("در حال دریافت کانفیگ...", MaterialTheme.colorScheme.onSurfaceVariant)
                    isRunning -> {
                        StatusText("متصل شد", green, bold = true)
                        Spacer(Modifier.height(4.dp))
                        StatusText("برای قطع اتصال لمس کنید", MaterialTheme.colorScheme.onSurfaceVariant, size = 13)
                        Spacer(Modifier.height(18.dp))
                        SpeedRow()
                    }
                    disabled -> {
                        StatusText("حساب شما غیرفعال شده است", MaterialTheme.colorScheme.error, bold = true)
                        if (reason.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            StatusText("علت: $reason", MaterialTheme.colorScheme.onSurfaceVariant, size = 14, modifier = Modifier.padding(horizontal = 32.dp))
                        }
                    }
                    !active -> StatusText("اعتبار شما به پایان رسیده", MaterialTheme.colorScheme.error, bold = true)
                    else -> StatusText("برای اتصال لمس کنید", MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (active) {
                Text(
                    "اعتبار تا ${jalaliDate(expiry)}",
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatusText(text: String, color: Color, bold: Boolean = false, size: Int = 17, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        color = color,
        fontSize = size.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        textAlign = TextAlign.Center
    )
}

private fun readTx(uid: Int): Long {
    val v = TrafficStats.getUidTxBytes(uid)
    return if (v >= 0) v else TrafficStats.getTotalTxBytes().coerceAtLeast(0)
}

private fun readRx(uid: Int): Long {
    val v = TrafficStats.getUidRxBytes(uid)
    return if (v >= 0) v else TrafficStats.getTotalRxBytes().coerceAtLeast(0)
}

private fun fmtBytes(b: Long, perSecond: Boolean): String {
    val suffix = if (perSecond) "/s" else ""
    return when {
        b >= 1_073_741_824L -> String.format(Locale.US, "%.2f GB%s", b / 1_073_741_824.0, suffix)
        b >= 1_048_576L -> String.format(Locale.US, "%.1f MB%s", b / 1_048_576.0, suffix)
        b >= 1024L -> String.format(Locale.US, "%.0f KB%s", b / 1024.0, suffix)
        else -> "$b B$suffix"
    }
}

@Composable
private fun SpeedRow() {
    var upSpeed by remember { mutableStateOf(0L) }
    var downSpeed by remember { mutableStateOf(0L) }
    var upTotal by remember { mutableStateOf(0L) }
    var downTotal by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        val uid = Process.myUid()
        val tx0 = readTx(uid)
        val rx0 = readRx(uid)
        var lastTx = tx0
        var lastRx = rx0
        var lastT = SystemClock.elapsedRealtime()
        while (true) {
            delay(1000)
            val t = SystemClock.elapsedRealtime()
            val tx = readTx(uid)
            val rx = readRx(uid)
            val dt = (t - lastT).coerceAtLeast(1L)
            upSpeed = ((tx - lastTx).coerceAtLeast(0L) * 1000L) / dt
            downSpeed = ((rx - lastRx).coerceAtLeast(0L) * 1000L) / dt
            upTotal = (tx - tx0).coerceAtLeast(0L)
            downTotal = (rx - rx0).coerceAtLeast(0L)
            lastTx = tx
            lastRx = rx
            lastT = t
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SpeedItem("\u2191", Color(0xFFF59E0B), upSpeed, upTotal)
            SpeedItem("\u2193", Color(0xFF22C55E), downSpeed, downTotal)
        }
    }
}

@Composable
private fun SpeedItem(arrow: String, color: Color, speed: Long, total: Long) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(arrow, color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(4.dp))
            Text(
                fmtBytes(speed, true),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            fmtBytes(total, false),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
    }
}
