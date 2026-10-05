package com.owlcoders.chitti.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.owlcoders.chitti.R
import com.owlcoders.chitti.ui.theme.Chitti

/*
 * Google's own sign-in button, per the Google Identity branding guidelines: the full-colour "G" on
 * white (light) or #131314 (dark), a 1dp outline, Roboto Medium label, pill shape. These colours
 * are Google's, not Chitti's, so they are fixed here rather than taken from the theme.
 */
private val LightFill = Color(0xFFFFFFFF)
private val LightStroke = Color(0xFF747775)
private val LightText = Color(0xFF1F1F1F)
private val DarkFill = Color(0xFF131314)
private val DarkStroke = Color(0xFF8E918F)
private val DarkText = Color(0xFFE3E3E3)

@Composable
fun GoogleSignInButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: String = "Continue with Google"
) {
    val dark = Chitti.colors.isDark
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .pressScale(interaction, pressed = 0.97f)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(CircleShape)
            .background(if (dark) DarkFill else LightFill)
            .border(1.dp, if (dark) DarkStroke else LightStroke, CircleShape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            // The guideline's pressed state: a 12% overlay of the label colour.
            .background(if (pressed) (if (dark) DarkText else LightText).copy(alpha = 0.12f) else Color.Transparent),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(painterResource(R.drawable.ic_google_g), contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            text,
            style = TextStyle(
                fontFamily = FontFamily.SansSerif, // Roboto on Android, as the guidelines ask
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                letterSpacing = 0.1.sp
            ),
            color = if (dark) DarkText else LightText,
            maxLines = 1
        )
    }
}
