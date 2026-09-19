package com.owlcoders.chitti.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.owlcoders.chitti.automation.AssistantIntentDispatcher
import com.owlcoders.chitti.db.CapturedEvent
import com.owlcoders.chitti.db.entities.ChatHistoryEntity
import com.owlcoders.chitti.db.entities.Memory
import com.owlcoders.chitti.db.entities.NotificationEntity
import com.owlcoders.chitti.services.TtsEngine
import com.owlcoders.chitti.ui.components.BarIconButton
import com.owlcoders.chitti.ui.components.ChipButton
import com.owlcoders.chitti.ui.components.Inset
import com.owlcoders.chitti.ui.components.InsetRow
import com.owlcoders.chitti.ui.components.LargeTitleScaffold
import com.owlcoders.chitti.ui.components.LargeTitleSubtitle
import com.owlcoders.chitti.ui.components.LatticeLoader
import com.owlcoders.chitti.ui.components.LatticePatterns
import com.owlcoders.chitti.ui.components.LatticeStatus
import com.owlcoders.chitti.ui.components.LinkButton
import com.owlcoders.chitti.ui.components.LocalBottomChrome
import com.owlcoders.chitti.ui.components.Motion
import com.owlcoders.chitti.ui.components.Space
import com.owlcoders.chitti.ui.components.WordRevealText
import com.owlcoders.chitti.ui.components.insetSection
import com.owlcoders.chitti.ui.components.pressScale
import com.owlcoders.chitti.ui.components.rememberHaptics
import com.owlcoders.chitti.ui.components.rememberReducedMotion
import com.owlcoders.chitti.ui.theme.Chitti
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class ChatMessage(val text: String, val isUser: Boolean, val actionLabel: String? = null, val failed: Boolean = false)

private data class Suggestion(val label: String, val query: String)

/**
 * Three prompts drawn from what the user actually has, each phrased the way the dispatcher
 * understands it. Falls back to plain capabilities only when there is no data to draw on.
 */
private fun suggestionsFor(
    context: Context,
    events: List<CapturedEvent>,
    notifications: List<NotificationEntity>,
    memories: List<Memory>
): List<Suggestion> {
    val out = mutableListOf<Suggestion>()
    if (events.isNotEmpty()) out += Suggestion("What's pending?", "what's pending")
    memories.firstOrNull()?.let { m ->
        val subject = m.key.trim().removePrefix("my ").removePrefix("My ").lowercase()
        if (subject.isNotBlank()) out += Suggestion("What's my $subject?", "what is my $subject")
    }
    notifications.firstOrNull()?.let { n ->
        val app = appLabel(context, n.packageName)
        out += Suggestion("Open $app", "open $app")
    }
    val fallbacks = listOf(
        Suggestion("Remind me in 10 minutes", "remind me to check my tasks in 10 minutes"),
        Suggestion("What can you do?", "what can you do"),
        Suggestion("Turn on the flashlight", "turn on the flashlight")
    )
    for (f in fallbacks) if (out.size < 3) out += f
    return out.take(3)
}

private data class Capability(val label: String, val detail: String, val icon: ImageVector, val query: String)

private val Capabilities = listOf(
    Capability("Open an app", "\"Open WhatsApp\"", Icons.AutoMirrored.Rounded.OpenInNew, "open whatsapp"),
    Capability("Set a reminder", "\"Remind me to call mom in 30 minutes\"", Icons.Rounded.Alarm, "remind me to call mom in 30 minutes"),
    Capability("Check what's pending", "\"What's pending?\"", Icons.Rounded.TaskAlt, "what's pending"),
    Capability("Control the flashlight", "\"Turn on the flashlight\"", Icons.Rounded.FlashlightOn, "turn on the flashlight")
)

/**
 * Ask: talk or type to Chitti.
 *
 * Idle is never empty: three suggestions from the user's own data, then what Chitti can do. A
 * reply reads in word by word as it arrives. If it arrives within 300ms nothing else is shown;
 * only a slower reply earns a "thinking" indicator, so on-device speed is visible, not hidden.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AskScreen(
    events: List<CapturedEvent>,
    notifications: List<NotificationEntity>,
    chatHistory: List<ChatHistoryEntity>,
    memories: List<Memory>,
    dispatcher: AssistantIntentDispatcher,
    ttsEngine: TtsEngine,
    onSaveMessage: (ChatHistoryEntity) -> Unit,
    onStartVoice: () -> Unit
) {
    val colors = Chitti.colors
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()

    var messages by remember { mutableStateOf(chatHistory.map { ChatMessage(it.message, it.role == "user") }) }
    // History arrives from Room after first composition; take it once, and never over a
    // conversation the user has already started here.
    var hydrated by remember { mutableStateOf(chatHistory.isNotEmpty()) }
    LaunchedEffect(chatHistory) {
        if (!hydrated && chatHistory.isNotEmpty() && messages.isEmpty()) {
            messages = chatHistory.map { ChatMessage(it.message, it.role == "user") }
            hydrated = true
        }
    }

    var input by remember { mutableStateOf("") }
    var thinking by remember { mutableStateOf(false) }
    var showThinking by remember { mutableStateOf(false) }
    var freshIndex by remember { mutableIntStateOf(-1) }
    // Messages from this index on were sent in this session and may animate in; earlier ones are
    // history and are simply there.
    var liveFrom by remember { mutableIntStateOf(Int.MAX_VALUE) }

    // Only a reply slower than 300ms gets an indicator; a fast one simply appears.
    LaunchedEffect(thinking) {
        showThinking = false
        if (thinking) {
            delay(300)
            showThinking = true
        }
    }

    LaunchedEffect(messages.size, showThinking) {
        val last = listState.layoutInfo.totalItemsCount - 1
        if (messages.isNotEmpty() && last >= 0) listState.animateScrollToItem(last)
    }

    fun send(text: String) {
        val query = text.trim()
        if (query.isEmpty() || thinking) return
        input = ""
        hydrated = true
        liveFrom = minOf(liveFrom, messages.size)
        messages = messages + ChatMessage(query, isUser = true)
        onSaveMessage(ChatHistoryEntity(role = "user", message = query))
        thinking = true
        scope.launch {
            try {
                val response = dispatcher.processQuery(query, events, memories, shouldSpeak = true)
                freshIndex = messages.size
                messages = messages + ChatMessage(response.message, false, response.actionLabel, failed = !response.actionSuccess)
                onSaveMessage(ChatHistoryEntity(role = "assistant", message = response.message))
                if (response.actionLabel != null) {
                    if (response.actionSuccess) haptics.confirm() else haptics.reject()
                }
            } catch (e: Exception) {
                messages = messages + ChatMessage("Something went wrong: ${e.message ?: "unknown error"}", false, failed = true)
                haptics.reject()
            } finally {
                thinking = false
            }
        }
    }

    val idle = messages.none { it.isUser }
    val suggestions = remember(events.isEmpty(), notifications.firstOrNull()?.packageName, memories.firstOrNull()?.key) {
        suggestionsFor(context, events, notifications, memories)
    }

    LargeTitleScaffold(
        title = "Ask",
        subtitle = { LargeTitleSubtitle("Runs on this phone. Nothing leaves it.") },
        listState = listState,
        actions = {
            if (ttsEngine.isSpeaking) {
                BarIconButton(icon = Icons.AutoMirrored.Rounded.VolumeOff, contentDescription = "Stop speaking", onClick = { ttsEngine.stop() })
            }
        },
        bottomBar = {
            Composer(
                value = input,
                onValueChange = { input = it },
                canSend = input.isNotBlank() && !thinking,
                onSend = {
                    haptics.press()
                    send(input)
                },
                onMic = onStartVoice
            )
        }
    ) {
        if (idle) {
            item(key = "suggestions") {
                FlowRow(
                    modifier = Modifier.padding(horizontal = Space.gutter).padding(top = Space.l),
                    horizontalArrangement = Arrangement.spacedBy(Space.s)
                ) {
                    suggestions.forEach { s -> ChipButton(label = s.label, onClick = { send(s.query) }) }
                }
            }
            insetSection(key = "can", header = "Chitti can", footer = "Say it with the mic, or type it below.") {
                rows(Capabilities, key = { it.label }, separatorInset = Inset.iconInset) { c ->
                    InsetRow(
                        title = c.label,
                        subtitle = c.detail,
                        icon = c.icon,
                        iconTint = colors.accent,
                        onClick = { send(c.query) }
                    )
                }
            }
        }

        itemsIndexed(messages, key = { index, _ -> "msg-$index" }) { index, msg ->
            Bubble(
                msg = msg,
                animateIn = index >= liveFrom,
                reveal = index == freshIndex,
                onRevealed = { if (freshIndex == index) freshIndex = -1 },
                onSpeak = { ttsEngine.speak(msg.text) },
                modifier = Modifier.animateItem(placementSpec = Motion.standard())
            )
        }

        if (showThinking) {
            item(key = "thinking") {
                LatticeLoader(
                    status = LatticeStatus.WORKING,
                    label = "Thinking",
                    pattern = LatticePatterns.Orbit,
                    color = colors.accent,
                    modifier = Modifier.padding(horizontal = Space.gutter + Space.xs, vertical = Space.m)
                )
            }
        }
    }
}

/**
 * The composer: one capsule holding the field, the mic and send. It floats above the tab bar and
 * rides up with the keyboard. Opaque content-layer material with a shadow, not glass.
 */
@Composable
private fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    canSend: Boolean,
    onSend: () -> Unit,
    onMic: () -> Unit
) {
    val colors = Chitti.colors
    val chrome = LocalBottomChrome.current
    val sendFill by animateColorAsState(if (canSend) colors.accentFill else colors.fill, Motion.standard(), label = "sendFill")
    val sendGlyph by animateColorAsState(if (canSend) colors.onAccent else colors.textLow, Motion.standard(), label = "sendGlyph")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets(bottom = chrome)))
            .padding(horizontal = Space.gutter, vertical = Space.s)
            .shadow(8.dp, CircleShape, ambientColor = colors.shadow, spotColor = colors.shadow)
            .clip(CircleShape)
            .background(colors.surface)
            .padding(start = Space.l, end = Space.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).padding(vertical = 14.dp),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textHigh),
            cursorBrush = SolidColor(colors.accent),
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text("Ask Chitti", style = MaterialTheme.typography.bodyLarge, color = colors.textMid)
                    inner()
                }
            }
        )
        BarIconButton(icon = Icons.Rounded.Mic, contentDescription = "Talk to Chitti", onClick = onMic, tint = colors.textMid)
        val interaction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .size(48.dp)
                .pressScale(interaction, 0.9f)
                .clickable(interactionSource = interaction, indication = null, enabled = canSend, role = Role.Button, onClick = onSend)
                .semantics { contentDescription = "Send" },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(sendFill),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.ArrowUpward, contentDescription = null, tint = sendGlyph, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/**
 * A message. Yours in the accent, on the right; Chitti's on a surface, on the left. Each grows out
 * of its own corner. A fresh reply reads in word by word.
 */
@Composable
private fun Bubble(
    msg: ChatMessage,
    animateIn: Boolean,
    reveal: Boolean,
    onRevealed: () -> Unit,
    onSpeak: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = Chitti.colors
    val reduce = rememberReducedMotion()
    val shown = remember { MutableTransitionState(reduce || !animateIn).apply { targetState = true } }
    val origin = if (msg.isUser) TransformOrigin(1f, 1f) else TransformOrigin(0f, 1f)
    val big = 20.dp
    val tail = 6.dp
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter, vertical = Space.xs),
        contentAlignment = if (msg.isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        AnimatedVisibility(
            visibleState = shown,
            enter = fadeIn(Motion.fade(160)) + scaleIn(Motion.standard(), initialScale = 0.9f, transformOrigin = origin)
        ) {
            Column {
                Column(
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .clip(RoundedCornerShape(big, big, if (msg.isUser) tail else big, if (msg.isUser) big else tail))
                        .background(if (msg.isUser) colors.accentFill else colors.surface)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    if (!msg.isUser && msg.actionLabel != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = Space.xs)) {
                            val tint = if (msg.failed) colors.warning else colors.success
                            Icon(if (msg.failed) Icons.Rounded.Info else Icons.Rounded.CheckCircle, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(msg.actionLabel, style = MaterialTheme.typography.labelMedium, color = tint)
                        }
                    }
                    WordRevealText(
                        text = msg.text,
                        animate = reveal && !msg.isUser,
                        onRevealed = onRevealed,
                        color = if (msg.isUser) colors.onAccent else colors.textHigh,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                // A message action sits under the bubble, not inside it, so the bubble is only words.
                if (!msg.isUser) {
                    Row(
                        modifier = Modifier.padding(start = Space.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
                        LinkButton(text = "Listen", onClick = onSpeak, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
