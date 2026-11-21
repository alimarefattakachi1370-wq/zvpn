package com.v2ray.ang.ui.wallet

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.zvpn.ZvpnApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WalletActivity : BaseComponentActivity() {
    @Composable
    override fun ScreenContent() {
        WalletScreen(onBack = { finish() })
    }
}

private const val MIN_TOPUP = 10_000L

private fun errorText(e: Exception): String =
    if (e is ZvpnApi.ApiException) (e.message ?: "خطای سرور") else "اتصال به سرور برقرار نشد"

private fun copyText(ctx: Context, label: String, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(ctx, "کپی شد", Toast.LENGTH_SHORT).show()
}

@Composable
fun WalletScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var version by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var payment by remember { mutableStateOf<ZvpnApi.Payment?>(null) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var selected by remember { mutableStateOf<Long?>(50_000L) }
    var custom by remember { mutableStateOf("") }

    val balance = remember(version) { WalletStore.balance(ctx) }
    val expiry = remember(version) { WalletStore.expiry(ctx) }
    val plans = remember(version) { WalletStore.plans(ctx) }
    val history = remember(version) { WalletStore.history(ctx) }
    val disabled = remember(version) { WalletStore.isDisabled(ctx) }
    val disabledReason = remember(version) { WalletStore.disabledReason(ctx) }
    val phone = remember(version) { WalletStore.phone(ctx) }
    val active = !disabled && expiry > now
    val daysLeft = remember(version, now) { WalletStore.daysLeft(ctx) }

    fun load() {
        loading = true
        loadError = null
        scope.launch {
            try {
                val pending = withContext(Dispatchers.IO) { ZvpnApi.refreshState(ctx) }
                payment = pending
            } catch (e: Exception) {
                if (e is ZvpnApi.ApiException && e.code == "login") {
                    Toast.makeText(ctx, e.message ?: "دوباره وارد شوید", Toast.LENGTH_LONG).show()
                    onBack()
                    return@launch
                }
                loadError = errorText(e)
            }
            version++
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    // clock tick (countdown + subscription state)
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }

    // poll the open payment until it is paid or expired
    val payId = payment?.id
    LaunchedEffect(payId) {
        if (payId == null) return@LaunchedEffect
        while (true) {
            delay(4000)
            try {
                val p = withContext(Dispatchers.IO) { ZvpnApi.paymentStatus(ctx, payId) }
                version++
                if (p.status == "paid") {
                    Toast.makeText(ctx, "پرداخت تایید شد و کیف پول شارژ شد", Toast.LENGTH_LONG).show()
                    payment = null
                    return@LaunchedEffect
                }
                if (p.status == "expired") {
                    Toast.makeText(ctx, "مهلت پرداخت تمام شد", Toast.LENGTH_LONG).show()
                    payment = null
                    return@LaunchedEffect
                }
            } catch (e: Exception) {
                // temporary network problem: try again
            }
        }
    }

    val amount: Long = custom.filter { it.isDigit() }.toLongOrNull() ?: selected ?: 0L

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { AppTopBar(title = "کیف پول", onBackClick = onBack) }
    ) { inner ->
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Balance card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF16A34A), Color(0xFF047857))))
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("موجودی کیف پول", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
                        Text(balance.toToman(), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (active) "اشتراک فعال · ${daysLeft.toString().toFa()} روز مانده (تا ${jalaliDate(expiry)})"
                            else "اشتراک فعال ندارید",
                            color = Color.White,
                            fontSize = 13.sp
                        )
                    }
                }

                if (disabled) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("حساب شما غیرفعال شده است", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                            if (disabledReason.isNotEmpty()) {
                                Text("علت: $disabledReason", fontSize = 13.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                            Text("برای پیگیری با پشتیبانی تماس بگیرید.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }

                if (loading) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                }
                if (loadError != null) {
                    Text(loadError ?: "", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    OutlinedButton(onClick = { load() }, modifier = Modifier.fillMaxWidth()) { Text("تلاش دوباره") }
                }

                val pay = payment
                if (pay != null) {
                    PaymentCard(
                        pay = pay,
                        remainingSec = ((pay.expiresMs - now) / 1000).coerceAtLeast(0),
                        onCopy = { label, text -> copyText(ctx, label, text) },
                        onClose = { payment = null }
                    )
                } else {
                    // Top up
                    Text("شارژ کیف پول", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    val quick = listOf(50_000L, 100_000L, 200_000L, 500_000L)
                    quick.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            pair.forEach { v ->
                                AmountChip(
                                    text = v.toToman(),
                                    selected = custom.isEmpty() && selected == v,
                                    onClick = { selected = v; custom = "" },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = custom,
                        onValueChange = {
                            custom = it.filter { ch -> ch.isDigit() }.take(9)
                            if (custom.isNotEmpty()) selected = null
                        },
                        label = { Text("مبلغ دلخواه (تومان)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        enabled = !busy,
                        onClick = {
                            if (amount < MIN_TOPUP) {
                                Toast.makeText(ctx, "حداقل مبلغ شارژ ${MIN_TOPUP.toToman()} است", Toast.LENGTH_SHORT).show()
                            } else {
                                busy = true
                                scope.launch {
                                    try {
                                        payment = withContext(Dispatchers.IO) { ZvpnApi.topUp(ctx, amount) }
                                    } catch (e: Exception) {
                                        Toast.makeText(ctx, errorText(e), Toast.LENGTH_LONG).show()
                                    }
                                    busy = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(if (amount > 0) "پرداخت کارت‌به‌کارت ${amount.toToman()}" else "پرداخت کارت‌به‌کارت")
                    }
                }

                // Plans
                Spacer(Modifier.height(4.dp))
                Text("خرید اشتراک", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                plans.forEach { plan ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(plan.title, fontWeight = FontWeight.Bold)
                                Text(plan.price.toToman(), color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                            }
                            Button(
                                enabled = !busy,
                                onClick = {
                                    if (balance < plan.price) {
                                        Toast.makeText(ctx, "موجودی کافی نیست، ابتدا کیف پول را شارژ کنید", Toast.LENGTH_LONG).show()
                                    } else {
                                        busy = true
                                        scope.launch {
                                            try {
                                                withContext(Dispatchers.IO) { ZvpnApi.buyPlan(ctx, plan.code) }
                                                version++
                                                Toast.makeText(ctx, "اشتراک فعال شد", Toast.LENGTH_SHORT).show()
                                            } catch (e: Exception) {
                                                Toast.makeText(ctx, errorText(e), Toast.LENGTH_LONG).show()
                                            }
                                            busy = false
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) { Text("خرید") }
                        }
                    }
                }

                // History
                if (history.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("تراکنش‌های اخیر", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    history.forEach { tx ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(tx.title)
                                Text(jalaliDate(tx.time, true), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                (if (tx.amount > 0) "+" else "-") + kotlin.math.abs(tx.amount).toToman(),
                                color = if (tx.amount > 0) Color(0xFF16A34A) else MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        if (phone.isNotEmpty()) "حساب: ${phone.toFa()}" else "",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { ZvpnApi.logout(ctx) }
                            onBack()
                        }
                    }) { Text("خروج از حساب", color = MaterialTheme.colorScheme.error) }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun PaymentCard(
    pay: ZvpnApi.Payment,
    remainingSec: Long,
    onCopy: (String, String) -> Unit,
    onClose: () -> Unit,
) {
    val mm = (remainingSec / 60).toString().padStart(2, '0')
    val ss = (remainingSec % 60).toString().padStart(2, '0')
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("پرداخت کارت‌به‌کارت", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(
                "دقیقاً مبلغ زیر را واریز کنید (نه کمتر و نه بیشتر). بعد از واریز، شارژ خودکار انجام می‌شود.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "${pay.payAmount.toFaNumber()} ${pay.unit}",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            OutlinedButton(
                onClick = { onCopy("amount", pay.payAmount.toString()) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("کپی مبلغ") }

            val digits = pay.cardNumber.filter { it.isDigit() }
            Text("شماره کارت", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    digits.chunked(4).joinToString("  "),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
            OutlinedButton(
                onClick = { onCopy("card", digits) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("کپی شماره کارت") }
            Text(
                "به نام ${pay.cardOwner} · بانک ${pay.cardBank}",
                fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(10.dp))
                Text(
                    if (remainingSec > 0) "در انتظار پرداخت · مهلت: ${("$mm:$ss").toFa()}" else "در حال بررسی آخرین وضعیت...",
                    fontSize = 13.sp
                )
            }
            OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("بستن") }
        }
    }
}

@Composable
private fun AmountChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}
