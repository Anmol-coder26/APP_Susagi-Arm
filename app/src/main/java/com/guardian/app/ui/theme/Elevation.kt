package com.guardian.app.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.gxFancyGlow(
    shape: Shape = GxShapeLg,
    elevation: Dp = 8.dp
): Modifier = this.shadow(
    elevation = elevation,
    shape = shape,
    ambientColor = Color(0x0A000000),
    spotColor = Color(0x10000000)
)

fun Modifier.gxDangerGlow(
    shape: Shape = GxShapeLg,
    elevation: Dp = 8.dp
): Modifier = this.shadow(
    elevation = elevation,
    shape = shape,
    ambientColor = Color(0x1CDC2626),
    spotColor = Color(0x24DC2626)
)
