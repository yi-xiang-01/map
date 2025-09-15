package com.example.mapcollection.ui.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mapcollection.R
import com.example.mapcollection.model.SearchItem

class SearchAdapter(
    private val onItemClick: (SearchItem) -> Unit
) : ListAdapter<SearchItem, SearchAdapter.VH>(Diff) {

    object Diff : DiffUtil.ItemCallback<SearchItem>() {
        override fun areItemsTheSame(oldItem: SearchItem, newItem: SearchItem) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: SearchItem, newItem: SearchItem) = oldItem == newItem
    }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.tvTitle)
        val sub: TextView = v.findViewById(R.id.tvSubtitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_search_result, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.title.text = item.title
        holder.sub.text = item.subtitle

        // ✅ 唯讀瀏覽：點了開 Viewer（不提供編輯）
        holder.itemView.setOnClickListener { onItemClick(item) }
        holder.itemView.isClickable = true
    }
}
