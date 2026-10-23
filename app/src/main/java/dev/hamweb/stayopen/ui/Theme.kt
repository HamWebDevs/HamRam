package dev.hamweb.stayopen.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.hamweb.stayopen.domain.ThemeMode

private val DarkColors = darkColorScheme(
    primary = Color(0xFF89D7BE), onPrimary = Color(0xFF00382B), primaryContainer = Color(0xFF20483E),
    onPrimaryContainer = Color(0xFFC4F3E0), secondary = Color(0xFFB5C8F4),
    background = Color(0xFF0E1218), surface = Color(0xFF0E1218),
    surfaceContainer = Color(0xFF191F27), surfaceContainerHigh = Color(0xFF232A34),
    surfaceVariant = Color(0xFF29333C), onSurface = Color(0xFFE5EAF2), onSurfaceVariant = Color(0xFFADB8C7),
    outline = Color(0xFF64717F), outlineVariant = Color(0xFF303A46), error = Color(0xFFFFB4A9),
)
private val LightColors = lightColorScheme(
    primary = Color(0xFF006B52), onPrimary = Color.White, primaryContainer = Color(0xFFD2F4E7),
    onPrimaryContainer = Color(0xFF093D30), secondary = Color(0xFF465F98),
    background = Color(0xFFF4F7FA), surface = Color(0xFFF4F7FA),
    surfaceContainer = Color.White, surfaceContainerHigh = Color(0xFFE9EFF4),
    surfaceVariant = Color(0xFFE3EAEF), onSurface = Color(0xFF18212D), onSurfaceVariant = Color(0xFF4C5C6D),
    outline = Color(0xFF718192), outlineVariant = Color(0xFFD6DFE7), error = Color(0xFFB42318),
)

@Composable
fun StayOpenTheme(mode: ThemeMode, content: @Composable (Boolean) -> Unit) {
    val dark = when (mode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.LIGHT -> false; ThemeMode.DARK -> true }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors,
        typography = Typography(
            headlineLarge = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-1).sp),
            headlineMedium = androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 27.sp, lineHeight = 34.sp),
            titleLarge = androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 28.sp),
            titleMedium = androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
            bodyLarge = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
            labelSmall = androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp),
        )) { content(dark) }
}
