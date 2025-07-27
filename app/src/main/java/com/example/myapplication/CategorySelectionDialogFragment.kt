package com.example.myapplication

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment

class CategorySelectionDialogFragment : DialogFragment() {

    interface CategorySelectedListener {
        fun onCategorySelected(transactionId: Int, newCategory: TransactionCategory)
    }

    private var listener: CategorySelectedListener? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val transactionId = arguments?.getInt(ARG_TRANSACTION_ID) ?: -1
        val currentCategory = arguments?.getSerializable(ARG_CURRENT_CATEGORY) as? TransactionCategory

        val categories = TransactionCategory.values().map { it.name }.toTypedArray()
        val selectedItem = categories.indexOf(currentCategory?.name)

        return AlertDialog.Builder(requireContext())
            .setTitle("Select Category")
            .setSingleChoiceItems(categories, selectedItem) { dialog, which ->
                val newCategory = TransactionCategory.valueOf(categories[which])
                listener?.onCategorySelected(transactionId, newCategory)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.cancel()
            }
            .create()
    }

    companion object {
        private const val ARG_TRANSACTION_ID = "transaction_id"
        private const val ARG_CURRENT_CATEGORY = "current_category"

        fun newInstance(transactionId: Int, currentCategory: TransactionCategory, listener: CategorySelectedListener): CategorySelectionDialogFragment {
            val fragment = CategorySelectionDialogFragment()
            val args = Bundle().apply {
                putInt(ARG_TRANSACTION_ID, transactionId)
                putSerializable(ARG_CURRENT_CATEGORY, currentCategory)
            }
            fragment.arguments = args
            fragment.listener = listener
            return fragment
        }
    }
}