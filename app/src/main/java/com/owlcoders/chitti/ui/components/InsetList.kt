package com.owlcoders.chitti.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.owlcoders.chitti.ui.theme.Chitti

/*
 * Inset grouped lists, the structure of iOS Settings.
 *
 * Related rows share one rounded container; hierarchy comes from grouping, not from giving every
 * item its own card. Separators start where the text starts (16dp with no icon, 57dp past an
 * icon well), never at the container edge: a full-bleed rule would cut the group into pieces.
 * Groups that are not self-evident carry a one-sentence footer.
 *
 * Two ways in, same cells:
 *  - `LazyListScope.insetSection { ... }` inside a LazyColumn (each row is its own lazy item);
 *  - `InsetGroup { ... }` inside a plain Column.
 */
object Inset {
    /** Group corner. Rows inside are full-bleed within it, so they need no radius of their own. */
    val corner = 20.dp
    /** Minimum row height: Android's 48dp touch target, not iOS's 44. */
    val rowMinHeight = 48.dp
    /** Decorative icon well. Not a touch target on its own; the whole row is. */
    val iconWell = 29.dp
    /** Separator start when a row has no icon: where the text starts. */
    val textInset = 16.dp
    /** Separator start past an icon well: 16 + 29 + 12. */
    val iconInset = 57.dp
}

enum class HeaderStyle {
    /** Small uppercase caption, for Settings-style groups whose rows are the point. */
    Caption,
    /** A bold state or question ("Needs you", "What Chitti did"), for content sections. */
    Prominent
}

private data class CellPosition(val first: Boolean, val last: Boolean) {
    fun shape(): Shape = RoundedCornerShape(
        topStart = if (first) Inset.corner else 0.dp,
        topEnd = if (first) Inset.corner else 0.dp,
        bottomStart = if (last) Inset.corner else 0.dp,
        bottomEnd = if (last) Inset.corner else 0.dp
    )
}

/** The shape of the cell being drawn, for things that must clip to it (swipe backgrounds). */
val LocalInsetCellShape = staticCompositionLocalOf<Shape> { RoundedCornerShape(Inset.corner) }

class InsetSectionScope internal constructor() {
    internal class Cell(val key: Any, val separatorInset: Dp, val content: @Composable () -> Unit)
    internal val cells = mutableListOf<Cell>()

    /** One row. [separatorInset] is [Inset.iconInset] for rows with an icon well. */
    fun row(key: Any, separatorInset: Dp = Inset.textInset, content: @Composable () -> Unit) {
        cells += Cell(key, separatorInset, content)
    }

    fun <T> rows(
        items: List<T>,
        key: (T) -> Any,
        separatorInset: Dp = Inset.textInset,
        content: @Composable (T) -> Unit
    ) {
        items.forEach { item -> row(key(item), separatorInset) { content(item) } }
    }
}

/** A group inside a LazyColumn. Header, rows and footer are separate lazy items. */
fun LazyListScope.insetSection(
    key: String,
    header: String? = null,
    headerStyle: HeaderStyle = HeaderStyle.Caption,
    headerAction: Pair<String, () -> Unit>? = null,
    footer: String? = null,
    content: InsetSectionScope.() -> Unit
) {
    val scope = InsetSectionScope().apply(content)
    if (header != null) {
        item(key = "$key:header") { SectionHeader(header, headerStyle, headerAction) }
    } else {
        item(key = "$key:gap") { Spacer(Modifier.height(Space.l)) }
    }
    scope.cells.forEachIndexed { index, cell ->
        val position = CellPosition(first = index == 0, last = index == scope.cells.lastIndex)
        item(key = "$key:${cell.key}") {
            InsetCell(position, cell.separatorInset, cell.content)
        }
    }
    if (footer != null) {
        item(key = "$key:footer") { SectionFooter(footer) }
    }
}

/** The same group for a plain Column. */
@Composable
fun InsetGroup(
    modifier: Modifier = Modifier,
    header: String? = null,
    headerStyle: HeaderStyle = HeaderStyle.Caption,
    footer: String? = null,
    content: InsetSectionScope.() -> Unit
) {
    val scope = InsetSectionScope().apply(content)
    Column(modifier = modifier.fillMaxWidth()) {
        if (header != null) SectionHeader(header, headerStyle, null) else Spacer(Modifier.height(Space.l))
        scope.cells.forEachIndexed { index, cell ->
            InsetCell(CellPosition(index == 0, index == scope.cells.lastIndex), cell.separatorInset, cell.content)
        }
        if (footer != null) SectionFooter(footer)
    }
}

@Composable
private fun InsetCell(position: CellPosition, separatorInset: Dp, content: @Composable () -> Unit) {
    val colors = Chitti.colors
    val shape = position.shape()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter)
            .clip(shape)
            .background(colors.surface)
    ) {
        CompositionLocalProvider(LocalInsetCellShape provides shape) { content() }
        if (!position.first) {
            // Drawn over the cell's top edge, starting at the text.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = separatorInset)
                    .height(0.5.dp)
                    .background(colors.separator)
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String, style: HeaderStyle, action: Pair<String, () -> Unit>?) {
    val colors = Chitti.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter)
            .padding(
                start = if (style == HeaderStyle.Caption) Inset.textInset else 0.dp,
                top = if (style == HeaderStyle.Prominent) Space.xxl else Space.xl,
                bottom = Space.s
            ),
        verticalAlignment = Alignment.Bottom
    ) {
        if (style == HeaderStyle.Prominent) {
            Text(
                text,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textHigh,
                modifier = Modifier.weight(1f).semantics { heading() }
            )
        } else {
            Text(
                text.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = colors.textMid,
                modifier = Modifier.weight(1f).semantics { heading() }
            )
        }
        if (action != null) {
            LinkButton(text = action.first, onClick = action.second)
        }
    }
}

@Composable
private fun SectionFooter(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = Chitti.colors.textMid,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter)
            .padding(start = Inset.textInset, end = Inset.textInset, top = Space.s)
    )
}

/**
 * A row. Label on the left, current answer ([value]) right-aligned on the same line, so it reads
 * label -> answer. Rows that navigate show a chevron; destructive rows are red and centred.
 * Pressing highlights the whole row on touch-down.
 */
@Composable
fun InsetRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = Chitti.colors.accent,
    value: String? = null,
    valueColor: Color = Chitti.colors.textMid,
    chevron: Boolean = false,
    destructive: Boolean = false,
    enabled: Boolean = true,
    subtitleLines: Int = 2,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    val colors = Chitti.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val highlight by animateColorAsState(
        if (pressed && onClick != null) colors.fill else Color.Transparent,
        // Instant on press, a soft release: the highlight must never lag the finger.
        if (pressed) Motion.snappy() else Motion.standard(),
        label = "rowHighlight"
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Inset.rowMinHeight)
            .background(highlight)
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick
                ) else Modifier
            )
            .padding(horizontal = Inset.textInset, vertical = if (subtitle != null) 10.dp else Space.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (destructive && icon == null) Arrangement.Center else Arrangement.Start
    ) {
        when {
            leading != null -> {
                leading()
                Spacer(Modifier.width(Space.m))
            }
            icon != null -> {
                IconWell(icon, iconTint)
                Spacer(Modifier.width(Space.m))
            }
        }
        val titleColor = when {
            !enabled -> colors.textLow
            destructive -> colors.danger
            else -> colors.textHigh
        }
        if (destructive && icon == null && value == null && trailing == null) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor, textAlign = TextAlign.Center)
        } else {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMid,
                        maxLines = subtitleLines,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (value != null) {
                Spacer(Modifier.width(Space.s))
                Text(
                    value,
                    style = MaterialTheme.typography.bodyLarge,
                    color = valueColor,
                    maxLines = 1,
                    textAlign = TextAlign.End
                )
            }
            if (trailing != null) {
                Spacer(Modifier.width(Space.s))
                trailing()
            }
            if (chevron) {
                Spacer(Modifier.width(Space.xs))
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.textLow,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/** A rounded square holding a tinted glyph. Decoration inside a labelled row. */
@Composable
fun IconWell(icon: ImageVector, tint: Color, size: Dp = Inset.iconWell) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.24f))
            .background(tint.copy(alpha = if (Chitti.colors.isDark) 0.22f else 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.62f))
    }
}
