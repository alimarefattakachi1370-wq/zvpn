package com.v2ray.ang.ui.main

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.draw.shadow
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
    var name by remember { mutableStateOf("") }
    var needName by remember { mutableStateOf(false) }
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
                needName = ZvpnApi.needName
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
                withContext(Dispatchers.IO) { ZvpnApi.verifyOtp(ctx, phoneDigits, latinDigits(code).filter { it.isDigit() }, name) }
                onLoggedIn()
            } catch (e: Exception) {
                error = errorText(e)
            }
            busy = false
        }
    }

    var smsGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED)
    }
    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { smsGranted = it }
    LaunchedEffect(codeSent) {
        if (codeSent && !smsGranted) smsLauncher.launch(Manifest.permission.RECEIVE_SMS)
    }
    // read the login code from the incoming SMS
    DisposableEffect(codeSent, smsGranted) {
        if (!codeSent || !smsGranted) return@DisposableEffect onDispose { }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val msgs = Telephony.Sms.Intents.getMessagesFromIntent(i) ?: return
                val body = latinDigits(msgs.joinToString("") { it.messageBody ?: "" })
                if (!body.contains("زدات")) return
                val m = Regex("(\\d{5})").find(body) ?: return
                code = m.groupValues[1]
            }
        }
        ContextCompat.registerReceiver(ctx, receiver, IntentFilter("android.provider.Telephony.SMS_RECEIVED"), ContextCompat.RECEIVER_EXPORTED)
        onDispose { runCatching { ctx.unregisterReceiver(receiver) } }
    }
    // returning users: submit automatically once the 5 digits are in
    LaunchedEffect(code) {
        if (codeSent && !needName && !busy && latinDigits(code).filter { it.isDigit() }.length == 5) verify()
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalContentColor provides MaterialTheme.colorScheme.onBackground
    ) {
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

            val fieldColors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.White.copy(alpha = 0.18f),
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            )
            val fieldShape = RoundedCornerShape(14.dp)
            val fieldMod = Modifier.fillMaxWidth().shadow(10.dp, fieldShape, ambientColor = Color.Black, spotColor = Color.Black)

            if (!codeSent) {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it.take(14) },
                    label = { Text("شماره موبایل (مثلاً ۰۹۱۲۳۴۵۶۷۸۹)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = fieldShape, colors = fieldColors, modifier = fieldMod
                )
            } else {
                if (needName) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(60) },
                        label = { Text("نام و نام خانوادگی") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        shape = fieldShape, colors = fieldColors, modifier = fieldMod
                    )
                    Spacer(Modifier.height(14.dp))
                }
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.take(5) },
                    label = { Text("کد تایید") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = fieldShape, colors = fieldColors, modifier = fieldMod
                )
            }

            if (error != null) {
                Spacer(Modifier.height(10.dp))
                Text(error ?: "", color = MaterialTheme.colorScheme.error, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(16.dp))

            Button(
                enabled = !busy && (if (!codeSent) phoneOk else latinDigits(code).filter { it.isDigit() }.length == 5 && (!needName || name.isNotBlank())),
                onClick = { if (!codeSent) sendCode() else verify() },
                modifier = Modifier.fillMaxWidth().height(52.dp)
                    .shadow(14.dp, RoundedCornerShape(14.dp), ambientColor = Color(0xFF22C55E), spotColor = Color(0xFF22C55E)),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A), contentColor = Color.White)
            ) {
                if (busy) CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text(if (!codeSent) "دریافت کد" else "ورود")
            }

            if (codeSent) {
                Spacer(Modifier.height(8.dp))
                TextButton(enabled = !busy && resendIn <= 0, onClick = { sendCode() }) {
                    Text(if (resendIn > 0) "ارسال مجدد کد (${resendIn.toString().toFa()} ثانیه)" else "ارسال مجدد کد")
                }
                TextButton(enabled = !busy, onClick = { codeSent = false; code = ""; name = ""; error = null }) { Text("ویرایش شماره") }
            }
        }
    }
}
