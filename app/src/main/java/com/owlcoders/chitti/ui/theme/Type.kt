package com.owlcoders.chitti.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.owlcoders.chitti.R

val ProfessionalFontFamily = FontFamily.SansSerif

val ChittiTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = ProfessionalFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
        color = AppBlack
    ),
    titleLarge = TextStyle(
        fontFamily = ProfessionalFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
        color = AppBlack
    )
)
