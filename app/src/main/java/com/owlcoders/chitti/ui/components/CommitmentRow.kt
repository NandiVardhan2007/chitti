package com.owlcoders.chitti.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.owlcoders.chitti.ChittiApp
import com.owlcoders.chitti.db.CapturedEvent
import com.owlcoders.chitti.ui.theme.Chitti
import com.owlcoders.chitti.ui.theme.category
import com.owlcoders.chitti.ui.theme.urgency
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Where a commitment's text starts (past the completion circle); its separator starts here too. */
val CommitmentTextInset = 49.dp

private fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

/**
 * Hands the commitment to the clock app. ACTION_SET_ALARM is gated by the Clock app's own
 * SET_ALARM permission (declared in our manifest); if no clock resolves it we try a timer, then
 * say so.
 */
private fun launchReminder(context: Context, event: CapturedEvent) {
    val title = event.extractedWhat ?: "Chitti reminder"
    for (action in listOf(AlarmClock.ACTION_SET_ALARM, AlarmClock.ACTION_SET_TIMER)) {
        val intent = Intent(action).apply {
            putExtra(AlarmClock.EXTRA_MESSAGE, title)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
            return
        } catch (e: ActivityNotFoundException) {
            // try the next one
        } catch (e: SecurityException) {
            toast(context, "Reminders need the alarm permission")
            return
        }
    }
    toast(context, "No clock app found to set a reminder")
}

private fun addToCalendar(context: Context, event: CapturedEvent) {
    val intent = Intent(Intent.ACTION_INSERT).apply {
        data = CalendarContract.Events.CONTENT_URI
        putExtra(CalendarContract.Events.TITLE, event.extractedWhat)
        putExtra(CalendarContract.Events.DESCRIPTION, "From ${event.sourceApp}\n${event.rawText}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        toast(context, "No calendar app found")
    }
}

/**
 * One commitment Chitti found, as a list row in the style of Reminders: a completion circle, what
 * it is, and when / who / where it came from underneath. Tapping the row opens its actions in
 * place (reply, calendar, remind); tapping the circle completes it.
 */
@Composable
fun CommitmentRow(
    event: CapturedEvent,
    sourceName: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = Chitti.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var expanded by remember { mutableStateOf(false) }
    var completing by remember { mutableStateOf(false) }
    var reply by remember { mutableStateOf<String?>(null) }
    var drafting by remember { mutableStateOf(false) }

    // The circle fills first, then the row leaves: the outcome is seen before it disappears.
    LaunchedEffect(completing) {
        if (completing) {
            delay(420)
            onDone()
        }
    }

    val urgent = event.urgency.equals("high", ignoreCase = true)
    val meta = listOfNotNull(
        event.extractedWhen?.takeIf { it.isNotBlank() },
        event.extractedWho?.takeIf { it.isNotBlank() && !it.equals("self", ignoreCase = true) },
        sourceName.takeIf { it.isNotBlank() }
    ).joinToString(" · ")

    val rowInteraction = remember { MutableInteractionSource() }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = rowInteraction,
                    indication = null,
                    role = Role.Button,
                    onClickLabel = if (expanded) "Hide actions" else "Show actions"
                ) { expanded = !expanded }
                .padding(end = Inset.textInset, top = Space.xs, bottom = Space.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompletionCircle(
                done = completing,
                ring = if (urgent) colors.danger else colors.textLow,
                onClick = {
                    if (!completing) {
                        haptics.confirm()
                        completing = true
                    }
                }
            )
            Column(modifier = Modifier.weight(1f).padding(vertical = Space.s)) {
                Text(
                    event.extractedWhat ?: event.rawText.take(80),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (completing) colors.textMid else colors.textHigh,
                    maxLines = if (expanded) 4 else 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val cat = event.category
                    if (!cat.isNullOrBlank()) {
                        Text(cat, style = MaterialTheme.typography.bodySmall, color = colors.category(cat))
                        if (meta.isNotEmpty()) Text(" · ", style = MaterialTheme.typography.bodySmall, color = colors.textMid)
                    }
                    if (meta.isNotEmpty()) {
                        Text(meta, style = MaterialTheme.typography.bodySmall, color = colors.textMid, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (urgent) {
                Spacer(Modifier.width(Space.s))
                StatusPill(text = "Urgent", tint = colors.urgency("high"))
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(Motion.standard()) + fadeIn(Motion.fade()),
            exit = shrinkVertically(Motion.standard()) + fadeOut(Motion.fade(140))
        ) {
            Column(modifier = Modifier.padding(start = CommitmentTextInset, end = Inset.textInset, bottom = Space.m)) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Space.s)
                ) {
                    SecondaryButton(
                        text = if (drafting) "Drafting…" else "Reply",
                        icon = Icons.AutoMirrored.Rounded.Reply,
                        enabled = !drafting,
                        onClick = {
                            drafting = true
                            scope.launch {
                                try {
                                    val engine = (context.applicationContext as ChittiApp).extractionEngine
                                    reply = engine?.generateSmartReply(event) ?: "Got it, I will take care of it."
                                } catch (e: Exception) {
                                    toast(context, "Could not draft a reply")
                                } finally {
                                    drafting = false
                                }
                            }
                        }
                    )
                    SecondaryButton(text = "Calendar", icon = Icons.Rounded.Event, onClick = { addToCalendar(context, event) })
                    SecondaryButton(text = "Remind", icon = Icons.Rounded.Alarm, onClick = { launchReminder(context, event) })
                }
                val draft = reply
                if (drafting || draft != null) {
                    Spacer(Modifier.height(Space.s))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(concentricRadius(Inset.corner, Space.xs)))
                            .background(colors.surfaceRaised)
                            .padding(Space.m)
                    ) {
                        if (drafting) {
                            LatticeLoader(status = LatticeStatus.WORKING, label = "Drafting a reply", color = colors.accent, fontSize = 13)
                        } else if (draft != null) {
                            Text("Suggested reply", style = MaterialTheme.typography.labelMedium, color = colors.textMid)
                            Spacer(Modifier.height(Space.xs))
                            Text(draft, style = MaterialTheme.typography.bodyLarge, color = colors.textHigh)
                            Spacer(Modifier.height(Space.xs))
                            PrimaryButton(
                                text = "Send",
                                icon = Icons.AutoMirrored.Rounded.Send,
                                fill = false,
                                onClick = {
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        putExtra(Intent.EXTRA_TEXT, draft)
                                        type = "text/plain"
                                    }
                                    try {
                                        context.startActivity(Intent.createChooser(send, "Send reply").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    } catch (e: ActivityNotFoundException) {
                                        toast(context, "No app available to send the reply")
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The Reminders circle: an outlined ring that fills with a check when tapped. 48dp target. */
@Composable
private fun CompletionCircle(done: Boolean, ring: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    val colors = Chitti.colors
    val fill by animateColorAsState(if (done) colors.accentFill else androidx.compose.ui.graphics.Color.Transparent, Motion.snappy(), label = "circleFill")
    val check by animateFloatAsState(if (done) 1f else 0f, Motion.snappy(), label = "circleCheck")
    Box(
        modifier = Modifier
            .size(width = CommitmentTextInset, height = 48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Checkbox,
                onClick = onClick
            )
            .semantics {
                contentDescription = "Mark done"
                stateDescription = if (done) "Done" else "Not done"
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(fill)
                .border(1.5.dp, if (done) colors.accentFill else ring, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = colors.onAccent,
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer {
                        scaleX = check
                        scaleY = check
                        alpha = check.coerceIn(0f, 1f)
                    }
            )
        }
    }
}
