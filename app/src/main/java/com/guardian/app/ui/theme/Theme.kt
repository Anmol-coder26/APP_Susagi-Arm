package com.guardian.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val LocalGxColors = staticCompositionLocalOf { GxDarkColorScheme }
val LocalGxTypography = staticCompositionLocalOf { GxType }

object GxDarkColorScheme {
    val void = GxVoid
    val base = GxBase
    val surface = GxSurface
    val surfaceAlt = GxSurfaceAlt
    val border = GxBorder
    val primary = GxPrimary
    val primarySoft = GxPrimarySoft
    val primaryGlow = GxPrimaryGlow
    val safe = GxSafe
    val safeSoft = GxSafeSoft
    val warning = GxWarning
    val warningSoft = GxWarningSoft
    val danger = GxDanger
    val dangerSoft = GxDangerSoft
    val textHi = GxTextHi
    val textMid = GxTextMid
    val textLo = GxTextLo
}

object GxTheme {
    val colors: GxDarkColorScheme
        @Composable
        @ReadOnlyComposable
        get() = LocalGxColors.current

    val type: GxTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalGxTypography.current
}

private val LightColorScheme = lightColorScheme(
    primary = GxPrimary,
    onPrimary = Color.White,
    primaryContainer = GxPrimarySoft,
    onPrimaryContainer = GxTextHi,
    secondary = GxSafe,
    onSecondary = Color.White,
    secondaryContainer = GxSafeSoft,
    onSecondaryContainer = GxSafe,
    background = GxBase,
    onBackground = GxTextHi,
    surface = GxSurface,
    onSurface = GxTextHi,
    surfaceVariant = GxSurfaceAlt,
    onSurfaceVariant = GxTextMid,
    outline = GxBorder,
    error = GxDanger,
    onError = Color.White
)

@Composable
fun GuardianTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = GxBase.toArgb()
                window.navigationBarColor = GxSurface.toArgb()
                val insets = WindowCompat.getInsetsController(window, view)
                insets.isAppearanceLightStatusBars = true
                insets.isAppearanceLightNavigationBars = true
            }
        }
    }

    CompositionLocalProvider(
        LocalGxColors provides GxDarkColorScheme,
        LocalGxTypography provides GxType
    ) {
        MaterialTheme(
            colorScheme = LightColorScheme,
            content = content
        )
    }
}
