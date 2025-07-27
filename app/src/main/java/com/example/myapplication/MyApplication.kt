package com.example.myapplication

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore


class MyApplication : Application() {

    lateinit var appDatabase: AppDatabase
    val transactionViewModel: TransactionViewModel by lazy {
        // This is a simplified way to get a ViewModel without a ViewModelStoreOwner
        // In a real app, you'd typically use a ViewModelProvider with a Fragment/Activity
        // For application-wide access, this approach is often used.
        ViewModelProvider(ViewModelStore(), ViewModelProvider.NewInstanceFactory()).get(TransactionViewModel::class.java)
    }

    override fun onCreate() {
        super.onCreate()
        appDatabase = AppDatabase.getDatabase(this)
    }
}
