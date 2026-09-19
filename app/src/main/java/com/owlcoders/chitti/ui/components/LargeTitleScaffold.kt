package com.owlcoders.chitti.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.owlcoders.chitti.ui.theme.Chitti

/**
 * Space the floating chrome occupies at the bottom of the window (tab bar, its margin, the system
 * navigation inset). Screens pad their scrolling content by this so the last row can scroll clear
 * of the bar instead of hiding under it.
 */
val LocalBottomChrome = staticCompositionLocalOf { 0.dp }

/** Height of the navigation bar row under the status bar. */
private val NavBarHeight = 52.dp

/**
 * The large-title pattern.
 *
 * The large title is item 0 of the list, not a header above it, so it scrolls with the content
 * and the content scrolls under the nav bar. Everything about the collapse is a function of scroll
 * offset, not a threshold-triggered animation: drag halfway and it sits halfway.
 *
 *  - progress 0..1 = how much of the large-title block has scrolled away;
 *  - the large title fades out over 0.35..0.75 while it passes under the bar;
 *  - the inline title fades in over 0.75..1, so the two are never legible at the same time;
 *  - the bar's glass materialises over 0.25..1 on the same progress, and is fully absent at rest,
 *    because until something has scrolled under the bar there is nothing to separate.
 *
 * [actions] live in the bar at all times (they are controls, not content).
 * [bottomBar] (Ask's composer) floats above the tab bar; content is padded to clear it.
 */
@Composable
fun LargeTitleScaffold(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    subtitle: (@Composable () -> Unit)? = null,
    listState: LazyListState = rememberLazyListState(),
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: (@Composable () -> Unit)? = null,
    content: LazyListScope.() -> Unit
) {
    val colors = Chitti.colors
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomChrome = LocalBottomChrome.current
    var titleHeightPx by remember { mutableIntStateOf(1) }
    var bottomBarHeightPx by remember { mutableIntStateOf(0) }
    val bottomBarHeight: Dp = with(density) { bottomBarHeightPx.toDp() }

    val progress by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / titleHeightPx.toFloat()).coerceIn(0f, 1f)
        }
    }

    Box(modifier = modifier.fillMaxSize().background(colors.background)) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .glassBackdrop(),
            contentPadding = PaddingValues(
                top = statusTop + NavBarHeight,
                // The composer already sits above the chrome, so its height is the whole bottom clearance.
                bottom = (if (bottomBar != null) bottomBarHeight else bottomChrome) + Space.xxl
            )
        ) {
            item(key = "large-title") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { titleHeightPx = it.height.coerceAtLeast(1) }
                        .graphicsLayer { alpha = 1f - smoothStep(0.35f, 0.75f, progress) }
                        .padding(horizontal = Space.gutter)
                        .padding(bottom = Space.xs)
                ) {
                    if (eyebrow != null) {
                        Text(
                            eyebrow.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textMid
                        )
                    }
                    Text(
                        title,
                        style = MaterialTheme.typography.displayLarge,
                        color = colors.textHigh,
                        modifier = Modifier.semantics { heading() }
                    )
                    if (subtitle != null) subtitle()
                }
            }
            content()
        }

        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .height(statusTop + NavBarHeight),
            edge = GlassEdge.Bottom,
            progress = smoothStep(0.25f, 1f, progress)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = statusTop)
                    .height(NavBarHeight)
                    .padding(horizontal = Space.xs)
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textHigh,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 96.dp)
                        // The large title already announces the screen; this copy is visual only.
                        .clearAndSetSemantics { }
                        .graphicsLayer { alpha = smoothStep(0.75f, 1f, progress) }
                )
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions
                )
            }
        }

        if (bottomBar != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { bottomBarHeightPx = it.height }
            ) {
                bottomBar()
            }
        }
    }
}

/** Subtitle line under a large title: secondary text, same gutter. */
@Composable
fun LargeTitleSubtitle(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Chitti.colors.textMid)
}
