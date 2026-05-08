package dev.spikeysanju.expensetracker.view.edit

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import dagger.hilt.android.AndroidEntryPoint
import dev.spikeysanju.expensetracker.R
import dev.spikeysanju.expensetracker.databinding.FragmentEditTransactionBinding
import dev.spikeysanju.expensetracker.model.Tag
import dev.spikeysanju.expensetracker.model.Transaction
import dev.spikeysanju.expensetracker.utils.Constants
import dev.spikeysanju.expensetracker.utils.TagKeywordMatcher
import dev.spikeysanju.expensetracker.view.base.BaseFragment
import dev.spikeysanju.expensetracker.view.main.viewmodel.TagViewModel
import dev.spikeysanju.expensetracker.view.main.viewmodel.TransactionViewModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import parseDouble
import snack
import transformIntoDatePicker
import java.util.*

@AndroidEntryPoint
class EditTransactionFragment : BaseFragment<FragmentEditTransactionBinding, TransactionViewModel>() {
    private val args: EditTransactionFragmentArgs by navArgs()
    override val viewModel: TransactionViewModel by activityViewModels()
    private val tagViewModel: TagViewModel by activityViewModels()
    private var availableTags: List<Tag> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // receiving bundles here
        val transaction = args.transaction
        initViews()
        observeTags()
        loadData(transaction)
    }

    private fun loadData(transaction: Transaction) = with(binding) {
        addTransactionLayout.etTitle.setText(transaction.title)
        addTransactionLayout.etAmount.setText(transaction.amount.toString())
        addTransactionLayout.etTransactionType.setText(transaction.transactionType, false)
        addTransactionLayout.etTag.setText(transaction.tag, false)
        addTransactionLayout.etWhen.setText(transaction.date)
        addTransactionLayout.etNote.setText(transaction.note)
        updateTagsAdapter()
    }

    private fun initViews() = with(binding) {
        val transactionTypeAdapter =
            ArrayAdapter(
                requireContext(),
                R.layout.item_autocomplete_layout,
                Constants.transactionType
            )

        // Set list to TextInputEditText adapter
        addTransactionLayout.etTransactionType.setAdapter(transactionTypeAdapter)
        addTransactionLayout.etTitle.doAfterTextChanged {
            applyKeywordSuggestionIfNeeded()
        }

        // Transform TextInputEditText to DatePicker using Ext function
        addTransactionLayout.etWhen.transformIntoDatePicker(
            requireContext(),
            "dd/MM/yyyy",
            Date()
        )

        addTransactionLayout.etTransactionType.setOnItemClickListener { _, _, _, _ ->
            updateTagsAdapter()
            addTransactionLayout.etTag.text = null
        }

        btnSaveTransaction.setOnClickListener {
            applyKeywordSuggestionIfNeeded()
            binding.addTransactionLayout.apply {
                val (title, amount, transactionType, tag, date, note) =
                    getTransactionContent()
                // validate if transaction content is empty or not
                when {
                    title.isEmpty() -> {
                        this.etTitle.error = "Title must not be empty"
                    }
                    amount.isNaN() -> {
                        this.etAmount.error = "Amount must not be empty"
                    }
                    transactionType.isEmpty() -> {
                        this.etTransactionType.error = "Transaction type must not be empty"
                    }
                    tag.isEmpty() -> {
                        this.etTag.error = "Tag must not be empty"
                    }
                    date.isEmpty() -> {
                        this.etWhen.error = "Date must not be empty"
                    }
                    note.isEmpty() -> {
                        this.etNote.error = "Note must not be empty"
                    }
                    else -> {
                        viewModel.updateTransaction(getTransactionContent()).also {
                            binding.root.snack(
                                string = R.string.success_expense_saved
                            ).run {
                                findNavController().popBackStack()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun observeTags() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                tagViewModel.tags.collect { tags ->
                    availableTags = tags
                    updateTagsAdapter()
                    applyKeywordSuggestionIfNeeded()
                }
            }
        }
    }

    private fun applyKeywordSuggestionIfNeeded() = with(binding.addTransactionLayout) {
        if (etTag.text.toString().isNotBlank()) {
            return
        }

        val matchedTag = TagKeywordMatcher.findBestMatch(
            tags = availableTags,
            title = etTitle.text.toString()
        ) ?: return

        if (etTransactionType.text.toString() != matchedTag.tagType) {
            etTransactionType.setText(matchedTag.tagType, false)
            updateTagsAdapter()
        }
        etTag.setText(matchedTag.tagName, false)
    }

    private fun updateTagsAdapter() {
        val transactionType = binding.addTransactionLayout.etTransactionType.text.toString()
        val filteredTags = if (transactionType.isNotEmpty()) {
            availableTags
                .filter { it.tagType == transactionType }
                .map { it.tagName }
                .sorted()
        } else {
            emptyList()
        }
        val tagsAdapter = ArrayAdapter(
            requireContext(),
            R.layout.item_autocomplete_layout,
            filteredTags
        )
        binding.addTransactionLayout.etTag.setAdapter(tagsAdapter)
    }

    private fun getTransactionContent(): Transaction = binding.addTransactionLayout.let {
        val id = args.transaction.id
        val title = it.etTitle.text.toString()
        val amount = parseDouble(it.etAmount.text.toString())
        val transactionType = it.etTransactionType.text.toString()
        val tag = it.etTag.text.toString()
        val date = it.etWhen.text.toString()
        val note = it.etNote.text.toString()

        return Transaction(
            title = title,
            amount = amount,
            transactionType = transactionType,
            tag = tag,
            date = date,
            note = note,
            createdAt = args.transaction.createdAt,
            id = id
        )
    }

    override fun getViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ) = FragmentEditTransactionBinding.inflate(inflater, container, false)
}
