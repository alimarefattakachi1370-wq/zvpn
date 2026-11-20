package ir.zdot.vpnadmin

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import java.util.Locale

@Composable
fun AdminTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) {
        darkColorScheme(
            primary = Color(0xFF4ADE80), onPrimary = Color(0xFF052E16),
            primaryContainer = Color(0xFF14532D), onPrimaryContainer = Color(0xFFBBF7D0),
            background = Color(0xFF111412), surface = Color(0xFF111412),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF16A34A), onPrimary = Color.White,
            primaryContainer = Color(0xFFBBF7D0), onPrimaryContainer = Color(0xFF052E16),
            background = Color(0xFFF7FBF8), surface = Color(0xFFF7FBF8),
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

fun String.toFa(): String = map { if (it in '0'..'9') '۰' + (it - '0') else it }.joinToString("")

fun latinDigits(s: String): String = s.map {
    when (it) {
        in '۰'..'۹' -> '0' + (it - '۰')
        in '٠'..'٩' -> '0' + (it - '٠')
        else -> it
    }
}.joinToString("")

fun Long.fa(): String = String.format(Locale.US, "%,d", this).replace(',', '٬').toFa()

fun Long.toman(): String = this.fa() + " تومان"

fun normPhone(input: String): String {
    val d = latinDigits(input).filter { it.isDigit() }
    return when {
        d.startsWith("98") && d.length == 12 -> "0" + d.substring(2)
        d.startsWith("9") && d.length == 10 -> "0$d"
        else -> d
    }
}

fun jalali(millis: Long, withTime: Boolean = false): String {
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    val gy = cal.get(Calendar.YEAR)
    val gm = cal.get(Calendar.MONTH) + 1
    val gd = cal.get(Calendar.DAY_OF_MONTH)
    val gdm = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
    val gy2 = if (gm > 2) gy + 1 else gy
    var days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) + ((gy2 + 399) / 400) + gd + gdm[gm - 1]
    var jy = -1595 + (33 * (days / 12053))
    days %= 12053
    jy += 4 * (days / 1461)
    days %= 1461
    if (days > 365) {
        jy += (days - 1) / 365
        days = (days - 1) % 365
    }
    val jm = if (days < 186) 1 + days / 31 else 7 + (days - 186) / 30
    val jd = 1 + if (days < 186) days % 31 else (days - 186) % 30
    val date = String.format(Locale.US, "%d/%02d/%02d", jy, jm, jd)
    val out = if (withTime) date + String.format(Locale.US, " %02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)) else date
    return out.toFa()
}

fun Exception.isLogin(): Boolean = this is Net.ApiException && this.code == "login"

fun Exception.userMessage(): String = if (this is Net.ApiException) (this.message ?: "خطا") else "اتصال به سرور برقرار نشد"

@Composable
fun SectionTitle(text: String) {
    Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
}

@Composable
fun ErrorText(text: String?) {
    if (text != null) Text(text, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
}

@Composable
fun Labeled(label: String, value: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 15.sp)
    }
}
