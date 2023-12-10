package com.roy93group.notes.ui.note.adapter

import android.text.SpannableString
import android.text.format.DateUtils
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.text.set
import androidx.core.view.ViewCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.roy93group.notes.R
import com.roy93group.notes.databinding.VItemHeaderBinding
import com.roy93group.notes.databinding.VItemMessageBinding
import com.roy93group.notes.databinding.VItemNoteLabelBinding
import com.roy93group.notes.databinding.VItemNoteListBinding
import com.roy93group.notes.databinding.VItemNoteListItemBinding
import com.roy93group.notes.databinding.VItemNoteTextBinding
import com.roy93group.notes.model.PrefsManager
import com.roy93group.notes.model.entity.Label
import com.roy93group.notes.model.entity.NoteType
import com.roy93group.notes.ext.strikethroughText
import com.roy93group.notes.ui.note.Highlighted
import com.roy93group.notes.ui.note.ShownDateField
import com.roy93group.notes.utils.RelativeDateFormatter
import java.text.DateFormat

sealed class NoteViewHolder<T : NoteItem>(itemView: View) :
    RecyclerView.ViewHolder(itemView) {

    private val dateFormatter = RelativeDateFormatter(itemView.resources) { date ->
        DateUtils.formatDateTime(
            itemView.context, date, DateUtils.FORMAT_SHOW_DATE or
                    DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_ALL
        )
    }
    private val reminderDateFormatter = RelativeDateFormatter(itemView.resources) { date ->
        DateFormat.getDateInstance(DateFormat.SHORT).format(date)
    }

    abstract val cardView: MaterialCardView
    abstract val swipeImv: ImageView

    protected abstract val titleTxv: TextView
    protected abstract val dateTxv: TextView
    protected abstract val reminderChip: Chip
    protected abstract val labelGroup: ChipGroup
    protected abstract val actionBtn: MaterialButton

    private val labelViewHolders = mutableListOf<LabelChipViewHolder>()

    open fun bind(adapter: NoteAdapter, item: T) {
        bindTitle(adapter, item)
        bindDate(adapter, item)
        bindReminder(item)
        bindLabels(adapter, item)
        bindActionBtn(adapter, item)

        // Set transition names for shared transitions
        val noteId = item.note.id
        ViewCompat.setTransitionName(
            cardView,
            "noteContainer$noteId"
        )

        // Click listeners
        cardView.isChecked = item.checked
        cardView.setOnClickListener {
            adapter.callback.onNoteItemClicked(item, bindingAdapterPosition)
        }
        cardView.setOnLongClickListener {
            adapter.callback.onNoteItemLongClicked(item, bindingAdapterPosition)
            true
        }
    }

    private fun bindTitle(adapter: NoteAdapter, item: NoteItem) {
        titleTxv.text = getHighlightedText(
            text = item.title,
            bgColor = adapter.highlightBackgroundColor, fgColor = adapter.highlightForegroundColor
        )
        titleTxv.isVisible = item.title.content.isNotBlank()
    }

    private fun bindDate(adapter: NoteAdapter, item: NoteItem) {
        val note = item.note
        val dateField = adapter.prefsManager.shownDateField
        val date = when (dateField) {
            ShownDateField.ADDED -> note.addedDate.time
            ShownDateField.MODIFIED -> note.lastModifiedDate.time
            ShownDateField.NONE -> 0L
        }
        dateTxv.text = dateFormatter.format(
            date = date,
            now = System.currentTimeMillis(),
            maxRelativeDays = PrefsManager.MAXIMUM_RELATIVE_DATE_DAYS
        )
        dateTxv.isGone = (dateField == ShownDateField.NONE)
    }

    private fun bindReminder(item: NoteItem) {
        val note = item.note
        reminderChip.isVisible = note.reminder != null
        if (note.reminder != null) {
            reminderChip.text = reminderDateFormatter.format(
                date = note.reminder.next.time,
                now = System.currentTimeMillis(), maxRelativeDays = PrefsManager.MAXIMUM_RELATIVE_DATE_DAYS
            )
            reminderChip.strikethroughText = note.reminder.done
            reminderChip.isActivated = !note.reminder.done
            reminderChip.setChipIconResource(
                if (note.reminder.recurrence != null)
                    R.drawable.ic_repeat else R.drawable.ic_alarm
            )
        }
    }

    private fun bindLabels(adapter: NoteAdapter, item: NoteItem) {
        // Show labels in order up to the maximum, then show a +N chip at the end.
        val maxLabels = adapter.prefsManager.maximumPreviewLabels
        if (maxLabels > 0) {
            labelGroup.isVisible = item.labels.isNotEmpty()
            val labels = if (item.labels.size > maxLabels) {
                item.labels.subList(0, maxLabels) +
                        Label(Label.NO_ID, "+${item.labels.size - maxLabels}")
            } else {
                item.labels
            }
            for (label in labels) {
                val viewHolder = adapter.obtainLabelViewHolder()
                labelViewHolders += viewHolder
                viewHolder.bind(label)
                labelGroup.addView(viewHolder.binding.root)
            }
        } else {
            // Don't show labels in preview
            labelGroup.isVisible = false
        }
    }

    private fun bindActionBtn(adapter: NoteAdapter, item: NoteItem) {
        val bottomPadding: Int
        if (item.showMarkAsDone && !item.checked) {
            actionBtn.isVisible = true
            actionBtn.setIconResource(R.drawable.ic_check)
            actionBtn.setText(R.string.action_mark_as_done)
            actionBtn.setOnClickListener {
                adapter.callback.onNoteActionButtonClicked(item, bindingAdapterPosition)
            }
            bottomPadding = R.dimen.note_bottom_padding_with_action
        } else {
            actionBtn.isVisible = false
            bottomPadding = R.dimen.note_bottom_padding_no_action
        }
        cardView.setContentPadding(
            /* left = */ 0,
            /* top = */ 0,
            /* right = */ 0,
            /* bottom = */ cardView.context.resources.getDimensionPixelSize(bottomPadding)
        )
    }

    /**
     * Unbind a previously bound view holder.
     * This is used to free "secondary" view holders.
     */
    open fun unbind(adapter: NoteAdapter) {
        // Free label view holders
        labelGroup.removeViews(0, labelGroup.childCount)
        for (viewHolder in labelViewHolders) {
            adapter.freeLabelViewHolder(viewHolder)
        }
        labelViewHolders.clear()
    }
}

class TextNoteViewHolder(private val binding: VItemNoteTextBinding) :
    NoteViewHolder<NoteItemText>(binding.root) {

    override val cardView = binding.cardView
    override val swipeImv = binding.swipeImv

    override val titleTxv = binding.titleTxv
    override val dateTxv = binding.dateTxv
    override val reminderChip = binding.reminderChip
    override val labelGroup = binding.labelGroup
    override val actionBtn = binding.actionBtn

    override fun bind(adapter: NoteAdapter, item: NoteItemText) {
        super.bind(adapter, item)

        val contentTxv = binding.contentTxv
        val maxPreviewLines = adapter.prefsManager.getMaximumPreviewLines(NoteType.TEXT)
        contentTxv.isVisible = maxPreviewLines > 0 && item.note.content.isNotBlank()
        contentTxv.text = getHighlightedText(
            item.content,
            adapter.highlightBackgroundColor, adapter.highlightForegroundColor
        )
        contentTxv.maxLines = maxPreviewLines
    }
}

class ListNoteViewHolder(private val binding: VItemNoteListBinding) :
    NoteViewHolder<NoteItemList>(binding.root) {

    override val cardView = binding.cardView
    override val swipeImv = binding.swipeImv

    override val titleTxv = binding.titleTxv
    override val dateTxv = binding.dateTxv
    override val reminderChip = binding.reminderChip
    override val labelGroup = binding.labelGroup
    override val actionBtn = binding.actionBtn

    private val itemViewHolders = mutableListOf<ListNoteItemViewHolder>()

    override fun bind(adapter: NoteAdapter, item: NoteItemList) {
        super.bind(adapter, item)

        // Bind list note items
        val itemsLayout = binding.itemsLayout
        itemsLayout.isVisible = item.items.isNotEmpty()
        for ((i, noteItem) in item.items.withIndex()) {
            val viewHolder = adapter.obtainListNoteItemViewHolder()
            viewHolder.bind(adapter = adapter, item = noteItem, checked = item.itemsChecked[i])
            itemsLayout.addView(/* child = */ viewHolder.binding.root, /* index = */ itemViewHolders.size)
            itemViewHolders += viewHolder
        }

        // Show a label indicating the number of items not shown.
        val infoTxv = binding.infoTxv
        infoTxv.isVisible = item.overflowCount > 0
        if (item.overflowCount > 0) {
            infoTxv.text = adapter.context.resources.getQuantityString(
                if (item.onlyCheckedInOverflow) {
                    R.plurals.note_list_item_info_checked
                } else {
                    R.plurals.note_list_item_info
                }, item.overflowCount, item.overflowCount
            )
        }
    }

    override fun unbind(adapter: NoteAdapter) {
        super.unbind(adapter)
        // Free view holders used by the item.
        binding.itemsLayout.removeViews(0, binding.itemsLayout.childCount - 1)
        for (viewHolder in itemViewHolders) {
            adapter.freeListNoteItemViewHolder(viewHolder)
        }
        itemViewHolders.clear()
    }
}

class MessageViewHolder(private val binding: VItemMessageBinding) :
    RecyclerView.ViewHolder(binding.root) {

    fun bind(item: MessageItem, adapter: NoteAdapter) {
        binding.messageTxv.text = adapter.context.getString(item.message, *item.args.toTypedArray())
        binding.closeImv.setOnClickListener {
            adapter.callback.onMessageItemDismissed(item, bindingAdapterPosition)
            adapter.notifyItemRemoved(bindingAdapterPosition)
        }

        (itemView.layoutParams as StaggeredGridLayoutManager.LayoutParams).isFullSpan = true
    }
}

class HeaderViewHolder(private val binding: VItemHeaderBinding) :
    RecyclerView.ViewHolder(binding.root) {

    fun bind(item: HeaderItem) {
        binding.titleTxv.setText(item.title)
        (itemView.layoutParams as StaggeredGridLayoutManager.LayoutParams).isFullSpan = true
    }
}

/**
 * A view holder for displayed an item in a list note view holder.
 * This is a "secondary" view holder, it is held by another view holder.
 */
class ListNoteItemViewHolder(val binding: VItemNoteListItemBinding) {

    fun bind(adapter: NoteAdapter, item: Highlighted, checked: Boolean) {
        binding.contentTxv.apply {
            text = getHighlightedText(
                text = item,
                bgColor = adapter.highlightBackgroundColor,
                fgColor = adapter.highlightForegroundColor
            )
            strikethroughText = checked && adapter.callback.strikethroughCheckedItems
        }

        binding.checkboxImv.setImageResource(
            if (checked) {
                R.drawable.ic_checkbox_on
            } else {
                R.drawable.ic_checkbox_off
            }
        )
    }
}

/**
 * A view holder for a label chip displayed in note view holders.
 * This is a "secondary" view holder, it is held by another view holder.
 */
class LabelChipViewHolder(val binding: VItemNoteLabelBinding) {

    fun bind(label: Label) {
        binding.labelChip.text = label.name
    }
}

/**
 * Creates a spannable string of a [text] with background spans of a [bgColor] and [fgColor]
 * for all the highlights in it.
 */
fun getHighlightedText(text: Highlighted, bgColor: Int, fgColor: Int): CharSequence {
    if (text.highlights.isEmpty()) {
        return text.content
    }
    val highlightedText = SpannableString(text.content)
    for (highlight in text.highlights) {
        highlightedText[highlight] = BackgroundColorSpan(bgColor)
        highlightedText[highlight] = ForegroundColorSpan(fgColor)
    }
    return highlightedText
}