package dev.spikeysanju.expensetracker.view.tags

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Filter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import dev.spikeysanju.expensetracker.R
import dev.spikeysanju.expensetracker.databinding.DialogAddTagBinding
import dev.spikeysanju.expensetracker.databinding.FragmentTagsBinding
import dev.spikeysanju.expensetracker.model.Tag.Companion.DEFAULT_ICON_NAME
import dev.spikeysanju.expensetracker.model.Tag
import dev.spikeysanju.expensetracker.utils.TagCatalog
import dev.spikeysanju.expensetracker.view.adapter.TagAdapter
import dev.spikeysanju.expensetracker.view.main.viewmodel.TagViewModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TagsFragment : Fragment() {

    private var _binding: FragmentTagsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TagViewModel by viewModels()
    private lateinit var tagAdapter: TagAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTagsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeTags()

        binding.addTagFab.setOnClickListener {
            showAddEditTagDialog(null)
        }
    }

    private fun setupRecyclerView() {
        tagAdapter = TagAdapter(
            onEditClicked = { tag -> showAddEditTagDialog(tag) },
            onDeleteClicked = { tag -> showDeleteConfirmationDialog(tag) }
        )
        binding.tagsRecyclerView.apply {
            adapter = tagAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun observeTags() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.tags.collect { tags ->
                    tagAdapter.differ.submitList(tags)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.message.collect { message ->
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showAddEditTagDialog(tag: Tag?) {
        val dialogBinding = DialogAddTagBinding.inflate(layoutInflater)

        val tagTypes = listOf(getString(R.string.income), getString(R.string.expense))
        val tagTypeAdapter = ArrayAdapter(
            requireContext(),
            R.layout.item_autocomplete_layout,
            tagTypes
        )
        val tagIconAdapter = TagIconSearchAdapter(
            requireContext(),
            TagCatalog.iconOptions.map { option -> option.label }
        )
        (dialogBinding.tagTypeEt as? AutoCompleteTextView)?.setAdapter(tagTypeAdapter)
        (dialogBinding.tagIconEt as? AutoCompleteTextView)?.apply {
            setAdapter(tagIconAdapter)
            threshold = 1
            setOnClickListener {
                if (TagCatalog.iconOptions.any { option ->
                        option.label.equals(text.toString().trim(), ignoreCase = true)
                    }
                ) {
                    selectAll()
                }
                showDropDown()
            }
            setOnItemClickListener { parent, _, position, _ ->
                val selectedLabel = parent.getItemAtPosition(position) as? String ?: return@setOnItemClickListener
                updateTagIconPreview(
                    dialogBinding,
                    TagCatalog.iconKeyFromLabel(selectedLabel)
                )
            }
        }

        val initialIconName = tag?.iconName ?: DEFAULT_ICON_NAME
        dialogBinding.tagIconEt.setText(TagCatalog.resolveIconLabel(initialIconName), false)
        dialogBinding.tagKeywordEt.setText(tag?.keyword.orEmpty())
        updateTagIconPreview(dialogBinding, initialIconName)

        if (tag != null) {
            dialogBinding.tagNameEt.setText(tag.tagName)
            dialogBinding.tagTypeEt.setText(tag.tagType, false)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (tag == null) getString(R.string.add_tag) else getString(R.string.edit_tag))
            .setView(dialogBinding.root)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val name = dialogBinding.tagNameEt.text.toString().trim()
                val type = dialogBinding.tagTypeEt.text.toString().trim()
                val iconName = TagCatalog.iconKeyFromLabel(dialogBinding.tagIconEt.text.toString())
                val keyword = dialogBinding.tagKeywordEt.text.toString().trim()

                if (name.isNotEmpty() && type.isNotEmpty()) {
                    if (tag == null) {
                        viewModel.insertTag(
                            Tag(
                                tagName = name,
                                tagType = type,
                                iconName = iconName,
                                keyword = keyword
                            )
                        )
                    } else {
                        val updatedTag = tag.copy(
                            tagName = name,
                            tagType = type,
                            iconName = iconName,
                            keyword = keyword
                        )
                        viewModel.updateTag(updatedTag)
                        Toast.makeText(requireContext(), "Tag updated", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun updateTagIconPreview(dialogBinding: DialogAddTagBinding, iconName: String) {
        dialogBinding.tagIconPreview.setImageResource(TagCatalog.resolveIconRes(iconName))
    }

    private fun showDeleteConfirmationDialog(tag: Tag) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.delete_tag))
            .setMessage("Are you sure you want to delete ${tag.tagName}?")
            .setPositiveButton(getString(R.string.delete_tag)) { _, _ ->
                viewModel.deleteTag(tag)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

private class TagIconSearchAdapter(
    context: Context,
    private val allLabels: List<String>
) : ArrayAdapter<String>(
    context,
    R.layout.item_autocomplete_layout,
    ArrayList(allLabels)
) {
    private val iconFilter = object : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val query = constraint?.toString()?.trim().orEmpty()
            val matches = if (query.isEmpty()) {
                allLabels
            } else {
                allLabels.filter { label -> label.contains(query, ignoreCase = true) }
            }

            return FilterResults().apply {
                values = matches
                count = matches.size
            }
        }

        override fun publishResults(constraint: CharSequence?, results: FilterResults) {
            @Suppress("UNCHECKED_CAST")
            val matches = results.values as? List<String> ?: emptyList()
            setNotifyOnChange(false)
            clear()
            addAll(matches)
            notifyDataSetChanged()
        }
    }

    override fun getFilter(): Filter = iconFilter
}
