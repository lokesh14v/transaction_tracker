package com.example.myapplication

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.databinding.ItemTransactionBinding

class TransactionAdapter(private val onReclassifyClick: (Transaction) -> Unit) :
    ListAdapter<Transaction, TransactionAdapter.TransactionViewHolder>(TransactionDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val binding = ItemTransactionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TransactionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        val transaction = getItem(position)
        holder.bind(transaction)
    }

    inner class TransactionViewHolder(private val binding: ItemTransactionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(transaction: Transaction) {
            binding.textViewAmount.text = "Amount: ${transaction.amount}"
            binding.textViewMerchant.text = "Merchant: ${transaction.merchant ?: "N/A"}"
            binding.textViewDate.text = "Date: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(transaction.date as Long))}"
            binding.textViewType.text = "Type: ${transaction.type}"
            binding.textViewCategory.text = "Category: ${transaction.category}"
            binding.textViewOriginalMessage.text = "Original: ${transaction.originalMessage}"

            binding.buttonReclassify.setOnClickListener { onReclassifyClick(transaction) }
        }
    }

    private class TransactionDiffCallback : DiffUtil.ItemCallback<Transaction>() {
        override fun areItemsTheSame(oldItem: Transaction, newItem: Transaction): Boolean {
            return oldItem == newItem // Assuming Transaction data class has unique identity
        }

        override fun areContentsTheSame(oldItem: Transaction, newItem: Transaction): Boolean {
            return oldItem == newItem
        }
    }
}
