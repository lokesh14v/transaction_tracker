package com.example.myapplication

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.databinding.ItemTransactionBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TransactionAdapter @JvmOverloads constructor(private val onCategoryClick: (Transaction) -> Unit = {}) : ListAdapter<Transaction, TransactionAdapter.TransactionViewHolder>(TransactionDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val binding = ItemTransactionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TransactionViewHolder(binding, onCategoryClick)
    }

    override fun onBindViewHolder(
        holder: TransactionViewHolder,
        position: Int
    ) {
        val transaction = getItem(position)
        holder.bind(transaction)
    }

    inner class TransactionViewHolder(private val binding: ItemTransactionBinding, private val onCategoryClick: (Transaction) -> Unit) : RecyclerView.ViewHolder(binding.root) {
        fun bind(transaction: Transaction) {
            val context = binding.root.context
            val amountText = String.format(Locale.getDefault(), "₹ %.2f", transaction.amount)
            binding.transactionAmount.text = amountText

            val amountColor = when (transaction.type) {
                TransactionType.CREDIT -> context.resources.getColor(android.R.color.holo_green_dark, null)
                TransactionType.DEBIT -> context.resources.getColor(android.R.color.holo_red_dark, null)
                else -> context.resources.getColor(android.R.color.black, null)
            }
            binding.transactionAmount.setTextColor(amountColor)

            binding.transactionType.text = "(${transaction.type})"
            binding.transactionMerchant.text = "Merchant: ${transaction.merchant ?: "Unknown"}"
            binding.transactionCategory.text = transaction.category.name
            binding.transactionBank.text = "Bank: ${transaction.bank ?: "N/A"}"
            binding.transactionAccountNumber.text = "A/c: ${transaction.accountNumber ?: "N/A"}"

            val smsDateFormat = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())
            smsDateFormat.timeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata")
            binding.smsDate.text = smsDateFormat.format(Date(transaction.smsDate))

            binding.transactionCategory.setOnClickListener { onCategoryClick(transaction) }
        }
    }

    private class TransactionDiffCallback : DiffUtil.ItemCallback<Transaction>() {
        override fun areItemsTheSame(oldItem: Transaction, newItem: Transaction): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Transaction, newItem: Transaction): Boolean {
            return oldItem == newItem
        }
    }
}