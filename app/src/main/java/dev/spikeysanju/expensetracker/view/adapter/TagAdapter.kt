package dev.spikeysanju.expensetracker.view.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import dev.spikeysanju.expensetracker.databinding.ItemTagBinding
import dev.spikeysanju.expensetracker.model.Tag
import dev.spikeysanju.expensetracker.utils.TagCatalog

class TagAdapter(
    private val onEditClicked: (Tag) -> Unit,
    private val onDeleteClicked: (Tag) -> Unit
) : RecyclerView.Adapter<TagAdapter.TagVH>() {

    inner class TagVH(val binding: ItemTagBinding) : RecyclerView.ViewHolder(binding.root)

    private val differCallback = object : DiffUtil.ItemCallback<Tag>() {
        override fun areItemsTheSame(oldItem: Tag, newItem: Tag): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Tag, newItem: Tag): Boolean {
            return oldItem == newItem
        }
    }

    val differ = AsyncListDiffer(this, differCallback)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TagVH {
        val binding = ItemTagBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TagVH(binding)
    }

    override fun onBindViewHolder(holder: TagVH, position: Int) {
        val tag = differ.currentList[position]
        holder.binding.apply {
            tagIconPreview.setImageResource(TagCatalog.resolveIconRes(tag.iconName))
            tagName.text = tag.tagName
            tagType.text = tag.tagType
            tagKeyword.text = tag.keyword
            tagKeyword.isVisible = tag.keyword.isNotBlank()

            editTag.setOnClickListener {
                onEditClicked(tag)
            }

            deleteTag.setOnClickListener {
                onDeleteClicked(tag)
            }
        }
    }

    override fun getItemCount(): Int {
        return differ.currentList.size
    }
}
