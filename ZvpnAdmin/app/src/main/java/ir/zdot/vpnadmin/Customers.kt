package ir.zdot.vpnadmin

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CustomersTab(api: AdminApi, refreshKey: Int, onAuth: () -> Unit, onOpen: (Int) -> Unit) {
    var query by remember { mutableStateOf("") }
    var users by remember { mutableStateOf<List<UserItem>>(emptyList()) }
    var stats by remember { mutableStateOf<Stats?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableStateOf(0) }

    LaunchedEffect(query, tick, refreshKey) {
        if (query.isNotEmpty()) delay(350)
        loading = true
        error = null
        try {
            users = withContext(Dispatchers.IO) { api.users(query) }
            if (query.isEmpty()) stats = withContext(Dispatchers.IO) { api.stats() }
        } catch (e: Exception) {
            if (e.isLogin()) onAuth() else error = e.userMessage()
        }
        loading = false
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val s = stats
        if (s != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                StatCard("مشتریان", s.users.toLong().fa(), Modifier.weight(1f))
                StatCard("اشتراک فعال", s.activeSubs.toLong().fa(), Modifier.weight(1f))
                StatCard("غیرفعال", s.disabled.toLong().fa(), Modifier.weight(1f))
            }
            Text("مجموع موجودی کیف پول مشتریان: ${s.walletTotal.toman()}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(15) },
            label = { Text("جستجو با شماره موبایل") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth()
        )
        ErrorText(error)
        if (loading) CircularProgressIndicator(modifier = Modifier.padding(4.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            items(users) { u ->
                Card3D(
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(u.id) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            if (u.name.isNotBlank()) Text(u.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(u.phone.toFa(), fontWeight = if (u.name.isBlank()) FontWeight.Bold else FontWeight.Normal, fontSize = if (u.name.isBlank()) 16.sp else 13.sp)
                            Text("کیف پول: ${u.wallet.toman()}", fontSize = 13.sp)
                            Text(
                                if (u.active) "اشتراک تا ${jalali(u.subUntilMs)}" else "بدون اشتراک فعال",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (u.status == "disabled") {
                            Text("غیرفعال", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier) {
    Card3D(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

private enum class Dlg { None, Charge, Days, Status }

@Composable
fun UserDetail(api: AdminApi, userId: Int, onBack: () -> Unit, onAuth: () -> Unit) {
    val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf<UserItem?>(null) }
    var history by remember { mutableStateOf<List<Tx>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var tick by remember { mutableStateOf(0) }
    var dlg by remember { mutableStateOf(Dlg.None) }
    var amountText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }
    var daysText by remember { mutableStateOf("") }
    var reasonText by remember { mutableStateOf("") }
    var dlgError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(tick) {
        loading = true
        error = null
        try {
            val r = withContext(Dispatchers.IO) { api.userDetail(userId) }
            user = r.first
            history = r.second
        } catch (e: Exception) {
            if (e.isLogin()) onAuth() else error = e.userMessage()
        }
        loading = false
    }

    fun perform(block: () -> Unit) {
        busy = true
        dlgError = null
        scope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
                dlg = Dlg.None
                amountText = ""; noteText = ""; daysText = ""; reasonText = ""
                tick++
            } catch (e: Exception) {
                if (e.isLogin()) onAuth() else dlgError = e.userMessage()
            }
            busy = false
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ بازگشت") }
        }
        if (loading && user == null) CircularProgressIndicator()
        ErrorText(error)
        val u = user
        if (u != null) {
            Card3D(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (u.name.isNotBlank()) Text(u.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(u.phone.toFa(), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Labeled("موجودی کیف پول", u.wallet.toman())
                    Labeled("اشتراک", if (u.active) "فعال تا ${jalali(u.subUntilMs)}" else "ندارد")
                    Labeled("وضعیت حساب", if (u.status == "disabled") "غیرفعال" else "فعال")
                    if (u.status == "disabled" && u.reason.isNotEmpty()) Labeled("علت غیرفعال شدن", u.reason)
                }
            }
            Button(onClick = { dlg = Dlg.Charge; dlgError = null }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp)) {
                Text("شارژ / کسر کیف پول")
            }
            OutlinedButton(onClick = { dlg = Dlg.Days; dlgError = null }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp)) {
                Text("افزودن / کاهش روز اشتراک")
            }
            if (u.status == "disabled") {
                OutlinedButton(onClick = { dlg = Dlg.Status; dlgError = null }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp)) {
                    Text("فعال کردن حساب")
                }
            } else {
                Button(
                    onClick = { dlg = Dlg.Status; dlgError = null },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("غیرفعال کردن حساب", color = Color.White) }
            }

            SectionTitle("تراکنش‌های اخیر")
            if (history.isEmpty()) Text("تراکنشی ثبت نشده است.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            history.forEach { t ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.title, fontSize = 14.sp)
                        Text(jalali(t.timeMs, true), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (t.amount != 0L) {
                        Text(
                            (if (t.amount > 0) "+" else "-") + kotlin.math.abs(t.amount).toman(),
                            color = if (t.amount > 0) Color(0xFF16A34A) else MaterialTheme.colorScheme.error,
                            fontSize = 13.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    val u = user
    if (u != null && dlg == Dlg.Charge) {
        AlertDialog(
            onDismissRequest = { if (!busy) dlg = Dlg.None },
            title = { Text("شارژ / کسر کیف پول") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = amountText, onValueChange = { amountText = it.take(10) }, label = { Text("مبلغ (تومان)") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(value = noteText, onValueChange = { noteText = it.take(100) }, label = { Text("توضیح (اختیاری)") }, singleLine = true)
                    ErrorText(dlgError)
                }
            },
            confirmButton = {
                Row {
                    TextButton(enabled = !busy, onClick = {
                        val a = latinDigits(amountText).filter { it.isDigit() }.toLongOrNull() ?: 0L
                        if (a <= 0) dlgError = "مبلغ را وارد کنید" else perform { api.charge(u.id, a, noteText) }
                    }) { Text("افزایش") }
                    TextButton(enabled = !busy, onClick = {
                        val a = latinDigits(amountText).filter { it.isDigit() }.toLongOrNull() ?: 0L
                        if (a <= 0) dlgError = "مبلغ را وارد کنید" else perform { api.charge(u.id, -a, noteText) }
                    }) { Text("کسر", color = MaterialTheme.colorScheme.error) }
                }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { dlg = Dlg.None }) { Text("انصراف") } }
        )
    }
    if (u != null && dlg == Dlg.Days) {
        AlertDialog(
            onDismissRequest = { if (!busy) dlg = Dlg.None },
            title = { Text("روز اشتراک") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = daysText, onValueChange = { daysText = it.take(4) }, label = { Text("تعداد روز") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    ErrorText(dlgError)
                }
            },
            confirmButton = {
                Row {
                    TextButton(enabled = !busy, onClick = {
                        val d = latinDigits(daysText).filter { it.isDigit() }.toIntOrNull() ?: 0
                        if (d <= 0) dlgError = "تعداد روز را وارد کنید" else perform { api.addDays(u.id, d) }
                    }) { Text("افزودن") }
                    TextButton(enabled = !busy, onClick = {
                        val d = latinDigits(daysText).filter { it.isDigit() }.toIntOrNull() ?: 0
                        if (d <= 0) dlgError = "تعداد روز را وارد کنید" else perform { api.addDays(u.id, -d) }
                    }) { Text("کاهش", color = MaterialTheme.colorScheme.error) }
                }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { dlg = Dlg.None }) { Text("انصراف") } }
        )
    }
    if (u != null && dlg == Dlg.Status) {
        val disabling = u.status != "disabled"
        AlertDialog(
            onDismissRequest = { if (!busy) dlg = Dlg.None },
            title = { Text(if (disabling) "غیرفعال کردن حساب" else "فعال کردن حساب") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (disabling) {
                        Text("علت غیرفعال شدن را بنویسید؛ کاربر آن را در اپ می‌بیند.", fontSize = 13.sp)
                        OutlinedTextField(
                            value = reasonText, onValueChange = { reasonText = it.take(250) },
                            label = { Text("علت") }, minLines = 2, modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text("حساب ${u.phone.toFa()} دوباره فعال می‌شود.", fontSize = 14.sp)
                    }
                    ErrorText(dlgError)
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    if (disabling && reasonText.isBlank()) dlgError = "علت را بنویسید"
                    else perform { api.setStatus(u.id, if (disabling) "disabled" else "active", reasonText) }
                }) { Text(if (disabling) "غیرفعال کن" else "فعال کن") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { dlg = Dlg.None }) { Text("انصراف") } }
        )
    }
}
