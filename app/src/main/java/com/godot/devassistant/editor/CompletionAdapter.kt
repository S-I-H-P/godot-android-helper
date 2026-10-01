package com.godot.devassistant.editor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.godot.devassistant.R
import com.godot.devassistant.model.CompletionItem

/**
 * 代码补全列表适配器
 */
class CompletionAdapter(
    private val onItemClick: (CompletionItem) -> Unit
) : ListAdapter<CompletionItem, CompletionAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_completion, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val labelText: TextView = view.findViewById(R.id.completion_label)
        private val kindText: TextView = view.findViewById(R.id.completion_kind)

        fun bind(item: CompletionItem) {
            labelText.text = item.label
            kindText.text = item.kind

            // 根据类型设置颜色
            val color = when (item.kind) {
                "keyword" -> 0xFFC792EA.toInt()
                "type" -> 0xFFFFCB6B.toInt()
                "function", "method" -> 0xFF82AAFF.toInt()
                "class" -> 0xFFA5E075.toInt()
                else -> 0xFFCDD6F4.toInt()
            }
            labelText.setTextColor(color)

            itemView.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<CompletionItem>() {
        override fun areItemsTheSame(old: CompletionItem, new: CompletionItem): Boolean {
            return old.label == new.label && old.kind == new.kind
        }

        override fun areContentsTheSame(old: CompletionItem, new: CompletionItem): Boolean {
            return old == new
        }
    }
}
