package com.airware.loader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Material 3 Expressive тема.
 *
 * Что изменилось относительно старой темы:
 *  - полный набор цветовых ролей (контейнеры, уровни surface, tertiary-акцент),
 *    а не 10 ручных цветов — это даёт «сочные» заливки, на которые опирается Expressive;
 *  - винно-розовая айдентика сохранена, но вытянута в полноценную тональную палитру
 *    с тёплым tertiary-акцентом для экспрессивных пятен;
 *  - крупная, жирная типографика (Expressive тяготеет к bold и большим кеглям);
 *  - расширенная система форм (крупные и разнообразные скругления);
 *  - MaterialExpressiveTheme + MotionScheme.expressive() — пружинная физика движения.
 */

// ----------------------------------------------------------------------------
// Цвет
// ----------------------------------------------------------------------------

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB0CB),
    onPrimary = Color(0xFF5E1133),
    primaryContainer = Color(0xFF7B2949),
    onPrimaryContainer = Color(0xFFFFD9E4),

    secondary = Color(0xFFE3BAC9),
    onSecondary = Color(0xFF422733),
    secondaryContainer = Color(0xFF5B3D49),
    onSecondaryContainer = Color(0xFFFFD9E4),

    // Тёплый tertiary — экспрессивный «контрастный» акцент для акцентных пятен/иконок
    tertiary = Color(0xFFF5B9A0),
    onTertiary = Color(0xFF4C1B0A),
    tertiaryContainer = Color(0xFF68301D),
    onTertiaryContainer = Color(0xFFFFDBCF),

    background = Color(0xFF18110F),
    onBackground = Color(0xFFF0E0DD),

    surface = Color(0xFF18110F),
    onSurface = Color(0xFFF0E0DD),
    surfaceVariant = Color(0xFF524345),
    onSurfaceVariant = Color(0xFFD7C2C4),

    surfaceContainerLowest = Color(0xFF120C0B),
    surfaceContainerLow = Color(0xFF211917),
    surfaceContainer = Color(0xFF261D1B),
    surfaceContainerHigh = Color(0xFF312725),
    surfaceContainerHighest = Color(0xFF3C3230),

    inverseSurface = Color(0xFFF0E0DD),
    inverseOnSurface = Color(0xFF382E2C),
    inversePrimary = Color(0xFF984061),

    outline = Color(0xFFA08C8E),
    outlineVariant = Color(0xFF524345),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF984061),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9E4),
    onPrimaryContainer = Color(0xFF3E001E),

    secondary = Color(0xFF74565F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD9E4),
    onSecondaryContainer = Color(0xFF2B151D),

    tertiary = Color(0xFF7F5539),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDBCF),
    onTertiaryContainer = Color(0xFF311300),

    background = Color(0xFFFFF8F7),
    onBackground = Color(0xFF22191A),

    surface = Color(0xFFFFF8F7),
    onSurface = Color(0xFF22191A),
    surfaceVariant = Color(0xFFF2DDE0),
    onSurfaceVariant = Color(0xFF524345),

    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF0F2),
    surfaceContainer = Color(0xFFFCEAED),
    surfaceContainerHigh = Color(0xFFF7E4E8),
    surfaceContainerHighest = Color(0xFFF1DFE2),

    inverseSurface = Color(0xFF382E2C),
    inverseOnSurface = Color(0xFFFFEDEA),
    inversePrimary = Color(0xFFFFB0CB),

    outline = Color(0xFF847375),
    outlineVariant = Color(0xFFD7C2C4),

    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

// ----------------------------------------------------------------------------
// Типографика — экспрессивная: жирнее и крупнее, с плотным трекингом в заголовках
// ----------------------------------------------------------------------------

private val ExpressiveTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        displayMedium = displayMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Bold),

        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),

        titleLarge = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            lineHeight = 30.sp,
            letterSpacing = (-0.2).sp,
        ),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.SemiBold),

        bodyLarge = bodyLarge.copy(fontSize = 16.sp),
        bodyMedium = bodyMedium.copy(fontSize = 14.sp),

        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
        labelMedium = labelMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = labelSmall.copy(fontWeight = FontWeight.Medium),
    )
}

// ----------------------------------------------------------------------------
// Формы — крупнее и разнообразнее, чем в базовом M3
// ----------------------------------------------------------------------------

private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(40.dp),
)

// ----------------------------------------------------------------------------
// Тема
// ----------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AirwareLoaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialExpressiveTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = ExpressiveTypography,
        shapes = ExpressiveShapes,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
          }
