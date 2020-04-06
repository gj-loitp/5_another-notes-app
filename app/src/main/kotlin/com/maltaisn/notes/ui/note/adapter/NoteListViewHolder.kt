/*
 * Copyright 2020 Nicolas Maltais
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.maltaisn.notes.ui.note.adapter

import android.graphics.Color
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.card.MaterialCardView
import com.maltaisn.notes.R
import com.maltaisn.notes.model.entity.ListNoteItem
import com.maltaisn.notes.model.entity.NoteType
import com.maltaisn.notes.ui.note.HighlightHelper
import kotlin.math.min

val HIGHLIGHT_COLOR = Color.rgb(0, 200, 255)

abstract class NoteViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {

    private val cardView = itemView as MaterialCardView
    private val titleTxv: TextView = itemView.findViewById(R.id.txv_title)

    open fun bind(adapter: NoteAdapter, item: NoteItem) {
        titleTxv.text = HighlightHelper.getHighlightedText(item.note.title, item.titleHighlights,
                adapter.highlightBackgroundColor, adapter.highlightForegroundColor)
        titleTxv.isVisible = item.note.title.isNotBlank()

        cardView.isChecked = item.checked
        cardView.setOnClickListener {
            adapter.callback.onNoteItemClicked(item, adapterPosition)
        }
        cardView.setOnLongClickListener {
            adapter.callback.onNoteItemLongClicked(item, adapterPosition)
            true
        }
    }

}

class TextNoteViewHolder(itemView: View) : NoteViewHolder(itemView) {

    private val contentTxv: TextView = itemView.findViewById(R.id.txv_content)

    override fun bind(adapter: NoteAdapter, item: NoteItem) {
        super.bind(adapter, item)
        require(item.note.type == NoteType.TEXT)

        contentTxv.text = HighlightHelper.getHighlightedText(item.note.content, item.contentHighlights,
                adapter.highlightBackgroundColor, adapter.highlightForegroundColor)
        contentTxv.maxLines = adapter.listLayoutMode.maxTextLines
    }
}

class ListNoteViewHolder(itemView: View) : NoteViewHolder(itemView) {

    private val itemLayout: LinearLayout = itemView.findViewById(R.id.layout_list_items)
    private val infoTxv: TextView = itemView.findViewById(R.id.txv_info)

    private val itemViewHolders = mutableListOf<ListNoteItemViewHolder>()

    override fun bind(adapter: NoteAdapter, item: NoteItem) {
        super.bind(adapter, item)
        require(item.note.type == NoteType.LIST)
        require(itemViewHolders.isEmpty())

        val maxItems = adapter.listLayoutMode.maxListItems

        // Add first items in list using view holders in pool.
        // Only the first few items are shown.
        val noteItems = item.note.listItems
        val itemHighlights = HighlightHelper.splitListNoteHighlightsByItem(
                noteItems, item.contentHighlights)
        for (i in 0 until min(maxItems, noteItems.size)) {
            val noteItem = noteItems[i]
            val viewHolder = adapter.obtainListNoteItemViewHolder()
            itemViewHolders += viewHolder
            viewHolder.bind(adapter, noteItem, itemHighlights[i])
            itemLayout.addView(viewHolder.itemView, i + 1)
        }

        // Show a label indicating the number of items not shown.
        val overflowCount = noteItems.size - maxItems
        infoTxv.isVisible = overflowCount > 0
        if (overflowCount > 0) {
            infoTxv.text = adapter.context.resources.getQuantityString(
                    R.plurals.note_list_item_info, overflowCount, overflowCount)
        }
    }

    fun unbind(): List<ListNoteItemViewHolder> {
        if (itemViewHolders.isEmpty()) {
            return emptyList()
        }

        // Free view holders used by the item.
        val viewHolders = itemViewHolders.toList()
        itemLayout.removeViews(1, itemLayout.childCount - 2)
        itemViewHolders.clear()

        return viewHolders
    }
}

class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val messageTxv: TextView = itemView.findViewById(R.id.txv_message)
    private val closeBtn: ImageView = itemView.findViewById(R.id.imv_message_close)

    fun bind(item: MessageItem, adapter: NoteAdapter) {
        messageTxv.text = messageTxv.context.getString(item.message, *item.args)
        closeBtn.setOnClickListener {
            adapter.callback.onMessageItemDismissed(item, adapterPosition)
            adapter.notifyItemRemoved(adapterPosition)
        }

        (itemView.layoutParams as StaggeredGridLayoutManager.LayoutParams).isFullSpan = true
    }
}

class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val titleTxv = itemView as TextView

    fun bind(item: HeaderItem) {
        titleTxv.setText(item.title)
        (itemView.layoutParams as StaggeredGridLayoutManager.LayoutParams).isFullSpan = true
    }
}

class ListNoteItemViewHolder(val itemView: View) {

    private val checkImv: ImageView = itemView.findViewById(R.id.imv_item_checkbox)
    private val contentTxv: TextView = itemView.findViewById(R.id.txv_item_content)

    fun bind(adapter: NoteAdapter, item: ListNoteItem, highlights: List<IntRange>) {
        contentTxv.text = HighlightHelper.getHighlightedText(item.content, highlights,
                adapter.highlightBackgroundColor, adapter.highlightForegroundColor)

        checkImv.setImageResource(if (item.checked) {
            R.drawable.ic_checkbox_on
        } else {
            R.drawable.ic_checkbox_off
        })
    }

}
