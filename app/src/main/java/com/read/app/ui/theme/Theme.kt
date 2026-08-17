package com.read.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// 暖色书卷配色 —— 纸感 + 墨色，适合阅读类应用
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6F4E37),           // 咖啡棕
    onPrimary = Color(0xFFFFFBFF),
    primaryContainer = Color(0xFFF8E5D5),
    onPrimaryContainer = Color(0xFF2A1508),
    secondary = Color(0xFF8A6F5C),
    onSecondary = Color(0xFFFFFBFF),
    secondaryContainer = Color(0xFFF3E4D8),
    tertiary = Color(0xFF5C715E),          // 墨绿点缀
    tertiaryContainer = Color(0xFFDFEFE0),
    background = Color(0xFFFBF7F2),        // 纸白（非纯白）
    onBackground = Color(0xFF3B3226),
    surface = Color(0xFFFBF7F2),
    onSurface = Color(0xFF3B3226),
    surfaceVariant = Color(0xFFF2E9DF),
    onSurfaceVariant = Color(0xFF54453A),
    outline = Color(0xFFA39284)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFD7B49A),
    onPrimary = Color(0xFF3A2311),
    primaryContainer = Color(0xFF533A26),
    onPrimaryContainer = Color(0xFFF8E5D5),
    secondary = Color(0xFFD4B9A4),
    onSecondary = Color(0xFF392617),
    secondaryContainer = Color(0xFF513B2B),
    tertiary = Color(0xFFB4CDB4),
    tertiaryContainer = Color(0xFF314234),
    background = Color(0xFF1E1A16),        // 暖深棕黑（非纯黑）
    onBackground = Color(0xFFEDE1D5),
    surface = Color(0xFF1E1A16),
    onSurface = Color(0xFFEDE1D5),
    surfaceVariant = Color(0xFF3B332B),
    onSurfaceVariant = Color(0xFFCCC0B4),
    outline = Color(0xFF8A7A6C)
)

// 书名、标题使用衬线字体，营造书籍质感
private val SerifHeadings = FontFamily.Serif

private val AppTypography = Typography(
    headlineSmall = TextStyle(fontFamily = SerifHeadings, fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
    titleLarge = TextStyle(fontFamily = SerifHeadings, fontWeight = FontWeight.Medium, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = SerifHeadings, fontWeight = FontWeight.Medium, fontSize = 16.sp),
    titleSmall = TextStyle(fontFamily = SerifHeadings, fontWeight = FontWeight.Medium, fontSize = 14.sp)
)

@Composable
fun ReadAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // 阅读应用使用专属暖色配色，不跟随壁纸动态取色，保证护眼一致性
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
