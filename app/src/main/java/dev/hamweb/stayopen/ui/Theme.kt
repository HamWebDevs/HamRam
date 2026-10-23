package dev.hamweb.stayopen.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.hamweb.stayopen.domain.ThemeMode
import dev.hamweb.stayopen.domain.Accent

private val DarkColors = darkColorScheme(
    primary = Color(0xFF78FF6E), onPrimary = Color(0xFF051305), primaryContainer = Color(0xFF15331B),
    onPrimaryContainer = Color(0xFFB9FFAF), secondary = Color(0xFF76DBFF),
    secondaryContainer = Color(0xFF15331B), onSecondaryContainer = Color(0xFFB9FFAF),
    background = Color(0xFF060B09), surface = Color(0xFF060B09),
    surfaceContainer = Color(0xFF0D1712), surfaceContainerHigh = Color(0xFF16231B),
    surfaceVariant = Color(0xFF20352A), onSurface = Color(0xFFE2F0E5), onSurfaceVariant = Color(0xFFA1B9AA),
    outline = Color(0xFF587665), outlineVariant = Color(0xFF294334), error = Color(0xFFFF8E86),
)
private val LightColors = lightColorScheme(
    primary = Color(0xFF12652B), onPrimary = Color.White, primaryContainer = Color(0xFFD5F5D9),
    onPrimaryContainer = Color(0xFF103A1C), secondary = Color(0xFF006479),
    secondaryContainer = Color(0xFFD5F5D9), onSecondaryContainer = Color(0xFF103A1C),
    background = Color(0xFFF1F7F2), surface = Color(0xFFF1F7F2),
    surfaceContainer = Color.White, surfaceContainerHigh = Color(0xFFE9EFF4),
    surfaceVariant = Color(0xFFE3EAEF), onSurface = Color(0xFF18212D), onSurfaceVariant = Color(0xFF4C5C6D),
    outline = Color(0xFF718192), outlineVariant = Color(0xFFD6DFE7), error = Color(0xFFB42318),
)

@Composable
fun StayOpenTheme(mode: ThemeMode, accent: Accent = Accent.GREEN, content: @Composable (Boolean) -> Unit) {
    val dark = when (mode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.LIGHT -> false; ThemeMode.DARK -> true }
    val primary = when (accent) {
        Accent.GREEN -> if (dark) Color(0xFF78FF6E) else Color(0xFF12652B)
        Accent.CYAN -> if (dark) Color(0xFF67E8F9) else Color(0xFF006479)
        Accent.AMBER -> if (dark) Color(0xFFFFCA62) else Color(0xFF795400)
    }
    val base = if (dark) DarkColors else LightColors
    val container = when (accent) {
        Accent.GREEN -> if (dark) Color(0xFF15331B) else Color(0xFFD5F5D9)
        Accent.CYAN -> if (dark) Color(0xFF10313A) else Color(0xFFC9F1F7)
        Accent.AMBER -> if (dark) Color(0xFF382B13) else Color(0xFFFBE9BB)
    }
    MaterialTheme(colorScheme = base.copy(primary = primary, primaryContainer = container, secondaryContainer = container,
        onPrimaryContainer = primary, onSecondaryContainer = primary),
        typography = Typography(
            headlineLarge = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 35.sp, letterSpacing = (-1).sp),
            headlineMedium = androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),
            titleLarge = androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 27.sp),
            titleMedium = androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
            bodyLarge = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
            labelMedium = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp),
            labelSmall = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 14.sp),
        )) { content(dark) }
}
