package com.roy93group.notes.ui.note

import com.roy93group.notes.model.PrefsManager
import com.roy93group.notes.model.entity.Label
import com.roy93group.notes.model.entity.ListNoteItem
import com.roy93group.notes.model.entity.Note
import com.roy93group.notes.model.entity.NoteType
import com.roy93group.notes.ui.note.adapter.NoteItemList
import com.roy93group.notes.ui.note.adapter.NoteItemText
import com.roy93group.notes.ui.note.adapter.NoteListLayoutMode
import javax.inject.Inject

/**
 * Class used to create note items for the note preview list.
 * Handles which items are visible, search highlights, ellipsis, etc.
 */
class NoteItemFactory @Inject constructor(
    private val prefs: PrefsManager,
) {

    /**
     * Search query used to set highlights on created note.
     * Set to `null` to highlight nothing.
     */
    var query: String? = null

    /**
     * Whether to append note ID to title, for debugging.
     */
    var appendIdToTitle: Boolean = false

    fun createItem(
        note: Note,
        labels: List<Label>,
        checked: Boolean,
        showMarkAsDone: Boolean = false,
    ) = when (note.type) {
        NoteType.TEXT -> createTextItem(
            note = note,
            labels = labels,
            checked = checked,
            showMarkAsDone = showMarkAsDone
        )

        NoteType.LIST -> createListItem(
            note = note,
            labels = labels,
            checked = checked,
            showMarkAsDone = showMarkAsDone
        )
    }

    private fun createTextItem(
        note: Note,
        labels: List<Label>,
        checked: Boolean,
        showMarkAsDone: Boolean = false,
    ): NoteItemText {
        val title = createTitle(note)

        val contentTrim = note.content.trim()
        val content = if (query == null) {
            Highlighted(contentTrim)
        } else {
            val ellipsisThreshold = getStartEllipsisThreshold(START_ELLIPSIS_THRESHOLD_CONTENT) *
                    maxOf(0, prefs.getMaximumPreviewLines(NoteType.TEXT) - 1) + START_ELLIPSIS_THRESHOLD_CONTENT_FIRST
            val highlights = HighlightHelper.findHighlightsInString(
                text = contentTrim,
                query = query!!,
                max = MAX_HIGHLIGHTS_IN_TEXT
            )
            HighlightHelper.getStartEllipsizedText(
                contentTrim, highlights,
                ellipsisThreshold, START_ELLIPSIS_DISTANCE_CONTENT
            )
        }

        return NoteItemText(
            id = note.id,
            note = note,
            labels = labels,
            checked = checked,
            title = title,
            content = content,
            showMarkAsDone = showMarkAsDone
        )
    }

    private fun createListItem(
        note: Note,
        labels: List<Label>,
        checked: Boolean,
        showMarkAsDone: Boolean,
    ): NoteItemList {
        // - Items are shown in a determined order:
        //     - If not moving checked to the bottom, items are shown in order.
        //     - If moving checked to the bottom, unchecked items are shown first.
        // - If there are highlights, some items are hidden to show as many highlights as possible, keeping order.
        // - There is a maximum and a minimum of items that can be shown.

        val items = note.listItems  // already trimmed
        val noteItemCounts = items.size

        if (prefs.moveCheckedToBottom) {
            items.sortBy { it.checked }
        }

        val highlights = getListItemHighlights(items)
        val itemsCount = getListItemCount(items, highlights)

        var onlyCheckedInOverflow = true
        if (query != null) {
            // Remove items so that as many highlights are visible as possible.
            // Keep first items in case there are less highlights than items shown.
            val highlightIndices = highlights.mapIndexedNotNullTo(
                mutableListOf()
            ) { i, h -> i.takeIf { h.isNotEmpty() } }
            highlightIndices += Int.MAX_VALUE
            var currHighlight = 0
            var delta = 0
            var i = 0
            while (i < items.size) {
                val highlightsLeft = highlightIndices.size - currHighlight - 1
                val itemSlotsLeft = itemsCount - i
                if (i == highlightIndices[currHighlight] - delta) {
                    currHighlight++
                } else if (itemSlotsLeft <= highlightsLeft) {
                    highlights.removeAt(i)
                    val item = items.removeAt(i)
                    onlyCheckedInOverflow = onlyCheckedInOverflow && item.checked
                    delta++
                    i--
                }
                i++
            }
        }
        val hiddenItems = items.subList(itemsCount, items.size)
        onlyCheckedInOverflow = onlyCheckedInOverflow && hiddenItems.all { it.checked }
        hiddenItems.clear()

        val overflowCount = noteItemCounts - items.size

        val title = createTitle(note)
        val ellipsisThreshold = getStartEllipsisThreshold(START_ELLIPSIS_THRESHOLD_ITEM)
        val highlightedItems = items.mapIndexed { i, item ->
            HighlightHelper.getStartEllipsizedText(
                text = item.content,
                highlights = if (query == null) mutableListOf() else highlights[i],
                startEllipsisThreshold = ellipsisThreshold, startEllipsisDistance = START_ELLIPSIS_DISTANCE_ITEM
            )
        }
        val itemsChecked = items.map { it.checked }

        return NoteItemList(
            id = note.id,
            note = note,
            labels = labels,
            checked = checked,
            title = title,
            items = highlightedItems,
            itemsChecked = itemsChecked,
            overflowCount = overflowCount,
            onlyCheckedInOverflow = onlyCheckedInOverflow,
            showMarkAsDone = showMarkAsDone
        )
    }

    private fun getListItemCount(items: List<ListNoteItem>, highlights: List<List<IntRange>>): Int {
        val maxItemsCount = prefs.getMaximumPreviewLines(NoteType.LIST)
        return minOf(maxItemsCount, if (prefs.moveCheckedToBottom) {
            var count = items.indexOfFirst { it.checked }
            if (count == -1) {
                count = items.size
            }
            if (query != null) {
                // Show checked items with highlights as well
                val checkedHighlighted = items.foldIndexed(0) { i, c, item ->
                    if (highlights[i].isNotEmpty() && item.checked) {
                        c + 1
                    } else {
                        c
                    }
                }
                count += checkedHighlighted
            }
            if (MINIMUM_LIST_NOTE_ITEMS in count until maxItemsCount) {
                // Less than minimum unchecked items, add checked items.
                count = minOf(items.size, MINIMUM_LIST_NOTE_ITEMS)
            }
            count
        } else {
            items.size
        })
    }

    private fun getListItemHighlights(items: List<ListNoteItem>): MutableList<MutableList<IntRange>> {
        return if (query == null) {
            mutableListOf()
        } else {
            var maxHighlights = MAX_HIGHLIGHTS_IN_LIST
            items.mapTo(mutableListOf()) {
                val ranges = HighlightHelper.findHighlightsInString(
                    text = it.content, query = query!!,
                    max = minOf(maxHighlights, MAX_HIGHLIGHTS_IN_LIST_ITEM)
                )
                maxHighlights -= ranges.size
                ranges
            }
        }
    }

    private fun createTitle(note: Note): Highlighted {
        var title = note.title.trim()
        if (appendIdToTitle) {
            title += " (${note.id})"
        }
        return if (query == null) {
            Highlighted(title)
        } else {
            val highlights = HighlightHelper.findHighlightsInString(
                text = title,
                query = query!!,
                max = MAX_HIGHLIGHTS_IN_TITLE
            )
            HighlightHelper.getStartEllipsizedText(
                text = title,
                highlights = highlights,
                startEllipsisThreshold = getStartEllipsisThreshold(START_ELLIPSIS_THRESHOLD_TITLE),
                startEllipsisDistance = START_ELLIPSIS_DISTANCE_TITLE
            )
        }
    }

    // Start ellipsis threshold is doubled in list layout mode
    private fun getStartEllipsisThreshold(threshold: Int) =
        threshold * (if (prefs.listLayoutMode == NoteListLayoutMode.GRID) 1 else 2)

    companion object {
        /**
         * If checked items are moved to the bottom and hidden in preview,
         * this is the minimum number of items shown in a list note preview.
         * So if all items are checked, this number of items will be shown, even if they're checked.
         */
        private const val MINIMUM_LIST_NOTE_ITEMS = 2

        // Maximum number of highlights in each note region
        private const val MAX_HIGHLIGHTS_IN_TITLE = 2
        private const val MAX_HIGHLIGHTS_IN_TEXT = 10
        private const val MAX_HIGHLIGHTS_IN_LIST_ITEM = 2
        private const val MAX_HIGHLIGHTS_IN_LIST = 10

        // Constants for start ellipsis of note content to make sure highlight falls in preview.
        // These are approximate values, the perfect value varies according to device size, character width, font, etc.
        // The current implementation ignores these factors and only accounts for a few settings (list layout, preview lines)
        private const val START_ELLIPSIS_THRESHOLD_TITLE = 20
        private const val START_ELLIPSIS_DISTANCE_TITLE = 10
        private const val START_ELLIPSIS_THRESHOLD_ITEM = 10
        private const val START_ELLIPSIS_DISTANCE_ITEM = 4
        private const val START_ELLIPSIS_THRESHOLD_CONTENT = 15  // per line of preview (-1)
        private const val START_ELLIPSIS_THRESHOLD_CONTENT_FIRST = 5  // for first line of preview
        private const val START_ELLIPSIS_DISTANCE_CONTENT = 20
    }
}