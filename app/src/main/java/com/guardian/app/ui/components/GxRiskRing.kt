package com.guardian.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guardian.app.ui.theme.GxBorder
import com.guardian.app.ui.theme.GxDanger
import com.guardian.app.ui.theme.GxSafe
import com.guardian.app.ui.theme.GxTextHi
import com.guardian.app.ui.theme.GxTextLo
import com.guardian.app.ui.theme.GxWarning

@Composable
fun GxRiskRing(
    riskScore: Int,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    strokeWidth: Dp = 8.dp,
    showLabel: Boolean = true
) {
    val animatedScore by animateFloatAsState(
        targetValue = riskScore.coerceIn(0, 100).toFloat(),
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "risk-score-anim"
    )

    val targetColor = when {
        riskScore >= 70 -> GxDanger
        riskScore >= 40 -> GxWarning
        else -> GxSafe
    }

    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 400),
        label = "risk-color-anim"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val strokePx = strokeWidth.toPx()
            // Track
            drawArc(
                color = GxBorder,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            // Progress
            val sweep = (animatedScore / 100f) * 360f
            if (sweep > 0f) {
                drawArc(
                    color = animatedColor,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
        }

        val qualitativeBand = when {
            riskScore >= 70 -> "HIGH"
            riskScore >= 40 -> "REVIEW"
            else -> "LOW"
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = qualitativeBand,
                color = animatedColor,
                fontSize = if (size >= 120.dp) 24.sp else 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif
            )
            Text(
                text = "${animatedScore.toInt()}/100",
                color = GxTextLo,
                fontSize = if (size >= 120.dp) 12.sp else 10.sp,
                fontFamily = FontFamily.Monospace
            )
            if (showLabel && size >= 120.dp) {
                Text(
                    text = when {
                        riskScore >= 70 -> "THREAT DETECTED"
                        riskScore >= 40 -> "NEEDS CAUTION"
                        else -> "SAFE / NORMAL"
                    },
                    color = animatedColor.copy(alpha = 0.85f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}
