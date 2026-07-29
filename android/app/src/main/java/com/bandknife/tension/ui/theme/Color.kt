package com.bandknife.tension.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1565C0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD4E3FF),
    onPrimaryContainer = Color(0xFF001C3B),
    inversePrimary = Color(0xFFA6C8FF),
    secondary = Color(0xFF2E7D32),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFB7F0B4),
    onSecondaryContainer = Color(0xFF002204),
    tertiary = Color(0xFF8A5300),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDDB6),
    onTertiaryContainer = Color(0xFF2C1600),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFFDFBFF),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFDFBFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFDFE2EB),
    onSurfaceVariant = Color(0xFF43474E),
    surfaceTint = Color(0xFF1565C0),
    inverseSurface = Color(0xFF2F3033),
    inverseOnSurface = Color(0xFFF1F0F4),
    outline = Color(0xFF73777F),
    outlineVariant = Color(0xFFC3C7CF),
    scrim = Color(0xFF000000)
)

val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFA6C8FF),
    onPrimary = Color(0xFF003060),
    primaryContainer = Color(0xFF004788),
    onPrimaryContainer = Color(0xFFD4E3FF),
    inversePrimary = Color(0xFF1565C0),
    secondary = Color(0xFF9BD49B),
    onSecondary = Color(0xFF003908),
    secondaryContainer = Color(0xFF10520F),
    onSecondaryContainer = Color(0xFFB7F0B4),
    tertiary = Color(0xFFFFB95C),
    onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF693C00),
    onTertiaryContainer = Color(0xFFFFDDB6),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF1A1C1E),
    onBackground = Color(0xFFE2E2E6),
    surface = Color(0xFF1A1C1E),
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF43474E),
    onSurfaceVariant = Color(0xFFC3C7CF),
    surfaceTint = Color(0xFFA6C8FF),
    inverseSurface = Color(0xFFE2E2E6),
    inverseOnSurface = Color(0xFF2F3033),
    outline = Color(0xFF8D9199),
    outlineVariant = Color(0xFF43474E),
    scrim = Color(0xFF000000)
)

/**
 * 合否・打撃品質を表す色。
 * text は本文コントラスト 4.5:1 以上を満たす濃色、fill は図形塗り用の明色として使い分ける。
 */
@Immutable
data class StatusColors(
    val okText: Color,
    val okFill: Color,
    val okContainer: Color,
    val onOkContainer: Color,
    val warnText: Color,
    val warnFill: Color,
    val warnContainer: Color,
    val onWarnContainer: Color,
    val ngText: Color,
    val ngFill: Color,
    val ngContainer: Color,
    val onNgContainer: Color,
    val idleText: Color,
    val idleFill: Color,
    val idleContainer: Color
)

val LightStatusColors = StatusColors(
    okText = Color(0xFF1B5E20),
    okFill = Color(0xFF2E7D32),
    okContainer = Color(0xFFD7F2D8),
    onOkContainer = Color(0xFF0A2E0C),
    warnText = Color(0xFF8A5300),
    warnFill = Color(0xFFF57C00),
    warnContainer = Color(0xFFFFE7B8),
    onWarnContainer = Color(0xFF2C1600),
    ngText = Color(0xFFB3261E),
    ngFill = Color(0xFFC62828),
    ngContainer = Color(0xFFFADAD7),
    onNgContainer = Color(0xFF410E0B),
    idleText = Color(0xFF43474E),
    idleFill = Color(0xFF757575),
    idleContainer = Color(0xFFE4E6EC)
)

val DarkStatusColors = StatusColors(
    okText = Color(0xFF9BD49B),
    okFill = Color(0xFF4CAF50),
    okContainer = Color(0xFF10520F),
    onOkContainer = Color(0xFFB7F0B4),
    warnText = Color(0xFFFFB95C),
    warnFill = Color(0xFFFFB300),
    warnContainer = Color(0xFF693C00),
    onWarnContainer = Color(0xFFFFDDB6),
    ngText = Color(0xFFF2B8B5),
    ngFill = Color(0xFFEF5350),
    ngContainer = Color(0xFF8C1D18),
    onNgContainer = Color(0xFFF9DEDC),
    idleText = Color(0xFFC3C7CF),
    idleFill = Color(0xFF9E9E9E),
    idleContainer = Color(0xFF43474E)
)

val LocalStatusColors = staticCompositionLocalOf { LightStatusColors }
