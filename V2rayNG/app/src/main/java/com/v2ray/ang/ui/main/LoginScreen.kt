package com.v2ray.ang.ui.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.ui.wallet.toFa
import com.v2ray.ang.zvpn.ZvpnApi
import com.v2ray.ang.zvpn.zvpnBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun latinDigits(s: String): String = s.map {
    when (it) {
        in '۰'..'۹' -> '0' + (it - '۰')
        in '٠'..'٩' -> '0' + (it - '٠')
        else -> it
    }
}.joinToString("")

private fun errorText(e: Exception): String =
    if (e is ZvpnApi.ApiException) (e.message ?: "خطای سرور") else "اتصال به سرور برقرار نشد"

@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var codeSent by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var resendIn by remember { mutableStateOf(0) }

    LaunchedEffect(codeSent, resendIn) {
        if (codeSent && resendIn > 0) {
            delay(1000)
            resendIn -= 1
        }
    }

    val phoneDigits = latinDigits(phone).filter { it.isDigit() }
    val phoneOk = Regex("^0?9\\d{9}$").matches(phoneDigits)

    fun sendCode() {
        busy = true
        error = null
        scope.launch {
            try {
                val wait = withContext(Dispatchers.IO) { ZvpnApi.requestOtp(phoneDigits) }
                codeSent = true
                code = ""
                resendIn = wait
            } catch (e: Exception) {
                error = errorText(e)
            }
            busy = false
        }
    }

    fun verify() {
        busy = true
        error = null
        scope.launch {
            try {
                withContext(Dispatchers.IO) { ZvpnApi.verifyOtp(ctx, phoneDigits, latinDigits(code).filter { it.isDigit() }) }
                onLoggedIn()
            } catch (e: Exception) {
                error = errorText(e)
            }
            busy = false
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(zvpnBackground())
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(R.drawable.ic_logo),
                contentDescription = null,
                modifier = Modifier.size(96.dp).clip(RoundedCornerShape(24.dp))
            )
            Spacer(Modifier.height(20.dp))
            Text("ورود به ZVPN", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                if (!codeSent) "شماره موبایل خود را وارد کنید تا کد تایید برایتان پیامک شود."
                else "کد ۵ رقمی ارسال‌شده به ${phoneDigits.toFa()} را وارد کنید.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))

            if (!codeSent) {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it.take(14) },
                    label = { Text("شماره موبایل (مثلاً ۰۹۱۲۳۴۵۶۷۸۹)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.take(5) },
                    label = { Text("کد تایید") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (error != null) {
                Spacer(Modifier.height(10.dp))
                Text(error ?: "", color = MaterialTheme.colorScheme.error, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(16.dp))

            Button(
                enabled = !busy && (if (!codeSent) phoneOk else latinDigits(code).filter { it.isDigit() }.length == 5),
                onClick = { if (!codeSent) sendCode() else verify() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (busy) CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text(if (!codeSent) "دریافت کد" else "ورود")
            }

            if (codeSent) {
                Spacer(Modifier.height(8.dp))
                TextButton(enabled = !busy && resendIn <= 0, onClick = { sendCode() }) {
                    Text(if (resendIn > 0) "ارسال مجدد کد (${resendIn.toString().toFa()} ثانیه)" else "ارسال مجدد کد")
                }
                TextButton(enabled = !busy, onClick = { codeSent = false; code = ""; error = null }) { Text("ویرایش شماره") }
            }
        }
    }
}
