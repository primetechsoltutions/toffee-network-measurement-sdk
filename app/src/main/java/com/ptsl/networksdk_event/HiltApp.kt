package com.ptsl.networksdk_event

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration as  WorkConfiguration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class HiltApp : Application(), WorkConfiguration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

//    override fun getWorkManagerConfiguration(): WorkConfiguration {
//        return WorkConfiguration.Builder()
//            .setWorkerFactory(workerFactory)
//            .build()
//    }

    override val workManagerConfiguration: WorkConfiguration
        get() = WorkConfiguration.Builder()
            .setWorkerFactory(workerFactory).build()
}

