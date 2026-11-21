package com.v2ray.ang.zvpn

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 3D card: light from the top, glossy border, deep shadow. */
@Composable
fun Card3D(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    colors: CardColors = CardDefaults.cardColors(),
    content: @Composable ColumnScope.() -> Unit
) {
    val base = colors.containerColor
    val top = lerp(base, Color.White, 0.14f)
    val bottom = lerp(base, Color.Black, 0.18f)
    Column(
        modifier = modifier
            .shadow(14.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(top, base, bottom)))
            .border(
                1.dp,
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.40f), Color.White.copy(alpha = 0.04f), Color.Black.copy(alpha = 0.35f))),
                shape
            ),
        content = content
    )
}

/** Raised 3D surface: top light, glossy bevel border, deep shadow. */
fun Modifier.raised3D(shape: Shape, base: Color, elevation: Dp = 12.dp): Modifier = this
    .shadow(elevation, shape, ambientColor = Color.Black, spotColor = Color.Black)
    .clip(shape)
    .background(Brush.verticalGradient(listOf(lerp(base, Color.White, 0.16f), base, lerp(base, Color.Black, 0.22f))))
    .border(
        1.dp,
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.40f), Color.White.copy(alpha = 0.04f), Color.Black.copy(alpha = 0.35f))),
        shape
    )
