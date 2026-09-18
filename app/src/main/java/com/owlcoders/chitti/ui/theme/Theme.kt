package com.owlcoders.chitti.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val ChittiColorScheme = lightColorScheme(
    primary = AppYellow,
    background = AppWhite,
    surface = AppWhite,
    onPrimary = AppBlack,
    onBackground = AppBlack,
    onSurface = AppBlack
)

@Composable
fun ChittiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ChittiColorScheme,
        typography = ChittiTypography,
        content = content
    )
}
