package com.owlcoders.chitti.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.owlcoders.chitti.db.entities.Memory
import com.owlcoders.chitti.db.entities.NotificationEntity
import com.owlcoders.chitti.ui.components.ChittiTextField
import com.owlcoders.chitti.ui.components.Dot
import com.owlcoders.chitti.ui.components.Inset
import com.owlcoders.chitti.ui.components.InsetRow
import com.owlcoders.chitti.ui.components.LinkButton
import com.owlcoders.chitti.ui.components.Motion
import com.owlcoders.chitti.ui.components.Space
import com.owlcoders.chitti.ui.components.SwipeAction
import com.owlcoders.chitti.ui.components.SwipeActionBox
import com.owlcoders.chitti.ui.components.rememberHaptics
import com.owlcoders.chitti.ui.theme.Chitti
import com.owlcoders.chitti.ui.theme.category
import androidx.compose.animation.animateColorAsState

/** Callbacks the Library screens share. */
class LibraryActions(
    val onMarkHandled: (NotificationEntity) -> Unit,
    val onDeleteNotification: (NotificationEntity) -> Unit,
    val onSaveMemory: (existing: Memory?, key: String, value: String, category: String) -> Unit,
    val onDeleteMemory: (Memory) -> Unit
)

/** The app's own icon, as Notification Center shows it. Falls back to its initial. */
@Composable
fun AppIcon(packageName: String, size: Dp = Inset.iconWell) {
    val context = LocalContext.current
    val bitmap: ImageBitmap? = remember(packageName) {
        try {
            context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
    if (bitmap != null) {
        Image(bitmap, contentDescription = null, modifier = Modifier.size(size).clip(RoundedCornerShape(size * 0.24f)))
    } else {
        val colors = Chitti.colors
        Box(
            modifier = Modifier.size(size).clip(RoundedCornerShape(size * 0.24f)).background(colors.fill),
            contentAlignment = Alignment.Center
        ) {
            Text(appLabel(context, packageName).take(1), style = MaterialTheme.typography.labelLarge, color = colors.textMid)
        }
    }
}

/**
 * A notification Chitti found. App icon, app and time on the first line, then what it said.
 * New (not yet handled) ones carry an accent dot, like unread mail. Tap to read it in full.
 * Swipe right to mark handled, left to delete.
 */
@Composable
fun NotificationRow(notification: NotificationEntity, actions: LibraryActions, removesWhenHandled: Boolean = false) {
    val colors = Chitti.colors
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val app = remember(notification.packageName) { appLabel(context, notification.packageName) }
    SwipeActionBox(
        startAction = if (notification.processed) null else SwipeAction(
            label = "Handled",
            icon = Icons.Rounded.Done,
            tint = colors.success,
            removes = removesWhenHandled,
            onCommit = { actions.onMarkHandled(notification) }
        ),
        endAction = SwipeAction(
            label = "Delete",
            icon = Icons.Rounded.Delete,
            tint = colors.danger,
            removes = true,
            onCommit = { actions.onDeleteNotification(notification) }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClickLabel = if (expanded) "Show less" else "Read all"
                ) { expanded = !expanded }
                .semantics { stateDescription = if (notification.processed) "Handled" else "New" }
                .padding(horizontal = Inset.textInset, vertical = Space.m),
            verticalAlignment = Alignment.Top
        ) {
            AppIcon(notification.packageName)
            Spacer(Modifier.width(Space.m))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(app, style = MaterialTheme.typography.labelMedium, color = colors.textMid, modifier = Modifier.weight(1f), maxLines = 1)
                    Text(relativeTime(notification.postTime), style = MaterialTheme.typography.labelMedium, color = colors.textMid)
                    if (!notification.processed) {
                        Spacer(Modifier.width(6.dp))
                        Dot(colors.accent)
                    }
                }
                Text(
                    notification.rawTitle.ifBlank { app },
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textHigh,
                    maxLines = if (expanded) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (notification.rawText.isNotBlank()) {
                    Text(
                        notification.rawText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMid,
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** Something Chitti knows about you. Tap to edit, swipe left to forget. */
@Composable
fun MemoryRow(memory: Memory, actions: LibraryActions, onEdit: (Memory) -> Unit, showCategory: Boolean = true) {
    val colors = Chitti.colors
    SwipeActionBox(
        endAction = SwipeAction(
            label = "Forget",
            icon = Icons.Rounded.Delete,
            tint = colors.danger,
            removes = true,
            onCommit = { actions.onDeleteMemory(memory) }
        )
    ) {
        InsetRow(
            title = memory.key,
            subtitle = memory.value,
            subtitleLines = 3,
            value = if (showCategory) memory.category.titleCase() else null,
            valueColor = colors.category(memory.category),
            chevron = true,
            onClick = { onEdit(memory) }
        )
    }
}

private val DefaultCategories = listOf("note", "person", "project", "college", "branch")

/** Add or edit something Chitti knows. Save is the confirming action; it stays off until both fields have text. */
@Composable
fun MemoryEditor(
    initial: Memory?,
    knownCategories: List<String>,
    onDismiss: () -> Unit,
    onSave: (key: String, value: String, category: String) -> Unit
) {
    val colors = Chitti.colors
    val haptics = rememberHaptics()
    var key by remember { mutableStateOf(initial?.key.orEmpty()) }
    var value by remember { mutableStateOf(initial?.value.orEmpty()) }
    var category by remember { mutableStateOf(initial?.category ?: "note") }
    val options = (DefaultCategories + knownCategories).distinct()
    val canSave = key.isNotBlank() && value.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = colors.surface,
        title = {
            Text(
                if (initial != null) "Edit" else "Something to know",
                style = MaterialTheme.typography.titleLarge,
                color = colors.textHigh
            )
        },
        text = {
            Column {
                ChittiTextField(value = key, onValueChange = { key = it }, placeholder = "My branch", label = "What")
                Spacer(Modifier.height(Space.m))
                ChittiTextField(value = value, onValueChange = { value = it }, placeholder = "CSE - AIML", label = "Answer", singleLine = false)
                Spacer(Modifier.height(Space.m))
                Text("Kind", style = MaterialTheme.typography.labelMedium, color = colors.textMid, modifier = Modifier.padding(start = Space.xs))
                Spacer(Modifier.height(Space.xs))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Space.s)
                ) {
                    options.forEach { option ->
                        ChoiceCapsule(option.titleCase(), selected = option == category) { category = option }
                    }
                }
            }
        },
        confirmButton = {
            LinkButton(
                text = "Save",
                enabled = canSave,
                style = MaterialTheme.typography.titleLarge,
                onClick = {
                    haptics.confirm()
                    onSave(key.trim(), value.trim(), category)
                }
            )
        },
        dismissButton = { LinkButton(text = "Cancel", onClick = onDismiss) }
    )
}

/** A selectable capsule: filled with the accent wash when chosen. */
@Composable
fun ChoiceCapsule(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = Chitti.colors
    val bg by animateColorAsState(if (selected) colors.accentWash else colors.fill, Motion.standard(), label = "choiceBg")
    val fg by animateColorAsState(if (selected) colors.accent else colors.textHigh, Motion.standard(), label = "choiceFg")
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = fg,
            modifier = Modifier
                .clip(CircleShape)
                .background(bg)
                .padding(horizontal = Space.m, vertical = Space.s)
        )
    }
}
