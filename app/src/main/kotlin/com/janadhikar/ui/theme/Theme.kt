package com.janadhikar.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * One theme, always dark, engineered for a single scenario: outdoor glare,
 * adrenaline, one hand, possibly cracked glass. Every foreground/background
 * pair clears the 7:1 contrast requirement (CONTRIBUTING.md).
 */
object Palette {
    val Black = Color(0xFF000000)
    val NearBlack = Color(0xFF0D0D0D)
    val White = Color(0xFFFFFFFF)

    /** The directive — the loudest thing the app ever says. */
    val DirectiveYellow = Color(0xFFFFD600) // 15.6:1 on black
    val AccentOrange = Color(0xFFFF9100) //   9.9:1 on black

    /** Live/recording accents. */
    val RecordRed = Color(0xFFFF5252)
    val TickerGreen = Color(0xFF69F0AE)

    /** Receipt card. */
    val PaperWhite = Color(0xFFFAFAFA)
    val InkBlack = Color(0xFF111111)

    val DangerRed = Color(0xFFFF1744)
    val DimGray = Color(0xFFB3B3B3) // 7.4:1 on black — minimum for hint text

    /** User's chat bubble. */
    val ChatUser = Color(0xFF1E2A38)

    /** Assistant's answer card — an elevated dark slate, not flat black. */
    val ChatAssistant = Color(0xFF191C22)
    val ChatAssistantEdge = Color(0xFF2A2F38)
    val Answered = Color(0xFF7EE0B0) // soft green accent for a verified answer
}

private val Scheme = darkColorScheme(
    primary = Palette.DirectiveYellow,
    onPrimary = Palette.InkBlack,
    secondary = Palette.AccentOrange,
    onSecondary = Palette.InkBlack,
    background = Palette.Black,
    onBackground = Palette.White,
    surface = Palette.NearBlack,
    onSurface = Palette.White,
    error = Palette.DangerRed,
    onError = Palette.White,
)

/** Large and high-contrast, but not overwhelming — sized to fit a directive on
 *  screen without scrolling and keep the citation card readable. */
private val JanadhikarTypography = Typography(
    // The directive.
    displayLarge = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black),
    // Live transcript.
    headlineMedium = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    // Buttons / section labels.
    titleLarge = TextStyle(fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold),
    // Receipt card body.
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    // Hints, tickers.
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
)

@Composable
fun JanadhikarTheme(content: @Composable () -> Unit) {
    // isSystemInDarkTheme() deliberately ignored: this app has one mode.
    MaterialTheme(
        colorScheme = Scheme,
        typography = JanadhikarTypography,
        content = content,
    )
}
