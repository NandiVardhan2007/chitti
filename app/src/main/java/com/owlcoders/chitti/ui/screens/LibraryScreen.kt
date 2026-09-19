package com.owlcoders.chitti.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.owlcoders.chitti.db.entities.Memory
import com.owlcoders.chitti.db.entities.NotificationEntity
import com.owlcoders.chitti.ui.components.AppMenuButton
import com.owlcoders.chitti.ui.components.BarIconButton
import com.owlcoders.chitti.ui.components.EmptyState
import com.owlcoders.chitti.ui.components.HeaderStyle
import com.owlcoders.chitti.ui.components.Inset
import com.owlcoders.chitti.ui.components.LargeTitleScaffold
import com.owlcoders.chitti.ui.components.LargeTitleSubtitle
import com.owlcoders.chitti.ui.components.SearchField
import com.owlcoders.chitti.ui.components.SegmentedTabs
import com.owlcoders.chitti.ui.components.Space
import com.owlcoders.chitti.ui.components.insetSection
import com.owlcoders.chitti.ui.components.skeletonSection

private const val PreviewCount = 4

private fun NotificationEntity.matches(q: String) =
    rawTitle.contains(q, ignoreCase = true) || rawText.contains(q, ignoreCase = true) || packageName.contains(q, ignoreCase = true)

private fun Memory.matches(q: String) =
    key.contains(q, ignoreCase = true) || value.contains(q, ignoreCase = true) || category.contains(q, ignoreCase = true)

/**
 * Library: everything Chitti has gathered, in two groups — what it found in your notifications,
 * and what it knows about you. One search covers both. Each group previews the latest few and
 * "See all" pushes the full list.
 */
@Composable
fun LibraryScreen(
    notifications: List<NotificationEntity>,
    memories: List<Memory>,
    categories: List<String>,
    actions: LibraryActions,
    onOpenFound: () -> Unit,
    onOpenKnows: () -> Unit,
    loading: Boolean = false
) {
    var query by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<Memory?>(null) }
    var adding by remember { mutableStateOf(false) }
    val q = query.trim()
    val found = if (q.isEmpty()) notifications else notifications.filter { it.matches(q) }
    val known = if (q.isEmpty()) memories else memories.filter { it.matches(q) }
    val newCount = notifications.count { !it.processed }

    LargeTitleScaffold(
        title = "Library",
        subtitle = {
            LargeTitleSubtitle(
                if (newCount > 0) "$newCount you haven't dealt with" else "Everything Chitti has gathered"
            )
        },
        actions = {
            BarIconButton(icon = Icons.Rounded.Add, contentDescription = "Add something Chitti should know", onClick = { adding = true })
            AppMenuButton()
        }
    ) {
        item(key = "search") {
            SearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search",
                modifier = Modifier.padding(horizontal = Space.gutter).padding(top = Space.m)
            )
        }

        if (loading) {
            skeletonSection("found-loading", rows = 3)
            skeletonSection("knows-loading", rows = 2, withIcon = false)
            return@LargeTitleScaffold
        }
        if (q.isNotEmpty() && found.isEmpty() && known.isEmpty()) {
            item(key = "no-results") {
                EmptyState(icon = Icons.Rounded.Search, title = "No results", message = "Nothing Chitti found or knows matches \"$q\".")
            }
        }

        if (found.isNotEmpty() || q.isEmpty()) {
            val shown = if (q.isEmpty()) found.take(PreviewCount) else found
            insetSection(
                key = "found",
                header = "What Chitti found",
                headerStyle = HeaderStyle.Prominent,
                headerAction = if (q.isEmpty() && notifications.size > PreviewCount) "See all" to onOpenFound else null,
                footer = if (notifications.isEmpty()) null else if (q.isEmpty()) "Swipe right when you've dealt with one, left to delete it." else null
            ) {
                if (shown.isEmpty()) {
                    row("none") {
                        EmptyState(
                            icon = Icons.Rounded.Inbox,
                            title = "Nothing yet",
                            message = "Notifications from your apps collect here once Chitti has access."
                        )
                    }
                } else {
                    rows(shown, key = { it.id }, separatorInset = Inset.iconInset) { NotificationRow(it, actions) }
                }
            }
        }

        if (known.isNotEmpty() || q.isEmpty()) {
            val shown = if (q.isEmpty()) known.take(PreviewCount) else known
            insetSection(
                key = "knows",
                header = "What Chitti knows",
                headerStyle = HeaderStyle.Prominent,
                headerAction = if (q.isEmpty() && memories.size > PreviewCount) "See all" to onOpenKnows else null,
                footer = if (memories.isEmpty()) null else "Chitti uses these when you ask about yourself."
            ) {
                if (shown.isEmpty()) {
                    row("none") {
                        EmptyState(
                            icon = Icons.Rounded.Lightbulb,
                            title = "Nothing yet",
                            message = "Tap + to tell Chitti your branch, your college, the people you work with."
                        )
                    }
                } else {
                    rows(shown, key = { it.id }) { MemoryRow(it, actions, onEdit = { m -> editing = m }) }
                }
            }
        }
    }

    if (adding || editing != null) {
        MemoryEditor(
            initial = editing,
            knownCategories = categories,
            onDismiss = { adding = false; editing = null },
            onSave = { key, value, category ->
                actions.onSaveMemory(editing, key, value, category)
                adding = false
                editing = null
            }
        )
    }
}

private val FoundFilters = listOf("All", "New", "Handled")

/** Every notification Chitti found, grouped by day, filterable by whether you've dealt with it. */
@Composable
fun FoundScreen(notifications: List<NotificationEntity>, actions: LibraryActions, loading: Boolean = false) {
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val shown = when (filter) {
        1 -> notifications.filter { !it.processed }
        2 -> notifications.filter { it.processed }
        else -> notifications
    }
    val newCount = notifications.count { !it.processed }
    val byDay = shown.groupBy { dayBucket(it.postTime) }

    LargeTitleScaffold(
        title = "What Chitti found",
        subtitle = { LargeTitleSubtitle(if (newCount == 0) "You've dealt with everything" else "$newCount you haven't dealt with") }
    ) {
        item(key = "filter") {
            SegmentedTabs(
                options = FoundFilters,
                selectedIndex = filter,
                onSelect = { filter = it },
                modifier = Modifier.padding(horizontal = Space.gutter).padding(top = Space.m)
            )
        }
        if (loading) {
            skeletonSection("found-all-loading", rows = 5)
            return@LargeTitleScaffold
        }
        if (shown.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    icon = Icons.Rounded.Inbox,
                    title = if (notifications.isEmpty()) "Nothing yet" else "Nothing here",
                    message = when (filter) {
                        1 -> "You've dealt with every notification Chitti found."
                        2 -> "Notifications you mark as handled collect here."
                        else -> "Notifications from your apps collect here once Chitti has access."
                    }
                )
            }
        }
        byDay.forEach { (day, items) ->
            insetSection(key = "found-$day", header = day) {
                rows(items, key = { it.id }, separatorInset = Inset.iconInset) { NotificationRow(it, actions, removesWhenHandled = filter == 1) }
            }
        }
    }
}

/** Everything Chitti knows, grouped by kind. */
@Composable
fun KnowsScreen(memories: List<Memory>, categories: List<String>, actions: LibraryActions, loading: Boolean = false) {
    var kind by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Memory?>(null) }
    var adding by remember { mutableStateOf(false) }
    val shown = if (kind == null) memories else memories.filter { it.category == kind }
    val byKind = shown.groupBy { it.category }.toSortedMap()

    LargeTitleScaffold(
        title = "What Chitti knows",
        subtitle = { LargeTitleSubtitle(if (memories.isEmpty()) "Nothing yet" else "${memories.size} things about you") },
        actions = {
            BarIconButton(icon = Icons.Rounded.Add, contentDescription = "Add something Chitti should know", onClick = { adding = true })
        }
    ) {
        if (categories.size > 1) {
            item(key = "kinds") {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = Space.gutter),
                    horizontalArrangement = Arrangement.spacedBy(Space.s)
                ) {
                    ChoiceCapsule("All", selected = kind == null) { kind = null }
                    categories.forEach { c -> ChoiceCapsule(c.titleCase(), selected = kind == c) { kind = c } }
                }
            }
        }
        if (loading) {
            skeletonSection("knows-all-loading", rows = 4, withIcon = false)
            return@LargeTitleScaffold
        }
        if (shown.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    icon = Icons.Rounded.Lightbulb,
                    title = "Nothing yet",
                    message = "Tap + to tell Chitti your branch, your college, the people you work with."
                )
            }
        }
        byKind.forEach { (category, items) ->
            insetSection(key = "kind-$category", header = category.titleCase()) {
                rows(items, key = { it.id }) { MemoryRow(it, actions, onEdit = { m -> editing = m }, showCategory = false) }
            }
        }
    }

    if (adding || editing != null) {
        MemoryEditor(
            initial = editing,
            knownCategories = categories,
            onDismiss = { adding = false; editing = null },
            onSave = { key, value, category ->
                actions.onSaveMemory(editing, key, value, category)
                adding = false
                editing = null
            }
        )
    }
}
