package com.quark.app.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * quark's palette, which is mostly not colour at all.
 *
 * The window is filled with the current cover, downscaled and blurred, and
 * darkened by half. Everything above it is translucent white or black with a
 * hairline border — glass over the artwork. Nothing here is a Material role;
 * see docs/decisions/02-visual-language.md for where each value comes from.
 */
@Immutable
data class QuarkColors(
    /** Behind the blurred cover, and what is shown before one is decoded. */
    val background: Color = Color(0xFF0E0E11),

    /** Laid over the blurred cover so text stays readable on bright artwork. */
    val backgroundScrim: Color = Color(0x80000000),

    val text: Color = Color.White,
    val textSecondary: Color = Color(0xFFB9B9B9),
    val textMuted: Color = Color(0x99FFFFFF),

    /** Controls sitting on glass. */
    val control: Color = Color(0x324A4A4D),
    val controlHover: Color = Color(0x554A4A4D),

    val track: Color = Color(0x40FFFFFF),
    val divider: Color = Color(0x1AFFFFFF),
    val rowHover: Color = Color(0x12FFFFFF),
    val rowSelected: Color = Color(0x1FFFFFFF),
)

/** One of the three glass recipes the original uses. */
@Immutable
data class Glass(
    val tint: Color,
    val border: Color,
    val gradient: List<Color>? = null,
    /** How hard the backdrop is blurred, in the original's sigma. */
    val blur: Float = 20f,
) {
    companion object {
        /** Settings and other large surfaces: bright, heavily blurred. */
        val Panel = Glass(
            tint = Color(0x33FFFFFF),
            border = Color(0x33FFFFFF),
            gradient = listOf(Color(0x26FFFFFF), Color(0x0DFFFFFF)),
            blur = 75f,
        )

        /** The player's own cards: dark, so artwork does not wash the text out. */
        val Card = Glass(
            tint = Color(0x73000000),
            border = Color(0x14FFFFFF),
            blur = 20f,
        )

        /** Dialogs: barely tinted, lightly blurred. */
        val Dialog = Glass(
            tint = Color(0x37000000),
            border = Color(0x33FFFFFF),
            blur = 10f,
        )
    }

    val brush: Brush? get() = gradient?.let(Brush::verticalGradient)
}

/** Colours taken from the current cover; see `AccentPalette`. */
@Immutable
data class AccentColors(
    val primary: Color = Color(0xFFB9B9B9),
    val secondary: Color = Color(0xFF8E8E98),
    val tertiary: Color = Color(0xFF5A5A66),
) {
    val gradient: List<Color> get() = listOf(primary, secondary, tertiary)
}

@Immutable
data class QuarkTypography(
    val trackTitle: TextStyle,
    val trackSubtitle: TextStyle,
    val nowPlayingTitle: TextStyle,
    val nowPlayingAlbum: TextStyle,
    val nowPlayingArtist: TextStyle,
    val panelTitle: TextStyle,
    val label: TextStyle,
    val button: TextStyle,
    val heading: TextStyle,
    val body: TextStyle,
)

/**
 * The desktop player uses the platform's own UI font, as the Flutter build did:
 * only its Android layout reached for Lexend. Nothing is bundled, so the app
 * looks native on each system.
 */
private fun typography(family: FontFamily = FontFamily.SansSerif) = QuarkTypography(
    trackTitle = TextStyle(fontFamily = family, fontSize = 14.sp),
    trackSubtitle = TextStyle(fontFamily = family, fontSize = 13.sp),
    nowPlayingTitle = TextStyle(fontFamily = family, fontSize = 30.sp, fontWeight = FontWeight.Bold),
    nowPlayingAlbum = TextStyle(fontFamily = family, fontSize = 17.sp),
    nowPlayingArtist = TextStyle(fontFamily = family, fontSize = 14.sp),
    panelTitle = TextStyle(fontFamily = family, fontSize = 17.sp, fontWeight = FontWeight.Medium),
    label = TextStyle(fontFamily = family, fontSize = 12.sp),
    button = TextStyle(fontFamily = family, fontSize = 15.sp),
    heading = TextStyle(fontFamily = family, fontSize = 21.sp, fontWeight = FontWeight.Bold),
    body = TextStyle(fontFamily = family, fontSize = 14.sp),
)

/** Corner radii, taken from the widths the original passes to `BorderRadius.circular`. */
object Radius {
    val cover = 10.dp
    val thumbnail = 3.dp
    val card = 14.dp
    val panel = 15.dp
    val control = 10.dp
    val pill = 24.dp
}

val LocalQuarkColors = staticCompositionLocalOf { QuarkColors() }
val LocalAccent = staticCompositionLocalOf { AccentColors() }
val LocalTypography = staticCompositionLocalOf { typography() }

object Quark {
    val colors: QuarkColors @Composable get() = LocalQuarkColors.current
    val accent: AccentColors @Composable get() = LocalAccent.current
    val type: QuarkTypography @Composable get() = LocalTypography.current
}

@Composable
fun QuarkTheme(
    accent: AccentColors = AccentColors(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalQuarkColors provides QuarkColors(),
        LocalAccent provides accent,
        LocalTypography provides typography(),
        // Material's ripple is the most recognisable thing about it; controls
        // here mark hover and press by changing their own fill.
        LocalIndication provides NoIndication,
        LocalTextSelectionColors provides TextSelectionColors(
            handleColor = Color.White,
            backgroundColor = Color(0x40FFFFFF),
        ),
        content = content,
    )
}
