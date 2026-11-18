package com.v2ray.ang.ui.main

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
    val active = expiry > now

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
                            if (active) "${daysLeft.toString().toFa()} روز اعتبار باقی مانده" else "بدون اعتبار",
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
private fun StatusText(text: String, color: Color, bold: Boolean = false, size: Int = 17) {
    Text(
        text,
        color = color,
        fontSize = size.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        textAlign = TextAlign.Center
    )
}
