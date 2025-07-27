package com.example.ExpenseTracker

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class CategorySelectionViewModel(private val userCategoryMappingDao: UserCategoryMappingDao) : ViewModel() {

    private val _categories = MutableLiveData<List<String>>()
    val categories: LiveData<List<String>> = _categories

    init {
        loadCategories()
    }

    private fun loadCategories() {
        viewModelScope.launch {
            val predefinedCategories = TransactionCategory.entries.filter { it != TransactionCategory.UNKNOWN }.map { it.name }
            val userDefinedCategories = userCategoryMappingDao.getAllMappings().map { it.pattern }
            val allCategories = mutableListOf<String>()
            allCategories.addAll(predefinedCategories)
            allCategories.addAll(userDefinedCategories)
            _categories.postValue(allCategories)
        }
    }

    fun addNewUserCategory(categoryName: String) {
        viewModelScope.launch {
            userCategoryMappingDao.insert(UserCategoryMapping(pattern = categoryName.lowercase(), category = TransactionCategory.UNKNOWN))
            loadCategories() // Reload categories after adding a new one
        }
    }
}

class CategorySelectionViewModelFactory(private val userCategoryMappingDao: UserCategoryMappingDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CategorySelectionViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CategorySelectionViewModel(userCategoryMappingDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}