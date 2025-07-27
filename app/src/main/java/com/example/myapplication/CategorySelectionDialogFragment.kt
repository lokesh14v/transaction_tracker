
import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import com.example.ExpenseTracker.CategorySelectionViewModel
import com.example.ExpenseTracker.CategorySelectionViewModelFactory
import com.example.ExpenseTracker.MyApplication
import com.example.ExpenseTracker.TransactionCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.widget.EditText
import kotlinx.coroutines.launch

class CategorySelectionDialogFragment : DialogFragment() {

    interface CategorySelectedListener {
        fun onCategorySelected(transactionId: Int, newCategory: TransactionCategory, userDefinedCategoryName: String?)
        fun onAddNewCategoryRequested(transactionId: Int)
    }

    private var listener: CategorySelectedListener? = null
    private lateinit var viewModel: CategorySelectionViewModel

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val transactionId = arguments?.getInt(ARG_TRANSACTION_ID) ?: -1
        val currentCategory = arguments?.getSerializable(ARG_CURRENT_CATEGORY) as? TransactionCategory

        val userCategoryMappingDao = (requireActivity().application as MyApplication).appDatabase.userCategoryMappingDao()
        val factory = CategorySelectionViewModelFactory(userCategoryMappingDao)
        viewModel = ViewModelProvider(this, factory).get(CategorySelectionViewModel::class.java)

        val loadingDialog = AlertDialog.Builder(requireContext())
            .setTitle("Loading Categories...")
            .setCancelable(false)
            .create()

        viewModel.categories.observe(this) { allCategories ->
            loadingDialog.dismiss()

            val categoriesArray = allCategories.toTypedArray()
            val selectedItem = if (currentCategory != null && currentCategory != TransactionCategory.UNKNOWN) {
                allCategories.indexOf(currentCategory.name)
            } else {
                -1 // No pre-selection if current is UNKNOWN or null
            }

            val alertDialog = AlertDialog.Builder(requireContext())
                .setTitle("Select Category")
                .setSingleChoiceItems(categoriesArray, selectedItem) { dialog, which ->
                    val selectedCategoryName = categoriesArray[which]
                    val newCategory = if (TransactionCategory.entries.any { it.name == selectedCategoryName }) {
                        TransactionCategory.valueOf(selectedCategoryName)
                    } else {
                        // User-defined category, map to UNKNOWN for Transaction entity
                        TransactionCategory.UNKNOWN
                    }
                    listener?.onCategorySelected(transactionId, newCategory, if (newCategory == TransactionCategory.UNKNOWN) selectedCategoryName else null)
                    dialog.dismiss()
                }
                .setPositiveButton("Add New Category") { dialog, _ ->
                    listener?.onAddNewCategoryRequested(transactionId)
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    dialog.cancel()
                }
                .create()

            alertDialog.show()
        }
        return loadingDialog
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