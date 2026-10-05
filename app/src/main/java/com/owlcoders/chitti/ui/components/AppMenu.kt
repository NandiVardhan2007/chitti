package com.owlcoders.chitti.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.owlcoders.chitti.ui.theme.Chitti

/** What the menu can do; provided once by MainActivity and read by every tab's menu button. */
class AppMenuActions(
    val accountLabel: String?,
    val onPersonalDetails: () -> Unit,
    val onBackup: () -> Unit,
    val onSettings: () -> Unit,
    val onSignOut: (() -> Unit)?
)

val LocalAppMenu = staticCompositionLocalOf<AppMenuActions?> { null }

/**
 * The menu in the top-right of each tab: a round "more" button that opens a short list of
 * everything that isn't a tab (Personal details, Backup, Settings, Sign out), anchored to the
 * button it came from.
 */
@Composable
fun AppMenuButton() {
    val actions = LocalAppMenu.current ?: return
    val colors = Chitti.colors
    val haptics = rememberHaptics()
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                    haptics.tick()
                    open = true
                }
                .semantics { contentDescription = "Menu" },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier.size(34.dp).clip(CircleShape).background(colors.fill),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.MoreHoriz, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
            }
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = MaterialTheme.shapes.large,
            containerColor = colors.surface,
            modifier = Modifier.widthIn(min = 220.dp)
        ) {
            if (actions.accountLabel != null) {
                Column(Modifier.padding(horizontal = Space.l, vertical = Space.s)) {
                    Text("Signed in as", style = MaterialTheme.typography.labelMedium, color = colors.textMid)
                    Text(actions.accountLabel, style = MaterialTheme.typography.bodyMedium, color = colors.textHigh, maxLines = 1)
                }
                HorizontalDivider(color = colors.separator, thickness = 0.5.dp)
            }
            MenuItem("Personal details", Icons.Rounded.Badge, colors.accent) { open = false; actions.onPersonalDetails() }
            MenuItem("Backup", Icons.Rounded.CloudUpload, colors.accent) { open = false; actions.onBackup() }
            MenuItem("Settings", Icons.Rounded.Settings, colors.accent) { open = false; actions.onSettings() }
            actions.onSignOut?.let { signOut ->
                HorizontalDivider(color = colors.separator, thickness = 0.5.dp)
                MenuItem("Sign out", Icons.AutoMirrored.Rounded.Logout, colors.danger, labelColor = colors.danger) { open = false; signOut() }
            }
        }
    }
}

@Composable
private fun MenuItem(
    label: String,
    icon: ImageVector,
    iconTint: Color,
    labelColor: Color = Chitti.colors.textHigh,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.typography.bodyLarge, color = labelColor) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = iconTint) },
        onClick = onClick
    )
}
