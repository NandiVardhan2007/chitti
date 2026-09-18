package com.owlcoders.chitti.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.owlcoders.chitti.ui.theme.TextPrimaryDark
import com.owlcoders.chitti.ui.theme.TextSecondaryDark

@Composable
fun ChittiBranding(
    showBrand: Boolean,
    showSubtitle: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedVisibility(
            visible = showBrand,
            enter = fadeIn(animationSpec = tween(600)) + 
                    slideInVertically(
                        animationSpec = tween(600),
                        initialOffsetY = { 30 }
                    )
        ) {
            Text(
                text = "CHITTI",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimaryDark,
                letterSpacing = 4.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        AnimatedVisibility(
            visible = showSubtitle,
            enter = fadeIn(animationSpec = tween(500)) + 
                    slideInVertically(
                        animationSpec = tween(500),
                        initialOffsetY = { 20 }
                    )
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Your Private AI Assistant",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondaryDark,
                    letterSpacing = 1.sp
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "SEE • UNDERSTAND • REMEMBER • ACT",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Light,
                    color = TextSecondaryDark.copy(alpha = 0.7f),
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
