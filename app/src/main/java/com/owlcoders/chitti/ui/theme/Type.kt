package com.owlcoders.chitti.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.owlcoders.chitti.R
import kotlin.math.exp

/*
 * Type.
 *
 * Inter, fetched through the Google Fonts provider (falls back to the system sans until it has
 * downloaded). The scale mirrors Apple's text styles, so hierarchy comes from the same size /
 * weight / leading sets: Large Title 34, Title 28 / 22 / 20, Headline 17 semibold, Body 17,
 * Subheadline 15, Footnote 13, Caption 12 / 11.
 *
 * Tracking is size-specific, from Inter's own dynamic-metrics formula: tight as the type grows,
 * slightly open at caption sizes. Leading tightens as size grows. Styles carry no colour.
 */
private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

private val inter = GoogleFont("Inter")

val InterFamily = FontFamily(
    Font(googleFont = inter, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = inter, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = inter, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = inter, fontProvider = provider, weight = FontWeight.Bold)
)

/** Inter's recommended tracking for a size: -0.0223 + 0.185 * e^(-0.1745 * size), in em. */
private fun tracking(size: Float): TextUnit = (size * (-0.0223f + 0.185f * exp(-0.1745f * size))).sp

private fun style(size: Float, leading: Float, weight: FontWeight) = TextStyle(
    fontFamily = InterFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = leading.sp,
    letterSpacing = tracking(size)
)

val ChittiTypography = Typography(
    displayLarge = style(34f, 41f, FontWeight.Bold),      // Large Title
    headlineLarge = style(28f, 34f, FontWeight.Bold),     // Title 1
    headlineMedium = style(22f, 28f, FontWeight.Bold),    // Title 2
    headlineSmall = style(20f, 25f, FontWeight.SemiBold), // Title 3
    titleLarge = style(17f, 22f, FontWeight.SemiBold),    // Headline
    titleMedium = style(16f, 21f, FontWeight.SemiBold),   // Callout, emphasised
    titleSmall = style(15f, 20f, FontWeight.SemiBold),    // Subheadline, emphasised
    bodyLarge = style(17f, 22f, FontWeight.Normal),       // Body
    bodyMedium = style(15f, 20f, FontWeight.Normal),      // Subheadline
    bodySmall = style(13f, 18f, FontWeight.Normal),       // Footnote
    labelLarge = style(15f, 20f, FontWeight.SemiBold),    // Buttons
    labelMedium = style(12f, 16f, FontWeight.Medium),     // Caption 1
    labelSmall = style(11f, 13f, FontWeight.Medium)       // Caption 2
)

/**
 * For any number that changes while on screen: tabular figures, so digits keep their width and
 * a count ticking from 9 to 10 does not shove its neighbours sideways.
 */
fun TextStyle.metricNumber(): TextStyle = copy(fontFeatureSettings = "tnum")
