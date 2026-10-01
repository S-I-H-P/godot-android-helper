package com.godot.devassistant.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.godot.devassistant.databinding.ItemDocBinding
import com.godot.devassistant.model.DocEntry

/**
 * 文档列表适配器
 */
class DocAdapter(
    private val onItemClick: (DocEntry) -> Unit
) : ListAdapter<DocEntry, DocAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDocBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemDocBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(doc: DocEntry) {
            binding.textDocTitle.text = doc.title
            binding.root.setOnClickListener {
                onItemClick(doc)
            }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<DocEntry>() {
        override fun areItemsTheSame(old: DocEntry, new: DocEntry): Boolean {
            return old.id == new.id
        }

        override fun areContentsTheSame(old: DocEntry, new: DocEntry): Boolean {
            return old == new
        }
    }
}
