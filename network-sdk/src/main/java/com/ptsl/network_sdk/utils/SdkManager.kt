package com.ptsl.network_sdk.utils

import android.content.Context
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.work.Configuration
import androidx.work.WorkManager
import kotlin.coroutines.Continuation

internal object SdkManager {
    fun init(context: Context) {
        val config = Configuration.Builder()
            .setWorkerFactory(SdkWorkerFactory())
            .build()
        WorkManager.initialize(context, config)
    }
}