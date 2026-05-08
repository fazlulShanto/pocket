package dev.spikeysanju.expensetracker.view.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import dev.spikeysanju.expensetracker.R
import dev.spikeysanju.expensetracker.databinding.ItemTransactionLayoutBinding
import dev.spikeysanju.expensetracker.model.Tag
import dev.spikeysanju.expensetracker.model.Transaction
import dev.spikeysanju.expensetracker.utils.SupportedCurrency
import dev.spikeysanju.expensetracker.utils.TagCatalog
import formatCurrencyAmount
import java.util.Locale

class TransactionAdapter : RecyclerView.Adapter<TransactionAdapter.TransactionVH>() {

	private var selectedCurrency: SupportedCurrency = SupportedCurrency.DEFAULT
    private var tagIconsByName: Map<String, Int> = emptyMap()

    inner class TransactionVH(val binding: ItemTransactionLayoutBinding) :
        RecyclerView.ViewHolder(binding.root)

    private val differCallback = object : DiffUtil.ItemCallback<Transaction>() {
        override fun areItemsTheSame(oldItem: Transaction, newItem: Transaction): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Transaction, newItem: Transaction): Boolean {
            return oldItem == newItem
        }
    }

    val differ = AsyncListDiffer(this, differCallback)

    fun updateCurrency(currency: SupportedCurrency) {
        if (selectedCurrency == currency) {
            return
        }
        selectedCurrency = currency
        notifyDataSetChanged()
    }

    fun updateTagMetadata(tags: List<Tag>) {
        val updatedIcons = tags.associate { tag ->
            normalizeTagName(tag.tagName) to TagCatalog.resolveIconRes(tag.iconName)
        }
        if (tagIconsByName == updatedIcons) {
            return
        }
        tagIconsByName = updatedIcons
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionVH {
        val binding =
            ItemTransactionLayoutBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TransactionVH(binding)
    }

    override fun getItemCount(): Int {
        return differ.currentList.size
    }

    override fun onBindViewHolder(holder: TransactionVH, position: Int) {
        val item = differ.currentList[position]
        holder.binding.apply {
            transactionName.text = item.title
            transactionCategory.text = item.tag

            when (item.transactionType) {
                "Income" -> {
                    transactionAmount.setTextColor(
                        ContextCompat.getColor(
                            transactionAmount.context,
                            R.color.income
                        )
                    )

                    transactionAmount.text = "+ ".plus(
						formatCurrencyAmount(item.amount, selectedCurrency)
					)
                }
                "Expense" -> {
                    transactionAmount.setTextColor(
                        ContextCompat.getColor(
                            transactionAmount.context,
                            R.color.expense
                        )
                    )
                    transactionAmount.text = "- ".plus(
						formatCurrencyAmount(item.amount, selectedCurrency)
					)
                }
            }

            transactionIconView.setImageResource(
                tagIconsByName[normalizeTagName(item.tag)]
                    ?: TagCatalog.resolveIconRes(TagCatalog.defaultIconNameForTag(item.tag))
            )

            // on item click
            holder.itemView.setOnClickListener {
                onItemClickListener?.let { it(item) }
            }
        }
    }

    // on item click listener
    private var onItemClickListener: ((Transaction) -> Unit)? = null
    fun setOnItemClickListener(listener: (Transaction) -> Unit) {
        onItemClickListener = listener
    }

    private fun normalizeTagName(tagName: String): String {
        return tagName.trim().lowercase(Locale.ROOT)
    }
}
