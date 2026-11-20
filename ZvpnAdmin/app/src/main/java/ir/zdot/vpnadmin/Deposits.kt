package ir.zdot.vpnadmin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DepositsTab(api: AdminApi, refreshKey: Int, onAuth: () -> Unit) {
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<List<PendingPayment>>(emptyList()) }
    var deposits by remember { mutableStateOf<List<DepositItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableStateOf(0) }
    var confirmTarget by remember { mutableStateOf<PendingPayment?>(null) }
    var assignTarget by remember { mutableStateOf<DepositItem?>(null) }
    var phoneText by remember { mutableStateOf("") }
    var dlgError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(tick, refreshKey) {
        loading = true
        error = null
        try {
            pending = withContext(Dispatchers.IO) { api.pending() }
            deposits = withContext(Dispatchers.IO) { api.deposits() }
        } catch (e: Exception) {
            if (e.isLogin()) onAuth() else error = e.userMessage()
        }
        loading = false
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                SectionTitle("پرداخت‌های در انتظار تایید")
                TextButton(onClick = { tick++ }) { Text("بروزرسانی") }
            }
            ErrorText(error)
            if (loading) CircularProgressIndicator(modifier = Modifier.padding(4.dp))
            if (!loading && pending.isEmpty()) Text("پرداخت منتظری وجود ندارد.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(pending) { p ->
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.phone.toFa(), fontWeight = FontWeight.Bold)
                        Text("مبلغ دقیق: ${p.payAmount.fa()} ریال", fontSize = 13.sp)
                        Text("شارژ: ${p.toman.toman()}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (p.expiresMs > System.currentTimeMillis()) "تا ${jalali(p.expiresMs, true)}" else "مهلت تمام شده", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(onClick = { confirmTarget = p }) { Text("تایید دستی") }
                }
            }
        }
        item { SectionTitle("واریزهای اخیر (از پیامک بانک)") }
        items(deposits) { d ->
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${d.amount.fa()} ریال", fontWeight = FontWeight.Bold)
                        Text("${d.date.toFa()}  ${d.time.toFa()}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        when {
                            d.matched -> Text("تطبیق داده شد: ${(d.phone ?: "").toFa()}", fontSize = 12.sp, color = Color(0xFF16A34A))
                            d.assigned -> Text("شارژ دستی: ${(d.phone ?: "").toFa()}", fontSize = 12.sp, color = Color(0xFF16A34A))
                            else -> Text("بدون تطبیق", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    if (!d.matched && !d.assigned) {
                        OutlinedButton(onClick = { assignTarget = d; phoneText = ""; dlgError = null }) { Text("شارژ برای کاربر") }
                    }
                }
            }
        }
    }

    val c = confirmTarget
    if (c != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) confirmTarget = null },
            title = { Text("تایید دستی پرداخت") },
            text = { Text("کیف پول ${c.phone.toFa()} به‌اندازه ${c.toman.toman()} شارژ می‌شود. فقط اگر مطمئنید مبلغ به حساب شما واریز شده تایید کنید.") },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { api.confirmPayment(c.id) }
                            confirmTarget = null
                            tick++
                        } catch (e: Exception) {
                            if (e.isLogin()) onAuth() else error = e.userMessage()
                            confirmTarget = null
                        }
                        busy = false
                    }
                }) { Text("تایید و شارژ") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { confirmTarget = null }) { Text("انصراف") } }
        )
    }

    val a = assignTarget
    if (a != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) assignTarget = null },
            title = { Text("شارژ دستی از واریز") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("مبلغ ${a.toman.toman()} به کیف پول کاربر اضافه می‌شود. شماره موبایل کاربر را وارد کنید.", fontSize = 13.sp)
                    OutlinedTextField(
                        value = phoneText, onValueChange = { phoneText = it.take(14) }, label = { Text("شماره موبایل") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                    ErrorText(dlgError)
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    val phone = normPhone(phoneText)
                    busy = true
                    dlgError = null
                    scope.launch {
                        try {
                            val found = withContext(Dispatchers.IO) { api.users(phone) }.firstOrNull { it.phone == phone }
                            if (found == null) {
                                dlgError = "کاربری با این شماره پیدا نشد"
                            } else {
                                withContext(Dispatchers.IO) { api.assignDeposit(a.id, found.id) }
                                assignTarget = null
                                tick++
                            }
                        } catch (e: Exception) {
                            if (e.isLogin()) onAuth() else dlgError = e.userMessage()
                        }
                        busy = false
                    }
                }) { Text("شارژ کن") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { assignTarget = null }) { Text("انصراف") } }
        )
    }
}
