package com.example.ExpenseTracker

import android.app.Dialog
import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment

class AddNewCategoryDialogFragment : DialogFragment() {

    interface AddNewCategoryListener {
        fun onNewCategoryAdded(transactionId: Int, newCategoryName: String)
    }

    private var listener: AddNewCategoryListener? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val transactionId = arguments?.getInt(ARG_TRANSACTION_ID) ?: -1

        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Add New Category")

        val input = EditText(requireContext())
        input.hint = "New Category Name"
        builder.setView(input)

        builder.setPositiveButton("Add") { dialog, _ ->
            val categoryName = input.text.toString().trim().uppercase()
            if (categoryName.isNotEmpty()) {
                listener?.onNewCategoryAdded(transactionId, categoryName)
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }

        return builder.create()
    }

    companion object {
        private const val ARG_TRANSACTION_ID = "transaction_id"

        fun newInstance(transactionId: Int, listener: AddNewCategoryListener): AddNewCategoryDialogFragment {
            val fragment = AddNewCategoryDialogFragment()
            val args = Bundle().apply {
                putInt(ARG_TRANSACTION_ID, transactionId)
            }
            fragment.arguments = args
            fragment.listener = listener
            return fragment
        }
    }
}