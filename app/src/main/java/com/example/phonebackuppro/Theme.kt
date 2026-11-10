package com.example.phonebackuppro

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.core.view.WindowCompat

val Vazir = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
    Font(R.font.vazirmatn_bold, FontWeight.ExtraBold),
    Font(R.font.vazirmatn_bold, FontWeight.SemiBold)
)

data class Accent(val name: String, val c1: Color, val c2: Color)

val Accents = listOf(
    Accent("بنفش", Color(0xFF7C5CFF), Color(0xFFB36BFF)),
    Accent("اقیانوس", Color(0xFF1E8CFF), Color(0xFF22D3EE)),
    Accent("زمرد", Color(0xFF0FBF8F), Color(0xFF7BE05A)),
    Accent("غروب", Color(0xFFFF6B4A), Color(0xFFFFB347))
)

/** Colours that Material3 has no slot for. */
class AppPalette(
    val dark: Boolean,
    val bg: Color,
    val card: Color,
    val cardHigh: Color,
    val text: Color,
    val textDim: Color,
    val stroke: Color,
    val g1: Color,
    val g2: Color
) {
    val brush: Brush get() = Brush.linearGradient(listOf(g1, g2))
    val success = Color(0xFF22C55E)
    val info = Color(0xFF38BDF8)
    val danger = Color(0xFFEF4444)
    val warn = Color(0xFFF59E0B)
}

val LocalPalette = staticCompositionLocalOf<AppPalette> { error("PhoneBackupTheme missing") }
val LocalSettings = staticCompositionLocalOf<AppSettings?> { null }

/** Short alias used everywhere: `pal.card`, `pal.brush`... */
val pal: AppPalette
    @Composable get() = LocalPalette.current

private fun palette(dark: Boolean, a: Accent) =
    if (dark) AppPalette(
        dark = true,
        bg = Color(0xFF0B0C1E), card = Color(0xFF15172F), cardHigh = Color(0xFF1E2142),
        text = Color(0xFFEDEEFF), textDim = Color(0xFF9A9DC4), stroke = Color(0x22FFFFFF),
        g1 = a.c1, g2 = a.c2
    ) else AppPalette(
        dark = false,
        bg = Color(0xFFF3F4FD), card = Color(0xFFFFFFFF), cardHigh = Color(0xFFE9EAFB),
        text = Color(0xFF14152B), textDim = Color(0xFF6A6D93), stroke = Color(0x14000000),
        g1 = a.c1, g2 = a.c2
    )

private fun typography(): Typography {
    val b = Typography()
    fun TextStyle.v() = copy(fontFamily = Vazir)
    return Typography(
        displayLarge = b.displayLarge.v(), displayMedium = b.displayMedium.v(), displaySmall = b.displaySmall.v(),
        headlineLarge = b.headlineLarge.v(), headlineMedium = b.headlineMedium.v(), headlineSmall = b.headlineSmall.v(),
        titleLarge = b.titleLarge.v(), titleMedium = b.titleMedium.v(), titleSmall = b.titleSmall.v(),
        bodyLarge = b.bodyLarge.v(), bodyMedium = b.bodyMedium.v(), bodySmall = b.bodySmall.v(),
        labelLarge = b.labelLarge.v(), labelMedium = b.labelMedium.v(), labelSmall = b.labelSmall.v()
    )
}

object Ty {
    val title = TextStyle(fontFamily = Vazir, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 32.sp)
    val h2 = TextStyle(fontFamily = Vazir, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 26.sp)
    val body = TextStyle(fontFamily = Vazir, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 22.sp)
    val bodyM = TextStyle(fontFamily = Vazir, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 22.sp)
    val small = TextStyle(fontFamily = Vazir, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp)
    val smallM = TextStyle(fontFamily = Vazir, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 18.sp)
    val big = TextStyle(fontFamily = Vazir, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 40.sp)
}

@Composable
fun PhoneBackupTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val accent = Accents[settings.accent.coerceIn(0, Accents.lastIndex)]
    val p = palette(dark, accent)

    val scheme = if (dark) darkColorScheme(
        primary = p.g1, secondary = p.g2, background = p.bg, surface = p.card,
        onPrimary = Color.White, onBackground = p.text, onSurface = p.text,
        surfaceVariant = p.cardHigh, onSurfaceVariant = p.textDim, outline = p.stroke
    ) else lightColorScheme(
        primary = p.g1, secondary = p.g2, background = p.bg, surface = p.card,
        onPrimary = Color.White, onBackground = p.text, onSurface = p.text,
        surfaceVariant = p.cardHigh, onSurfaceVariant = p.textDim, outline = p.stroke
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val c = WindowCompat.getInsetsController(window, view)
            c.isAppearanceLightStatusBars = !dark
            c.isAppearanceLightNavigationBars = !dark
        }
    }

    CompositionLocalProvider(
        LocalPalette provides p,
        LocalSettings provides settings,
        LocalLayoutDirection provides LayoutDirection.Rtl
    ) {
        MaterialTheme(colorScheme = scheme, typography = typography(), content = content)
    }
}
