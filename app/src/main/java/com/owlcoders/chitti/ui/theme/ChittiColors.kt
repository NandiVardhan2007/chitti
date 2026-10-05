package com.owlcoders.chitti.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * Chitti colour tokens, one set per appearance.
 *
 * The structure follows Apple's grouped-content model: a grouped background, a surface for the
 * groups that sit on it, and a raised fill for controls inside those groups. The values come from
 * the iQOO hackathon palette (the same one as the pitch deck): brand yellow #F0B31C on black
 * #050508, warm paper neutrals in light mode. Yellow is the single accent; semantic hues carry
 * state only: success, warning, danger, info.
 *
 * Every text token clears 4.5:1 against both `background` and `surface` in its appearance, except
 * `textLow`, which is reserved for placeholders, disabled glyphs and chevrons.
 *
 * Screens read these through `Chitti.colors`; no screen references a top-level Color value.
 */
@Immutable
data class ChittiColors(
    val isDark: Boolean,
    /** Page behind grouped content. */
    val background: Color,
    /** Groups, rows, bubbles: anything that sits on [background]. */
    val surface: Color,
    /** Inputs, chips and wells inside a [surface]. */
    val surfaceRaised: Color,
    /** Pressed-row highlight and quiet control fills. */
    val fill: Color,
    /** Thin rules between rows. */
    val separator: Color,
    val textHigh: Color,
    val textMid: Color,
    val textLow: Color,
    /** The one accent: selection, links, the primary action. */
    val accent: Color,
    /** Accent as a filled background behind [onAccent]. */
    val accentFill: Color,
    /** Deeper accent: the far side of a gradient, the pressed shade of a filled control. */
    val accentDeep: Color,
    val onAccent: Color,
    /** 12-16% accent, for selected capsules. */
    val accentWash: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val info: Color,
    val purple: Color,
    /** Tint laid over blurred content on a glass surface. */
    val glassTint: Color,
    /** The same surface when glass is not available: fully opaque. */
    val glassFallback: Color,
    /** Secondary text on glass. Darker than [textMid] so it holds 4.5:1 over any backdrop. */
    val glassTextMid: Color,
    /** The light-catching top edge of a glass surface. */
    val specular: Color,
    val shadow: Color,
    val scrim: Color
)

val LightChittiColors = ChittiColors(
    isDark = false,
    background = Color(0xFFF2F2EC),   // iQOO paper-deep
    surface = Color(0xFFFAFAF7),      // iQOO paper
    surfaceRaised = Color(0xFFECEAE3),
    fill = Color(0x1F78756B),
    separator = Color(0xFFDDD9CC),    // iQOO line, one step darker so it reads on paper
    textHigh = Color(0xFF000000),     // iQOO ink
    textMid = Color(0xFF5A5A5A),      // iQOO ink-mute
    textLow = Color(0xFF8C8A83),
    // Yellow text is unreadable on paper (1.8:1), so text uses the site's deep yellow-brown;
    // filled controls use the true brand yellow with black on top.
    accent = Color(0xFF8A6205),       // iQOO iqoo-text
    accentFill = Color(0xFFF0B31C),   // iQOO yellow
    accentDeep = Color(0xFFC8920A),   // iQOO iqoo-deep
    onAccent = Color(0xFF000000),
    accentWash = Color(0x2EF0B31C),
    success = Color(0xFF1E7B34),
    warning = Color(0xFFA34A00),
    danger = Color(0xFFC8102E),       // iQOO alert red
    info = Color(0xFF00699B),
    purple = Color(0xFF7C4DDA),       // iQOO HYD
    glassTint = Color(0xD9FAFAF7),
    glassFallback = Color(0xFFFAFAF7),
    glassTextMid = Color(0xFF4A4A48),
    specular = Color(0xFFFFFFFF),
    shadow = Color(0x33000000),
    scrim = Color(0x66000000)
)

val DarkChittiColors = ChittiColors(
    isDark = true,
    background = Color(0xFF050508),   // iQOO dark background
    surface = Color(0xFF1A1A1A),      // iQOO ink-soft
    surfaceRaised = Color(0xFF262626),
    fill = Color(0x2EFFFFFF),
    separator = Color(0xFF2E2E2E),
    textHigh = Color(0xFFFFFFFF),
    textMid = Color(0xFFB3B3B3),      // white at 70%, as on the site's dark sections
    textLow = Color(0xFF7A7A7A),
    accent = Color(0xFFF0B31C),       // iQOO yellow
    accentFill = Color(0xFFF0B31C),
    accentDeep = Color(0xFFC8920A),
    onAccent = Color(0xFF000000),
    accentWash = Color(0x2EF0B31C),
    success = Color(0xFF30D158),
    warning = Color(0xFFFF8A3D),      // orange, kept clear of the yellow accent
    danger = Color(0xFFFF5A6E),
    info = Color(0xFF64D2FF),
    purple = Color(0xFFA987F0),
    glassTint = Color(0xC71A1A1A),
    glassFallback = Color(0xFF1A1A1A),
    glassTextMid = Color(0xFFC9C9C9),
    specular = Color(0xFFFFFFFF),
    shadow = Color(0x80000000),
    scrim = Color(0x99000000)
)

val LocalChittiColors = staticCompositionLocalOf { DarkChittiColors }

/** Entry point for design tokens: `Chitti.colors.textHigh`. */
object Chitti {
    val colors: ChittiColors
        @Composable @ReadOnlyComposable get() = LocalChittiColors.current
}

/** Semantic helpers so every screen maps data to colour the same way. */
fun ChittiColors.category(category: String?): Color = when (category?.lowercase()) {
    "work", "office", "project" -> accent
    "academic", "college", "branch", "study" -> purple
    "personal", "person", "family" -> success
    "finance", "payment", "bill" -> warning
    else -> info
}

fun ChittiColors.urgency(urgency: String?): Color = when (urgency?.lowercase()) {
    "high" -> danger
    "medium" -> warning
    else -> success
}
