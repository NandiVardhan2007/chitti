package com.owlcoders.chitti.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Today
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.owlcoders.chitti.Routes
import com.owlcoders.chitti.ui.components.GlassSurface
import com.owlcoders.chitti.ui.components.Motion
import com.owlcoders.chitti.ui.components.Space
import com.owlcoders.chitti.ui.components.concentricRadius
import com.owlcoders.chitti.ui.components.pressScale
import com.owlcoders.chitti.ui.components.projectMomentum
import com.owlcoders.chitti.ui.components.rememberHaptics
import com.owlcoders.chitti.ui.components.rubberBand
import com.owlcoders.chitti.ui.theme.Chitti
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class Tab(val route: String, val label: String, val icon: ImageVector, val activeIcon: ImageVector) {
    Today(Routes.TODAY, "Today", Icons.Outlined.Today, Icons.Rounded.Today),
    Ask(Routes.ASK, "Ask", Icons.Outlined.ChatBubbleOutline, Icons.Rounded.ChatBubble),
    Library(Routes.LIBRARY, "Library", Icons.AutoMirrored.Outlined.LibraryBooks, Icons.AutoMirrored.Rounded.LibraryBooks)
}

val TabBarHeight = 64.dp
val TabBarBottomGap = 8.dp
private val BarInset = 5.dp
private val EdgeMargin = 20.dp

/**
 * The tab bar: a floating glass capsule, 20dp in from the screen edges, with content scrolling
 * behind it, and the microphone as its own round button beside it.
 *
 * The selection is one lens that moves between tabs rather than a highlight that blinks from one
 * to another. It can also be dragged: it follows the finger 1:1, rubber-bands past the ends,
 * ticks as it crosses a tab, and on release lands where the flick was heading.
 *
 * Tint marks only what matters: the selected tab, and the mic (the primary action). Everything
 * else is neutral.
 */
@Composable
fun ChittiTabBar(
    selected: Tab,
    onSelect: (Tab) -> Unit,
    onMic: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = Chitti.colors
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val tabs = Tab.entries
    val selectedIndex = tabs.indexOf(selected)
    val select by rememberUpdatedState(onSelect)
    val current by rememberUpdatedState(selectedIndex)

    var widthPx by remember { mutableIntStateOf(0) }
    val insetPx = with(density) { BarInset.toPx() }
    val slotPx = if (widthPx > 0) (widthPx - 2 * insetPx) / tabs.size else 0f
    val lensX = remember { Animatable(0f) }
    var placed by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }

    LaunchedEffect(selectedIndex, slotPx) {
        if (slotPx <= 0f || dragging) return@LaunchedEffect
        val target = slotPx * selectedIndex
        if (!placed) {
            lensX.snapTo(target)
            placed = true
        } else {
            lensX.animateTo(target, Motion.standard())
        }
    }
    val hovered = if (slotPx > 0f) ((lensX.value + slotPx / 2f) / slotPx).toInt().coerceIn(0, tabs.lastIndex) else selectedIndex
    val active = if (dragging) hovered else selectedIndex

    val shape = RoundedCornerShape(TabBarHeight / 2)
    val lensShape = RoundedCornerShape(concentricRadius(TabBarHeight / 2, BarInset))

    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = EdgeMargin, end = EdgeMargin, bottom = TabBarBottomGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassSurface(
            modifier = Modifier
                .weight(1f)
                .height(TabBarHeight)
                .onSizeChanged { widthPx = it.width },
            shape = shape,
            elevation = 12.dp
        ) {
            if (placed) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset((insetPx + lensX.value).roundToInt(), 0) }
                        .width(with(density) { slotPx.toDp() })
                        .fillMaxHeight()
                        .padding(vertical = BarInset)
                        .clip(lensShape)
                        .background(colors.accentWash)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = BarInset)
                    .pointerInput(tabs.size) {
                        val tracker = VelocityTracker()
                        var raw = 0f
                        var lastHover = -1
                        fun slot() = (size.width.toFloat()) / tabs.size
                        detectHorizontalDragGestures(
                            onDragStart = { down ->
                                dragging = true
                                tracker.resetTracking()
                                // The lens jumps under the finger, then follows it.
                                raw = (down.x - slot() / 2f).coerceIn(0f, slot() * tabs.lastIndex)
                                lastHover = ((raw + slot() / 2f) / slot()).toInt()
                                scope.launch { lensX.animateTo(raw, Motion.snappy()) }
                            },
                            onHorizontalDrag = { change, delta ->
                                change.consume()
                                tracker.addPosition(change.uptimeMillis, change.position)
                                val maxX = slot() * tabs.lastIndex
                                raw += delta
                                val visual = when {
                                    raw < 0f -> -rubberBand(-raw, slot())
                                    raw > maxX -> maxX + rubberBand(raw - maxX, slot())
                                    else -> raw
                                }
                                scope.launch { lensX.snapTo(visual) }
                                val hover = ((visual + slot() / 2f) / slot()).toInt().coerceIn(0, tabs.lastIndex)
                                if (hover != lastHover) {
                                    lastHover = hover
                                    haptics.tick()
                                }
                            },
                            onDragEnd = {
                                val v = tracker.calculateVelocity().x
                                val projected = lensX.value + projectMomentum(v, decelerationRate = 0.99f)
                                val target = ((projected + slot() / 2f) / slot()).toInt().coerceIn(0, tabs.lastIndex)
                                dragging = false
                                scope.launch { lensX.animateTo(slot() * target, Motion.momentum(), initialVelocity = v) }
                                if (target != current) select(tabs[target])
                            },
                            onDragCancel = {
                                dragging = false
                                scope.launch { lensX.animateTo(slot() * current, Motion.standard()) }
                            }
                        )
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, tab ->
                    TabItem(
                        tab = tab,
                        selected = index == active,
                        onClick = {
                            if (index != selectedIndex) haptics.tick()
                            onSelect(tab)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(Modifier.width(Space.m))

        // The primary action: the one solid accent in the bar. Opaque with a shadow, not glass.
        val micInteraction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .size(TabBarHeight)
                .pressScale(micInteraction, pressed = 0.9f)
                .shadow(12.dp, CircleShape, ambientColor = colors.shadow, spotColor = colors.shadow)
                .clip(CircleShape)
                .background(colors.accentFill)
                .clickable(interactionSource = micInteraction, indication = null, role = Role.Button) {
                    haptics.press()
                    onMic()
                }
                .semantics { contentDescription = "Talk to Chitti" },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Mic, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun TabItem(tab: Tab, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Chitti.colors
    val tint by animateColorAsState(if (selected) colors.accent else colors.textHigh, Motion.standard(), label = "tabTint")
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .semantics {
                role = Role.Tab
                this.selected = selected
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Filled when selected, outlined otherwise, so the state reads without colour.
        Icon(if (selected) tab.activeIcon else tab.icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(2.dp))
        Text(
            tab.label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}
