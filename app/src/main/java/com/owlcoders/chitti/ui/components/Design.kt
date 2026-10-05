package com.owlcoders.chitti.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.owlcoders.chitti.ui.theme.Chitti
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/*
 * The component kit. Screens are built from these and from InsetList / LargeTitleScaffold, so
 * spacing, radii, type and colour stay identical everywhere. If a screen needs something that is
 * not here, it is added here, not hand-rolled in the screen.
 */

/** 4pt spacing scale. */
object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    /** Side margin of every screen. */
    val gutter = 20.dp
}

/** Quiet empty state: glyph, one line of title, one line of guidance. No motion. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier
) {
    val colors = Chitti.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.xxxl, vertical = Space.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = colors.textLow, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(Space.m))
        Text(title, style = MaterialTheme.typography.titleLarge, color = colors.textHigh, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.xs))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = colors.textMid, textAlign = TextAlign.Center)
    }
}

// ---------------------------------------------------------------- Buttons

/** Capsule button. Dims and shrinks on touch-down, so the press is felt before the release. */
@Composable
private fun CapsuleButton(
    text: String,
    onClick: () -> Unit,
    container: Color,
    contentColor: Color,
    modifier: Modifier,
    enabled: Boolean,
    icon: ImageVector?,
    fill: Boolean,
    large: Boolean
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val alpha = when {
        !enabled -> 0.4f
        pressed -> 0.75f
        else -> 1f
    }
    Row(
        modifier = modifier
            .then(if (fill) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = if (large) 50.dp else 48.dp)
            .pressScale(interaction, pressed = 0.97f)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = if (large) 0.dp else 6.dp)
            .clip(CircleShape)
            .background(container.copy(alpha = container.alpha * alpha))
            .padding(horizontal = if (large) Space.xl else Space.l)
            .heightIn(min = if (large) 50.dp else 36.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = contentColor.copy(alpha = alpha), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Space.s))
        }
        Text(
            text,
            style = if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelLarge,
            color = contentColor.copy(alpha = alpha),
            maxLines = 1
        )
    }
}

/** The one filled accent action on a screen. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    fill: Boolean = true
) = CapsuleButton(text, onClick, Chitti.colors.accentFill, Chitti.colors.onAccent, modifier, enabled, icon, fill, large = fill)

/** Tinted, not filled: accent label on a quiet fill. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    fill: Boolean = false
) = CapsuleButton(text, onClick, Chitti.colors.fill, Chitti.colors.accent, modifier, enabled, icon, fill, large = fill)

@Composable
fun DangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    fill: Boolean = false
) = CapsuleButton(text, onClick, Chitti.colors.danger.copy(alpha = 0.14f), Chitti.colors.danger, modifier, enabled, icon, fill, large = fill)

/** Plain accent text, for "See all", "Save", "Allow". Keeps a 48dp touch target. */
@Composable
fun LinkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Chitti.colors.accent,
    style: TextStyle = MaterialTheme.typography.bodyLarge
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.xs),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = style,
            color = if (!enabled) Chitti.colors.textLow else color.copy(alpha = if (pressed) 0.5f else 1f),
            maxLines = 1
        )
    }
}

/**
 * A glyph button for a bar. 48dp target around a 24dp glyph; the glyph dims on press. Always
 * labelled: an icon that is the whole control needs words for TalkBack.
 */
@Composable
fun BarIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Chitti.colors.accent
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint.copy(alpha = if (pressed) 0.5f else 1f), modifier = Modifier.size(24.dp))
    }
}

/** The user's initial in a circle; stands in for a photo, which Chitti does not have. */
@Composable
fun Avatar(
    initial: String?,
    size: Dp = 32.dp,
    fallback: ImageVector? = null
) {
    val colors = Chitti.colors
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.accentWash),
        contentAlignment = Alignment.Center
    ) {
        if (!initial.isNullOrBlank()) {
            Text(
                initial.take(1).uppercase(),
                style = MaterialTheme.typography.titleSmall.copy(fontSize = MaterialTheme.typography.titleSmall.fontSize * (size.value / 32f)),
                color = colors.accent
            )
        } else if (fallback != null) {
            Icon(fallback, contentDescription = null, tint = colors.accent, modifier = Modifier.size(size * 0.56f))
        }
    }
}

/** Small status badge: tinted wash, tinted label, capsule. */
@Composable
fun StatusPill(
    text: String,
    tint: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(tint.copy(alpha = if (Chitti.colors.isDark) 0.2f else 0.12f))
            .padding(horizontal = Space.s, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = tint, fontWeight = FontWeight.SemiBold)
    }
}

/** A dot, for urgency and status marks next to text. */
@Composable
fun Dot(color: Color, size: Dp = 8.dp, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(size).clip(CircleShape).background(color))
}

/**
 * Suggestion capsule for the content layer (opaque; glass is for controls that float). Used for
 * "do this now" affordances so they all look and respond the same way.
 */
@Composable
fun ChipButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color = Chitti.colors.accent
) {
    val colors = Chitti.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bg by animateColorAsState(if (pressed) colors.fill else colors.surface, Motion.snappy(), label = "chip")
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .pressScale(interaction, pressed = 0.96f)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp)
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = Space.l, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Space.s))
        }
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.textHigh, maxLines = 1)
    }
}

/**
 * Segmented control. Tap a segment, or grab the thumb and drag it: it tracks the finger 1:1,
 * rubber-bands past either end, ticks as it crosses each segment, and on release lands on the
 * segment its momentum is heading for, carrying the finger's velocity into the settle.
 * Capsule track; the thumb's radius is concentric with it.
 */
@Composable
fun SegmentedTabs(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = Chitti.colors
    val trackHeight = 36.dp
    val thumbInset = 3.dp
    val thumbShape = RoundedCornerShape(concentricRadius(trackHeight / 2, thumbInset))
    var widthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val count = options.size.coerceAtLeast(1)
    val segmentPx = widthPx.toFloat() / count
    val thumbX = remember { Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }
    var measured by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val select by rememberUpdatedState(onSelect)
    val selectedNow by rememberUpdatedState(selectedIndex)

    LaunchedEffect(selectedIndex, segmentPx) {
        if (segmentPx <= 0f || dragging) return@LaunchedEffect
        val target = segmentPx * selectedIndex
        if (!measured) {
            thumbX.snapTo(target)
            measured = true
        } else {
            thumbX.animateTo(target, Motion.standard())
        }
    }

    val hovered = if (segmentPx > 0f) ((thumbX.value + segmentPx / 2f) / segmentPx).toInt().coerceIn(0, count - 1) else selectedIndex
    val activeIndex = if (dragging) hovered else selectedIndex

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(trackHeight)
            .clip(CircleShape)
            .background(colors.fill)
            .onSizeChanged { widthPx = it.width }
            .pointerInput(count) {
                val tracker = VelocityTracker()
                var raw = 0f
                var lastHover = -1
                fun seg() = size.width.toFloat() / count
                detectHorizontalDragGestures(
                    onDragStart = { down ->
                        dragging = down.x >= thumbX.value && down.x <= thumbX.value + seg()
                        if (dragging) {
                            tracker.resetTracking()
                            raw = thumbX.value
                            lastHover = ((raw + seg() / 2f) / seg()).toInt()
                            scope.launch { thumbX.stop() }
                        }
                    },
                    onHorizontalDrag = { change, delta ->
                        if (dragging) {
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val maxX = seg() * (count - 1)
                            raw += delta
                            val visual = when {
                                raw < 0f -> -rubberBand(-raw, seg())
                                raw > maxX -> maxX + rubberBand(raw - maxX, seg())
                                else -> raw
                            }
                            scope.launch { thumbX.snapTo(visual) }
                            val hover = ((visual + seg() / 2f) / seg()).toInt().coerceIn(0, count - 1)
                            if (hover != lastHover) {
                                lastHover = hover
                                haptics.tick()
                            }
                        }
                    },
                    onDragEnd = {
                        if (dragging) {
                            val v = tracker.calculateVelocity().x
                            val projected = thumbX.value + projectMomentum(v, decelerationRate = 0.99f)
                            val target = ((projected + seg() / 2f) / seg()).toInt().coerceIn(0, count - 1)
                            scope.launch { thumbX.animateTo(seg() * target, Motion.momentum(), initialVelocity = v) }
                            dragging = false
                            if (target != selectedNow) select(target)
                        }
                    },
                    onDragCancel = {
                        if (dragging) {
                            dragging = false
                            scope.launch { thumbX.animateTo(seg() * selectedNow, Motion.standard()) }
                        }
                    }
                )
            }
    ) {
        if (widthPx > 0) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(thumbX.value.roundToInt(), 0) }
                    .width(with(density) { segmentPx.toDp() })
                    .fillMaxHeight()
                    .padding(thumbInset)
                    .shadow(if (colors.isDark) 0.dp else 2.dp, thumbShape, ambientColor = colors.shadow, spotColor = colors.shadow)
                    .clip(thumbShape)
                    .background(if (colors.isDark) colors.surfaceRaised.copy(alpha = 1f) else colors.surface)
            )
        }
        Row(modifier = Modifier.fillMaxSize()) {
            options.forEachIndexed { index, option ->
                val selected = index == activeIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .semantics {
                            role = Role.Tab
                            this.selected = selected
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (index != selectedIndex) {
                                haptics.tick()
                                onSelect(index)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        option,
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textHigh,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * The single text-field style: a filled rounded field with no outline, like iOS. [label] sits above
 * it as a caption when the field is not inside a labelled row.
 */
@Composable
fun ChittiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    leadingIcon: ImageVector? = null,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
    containerColor: Color = Chitti.colors.surfaceRaised,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null
) {
    val colors = Chitti.colors
    Column(modifier = modifier.fillMaxWidth()) {
        if (label != null) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = colors.textMid,
                modifier = Modifier.padding(start = Space.xs, bottom = Space.xs)
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textHigh),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(onAny = { onImeAction() }),
            visualTransformation = visualTransformation,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(containerColor)
                        .padding(horizontal = Space.m, vertical = Space.m),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (leadingIcon != null) {
                        Icon(leadingIcon, contentDescription = null, tint = colors.textMid, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(Space.s))
                    }
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty()) {
                            Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = colors.textLow)
                        }
                        inner()
                    }
                    trailing?.invoke()
                }
            }
        )
    }
}

/** Search field with a clear button that appears once there is something to clear. */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val colors = Chitti.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textHigh),
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(colors.fill)
                    .padding(start = Space.s),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.textMid, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = colors.textMid)
                    }
                    inner()
                }
                if (value.isNotEmpty()) {
                    BarIconButton(
                        icon = Icons.Rounded.Cancel,
                        contentDescription = "Clear search",
                        onClick = { onValueChange("") },
                        tint = colors.textLow
                    )
                }
            }
        }
    )
}
