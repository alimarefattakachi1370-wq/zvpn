package com.v2ray.ang.ui.wallet

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar

class WalletActivity : BaseComponentActivity() {
    @Composable
    override fun ScreenContent() {
        WalletScreen(onBack = { finish() })
    }
}

private const val MIN_TOPUP = 10_000L

@Composable
fun WalletScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var balance by remember { mutableStateOf(WalletStore.balance(ctx)) }
    var expiry by remember { mutableStateOf(WalletStore.expiry(ctx)) }
    var history by remember { mutableStateOf(WalletStore.history(ctx)) }
    var selected by remember { mutableStateOf<Long?>(50_000L) }
    var custom by remember { mutableStateOf("") }
    var showPay by remember { mutableStateOf(false) }

    fun refresh() {
        balance = WalletStore.balance(ctx)
        expiry = WalletStore.expiry(ctx)
        history = WalletStore.history(ctx)
    }

    val amount: Long = custom.filter { it.isDigit() }.toLongOrNull() ?: selected ?: 0L
    val active = expiry > System.currentTimeMillis()
    val daysLeft = WalletStore.daysLeft(ctx)

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

                Text(
                    "نسخه نمایشی: پرداخت و اشتراک فعلاً شبیه‌سازی می‌شود و به سرور وصل نیست.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )

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
                    onValueChange = { custom = it.filter { ch -> ch.isDigit() }.take(9); if (custom.isNotEmpty()) selected = null },
                    label = { Text("مبلغ دلخواه (تومان)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        if (amount < MIN_TOPUP) {
                            Toast.makeText(ctx, "حداقل مبلغ شارژ ${MIN_TOPUP.toToman()} است", Toast.LENGTH_SHORT).show()
                        } else showPay = true
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(if (amount > 0) "پرداخت ${amount.toToman()}" else "پرداخت")
                }

                // Plans
                Spacer(Modifier.height(4.dp))
                Text("خرید اشتراک", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                WalletStore.plans.forEach { plan ->
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
                                onClick = {
                                    if (WalletStore.buyPlan(ctx, plan)) {
                                        refresh()
                                        Toast.makeText(ctx, "اشتراک فعال شد", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(ctx, "موجودی کافی نیست، ابتدا کیف پول را شارژ کنید", Toast.LENGTH_LONG).show()
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
                Spacer(Modifier.height(24.dp))
            }

            if (showPay) {
                AlertDialog(
                    onDismissRequest = { showPay = false },
                    title = { Text("پرداخت آزمایشی") },
                    text = { Text("مبلغ ${amount.toToman()} بدون اتصال به درگاه به کیف پول اضافه می‌شود.") },
                    confirmButton = {
                        TextButton(onClick = {
                            WalletStore.topUp(ctx, amount)
                            refresh()
                            showPay = false
                            Toast.makeText(ctx, "کیف پول شارژ شد", Toast.LENGTH_SHORT).show()
                        }) { Text("تایید") }
                    },
                    dismissButton = { TextButton(onClick = { showPay = false }) { Text("انصراف") } }
                )
            }
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
