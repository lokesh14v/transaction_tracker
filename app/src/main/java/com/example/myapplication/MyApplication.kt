package com.example.ExpenseTracker

import android.app.Application

class MyApplication : Application() {

    lateinit var appDatabase: AppDatabase

    override fun onCreate() {
        super.onCreate()
        appDatabase = AppDatabase.getDatabase(this)
        NotificationHelper.createNotificationChannel(this)
    }
}
