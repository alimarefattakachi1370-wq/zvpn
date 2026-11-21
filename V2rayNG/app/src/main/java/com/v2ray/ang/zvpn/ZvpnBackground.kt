package com.v2ray.ang.zvpn

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Green gradient background used by every ZVPN screen. */
@Composable
fun zvpnBackground(): Brush {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (dark) Brush.verticalGradient(listOf(Color(0xFF12402B), Color(0xFF0B1A13), Color(0xFF050A08)))
    else Brush.verticalGradient(listOf(Color(0xFFEAF8F0), Color(0xFFCFEBDA), Color(0xFFB5DDC6)))
}
