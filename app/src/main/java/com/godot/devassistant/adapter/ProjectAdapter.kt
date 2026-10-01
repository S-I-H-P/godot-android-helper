package com.godot.devassistant.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.godot.devassistant.databinding.ItemProjectBinding
import com.godot.devassistant.model.GodotProject

/**
 * 项目列表适配器
 */
class ProjectAdapter(
    private val onItemClick: (GodotProject) -> Unit
) : ListAdapter<GodotProject, ProjectAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemProjectBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemProjectBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(project: GodotProject) {
            binding.textProjectName.text = project.name
            binding.textScriptCount.text = "${project.scripts.size} 个脚本"
            binding.root.setOnClickListener {
                onItemClick(project)
            }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<GodotProject>() {
        override fun areItemsTheSame(old: GodotProject, new: GodotProject): Boolean {
            return old.uri == new.uri
        }

        override fun areContentsTheSame(old: GodotProject, new: GodotProject): Boolean {
            return old.name == new.name && old.scripts.size == new.scripts.size
        }
    }
}
