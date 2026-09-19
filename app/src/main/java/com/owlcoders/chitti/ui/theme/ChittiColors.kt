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
 * groups that sit on it, and a raised fill for controls inside those groups. The neutrals are
 * true greys (not tinted), so the single accent is the only colour on screen that is not a
 * statement of meaning. Semantic hues carry state only: success, warning, danger, info.
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
    background = Color(0xFFF2F2F7),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFF2F2F7),
    fill = Color(0x1F787880),
    separator = Color(0xFFD1D1D6),
    textHigh = Color(0xFF000000),
    textMid = Color(0xFF6C6C70),
    textLow = Color(0xFF9A9AA0),
    accent = Color(0xFF3A57D9),
    accentFill = Color(0xFF3A57D9),
    onAccent = Color(0xFFFFFFFF),
    accentWash = Color(0x1F3A57D9),
    success = Color(0xFF1E7B34),
    warning = Color(0xFFA85100),
    danger = Color(0xFFD70015),
    info = Color(0xFF00699B),
    purple = Color(0xFF8944AB),
    glassTint = Color(0xD9F9F9FB),
    glassFallback = Color(0xFFF9F9FB),
    glassTextMid = Color(0xFF505055),
    specular = Color(0xFFFFFFFF),
    shadow = Color(0x33000000),
    scrim = Color(0x66000000)
)

val DarkChittiColors = ChittiColors(
    isDark = true,
    background = Color(0xFF000000),
    surface = Color(0xFF1C1C1E),
    surfaceRaised = Color(0xFF2C2C2E),
    fill = Color(0x3D767680),
    separator = Color(0xFF38383A),
    textHigh = Color(0xFFFFFFFF),
    textMid = Color(0xFFAEAEB2),
    textLow = Color(0xFF7C7C80),
    accent = Color(0xFF8FA6FF),
    accentFill = Color(0xFF4E6EF2),
    onAccent = Color(0xFFFFFFFF),
    accentWash = Color(0x2E8FA6FF),
    success = Color(0xFF30D158),
    warning = Color(0xFFFF9F0A),
    danger = Color(0xFFFF6961),
    info = Color(0xFF64D2FF),
    purple = Color(0xFFDA8FFF),
    glassTint = Color(0xC71C1C1E),
    glassFallback = Color(0xFF1C1C1E),
    glassTextMid = Color(0xFFC7C7CC),
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
