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
import com.quark.app.i18n.LocalStrings
import com.quark.app.i18n.Strings

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

    /** Menus and dialogs, which sit over anything and so are nearly opaque. */
    val menu: Color = Color(0xF21C1C21),

    /** Errors and destructive actions. */
    val danger: Color = Color(0xFFFF6B6B),

    val isLight: Boolean = false,
) {
    companion object {
        val Dark = QuarkColors()

        /**
         * The light palette the slop branch added (`appThemeMode`): the same
         * glass, frosted white instead of smoked, with ink for text.
         */
        val Light = QuarkColors(
            background = Color(0xFFF1F1F4),
            backgroundScrim = Color(0xA6FFFFFF),
            text = Color(0xFF141418),
            textSecondary = Color(0xFF4B4B53),
            textMuted = Color(0x8C000000),
            control = Color(0x14000000),
            controlHover = Color(0x26000000),
            track = Color(0x33000000),
            divider = Color(0x1A000000),
            rowHover = Color(0x0D000000),
            rowSelected = Color(0x17000000),
            menu = Color(0xF7FAFAFC),
            danger = Color(0xFFD93B3B),
            isLight = true,
        )
    }
}

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

    /** The same recipe frosted rather than smoked, for the light theme. */
    fun forLight(): Glass = Glass(
        tint = Color.White.copy(alpha = (tint.alpha + 0.25f).coerceAtMost(0.85f)),
        border = Color.Black.copy(alpha = 0.08f),
        gradient = gradient?.map { Color.White.copy(alpha = it.alpha) },
        blur = blur,
    )
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
    light: Boolean = false,
    strings: Strings = Strings(),
    content: @Composable () -> Unit,
) {
    val colors = if (light) QuarkColors.Light else QuarkColors.Dark
    CompositionLocalProvider(
        LocalQuarkColors provides colors,
        LocalAccent provides accent,
        LocalTypography provides typography(),
        LocalStrings provides strings,
        // Material's ripple is the most recognisable thing about it; controls
        // here mark hover and press by changing their own fill.
        LocalIndication provides NoIndication,
        LocalTextSelectionColors provides TextSelectionColors(
            handleColor = colors.text,
            backgroundColor = colors.text.copy(alpha = 0.25f),
        ),
        content = content,
    )
}
