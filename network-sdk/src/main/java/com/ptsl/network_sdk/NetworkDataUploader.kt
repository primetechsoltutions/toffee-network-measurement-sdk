package com.ptsl.network_sdk


import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.ptsl.network_sdk.data_model.UploadStatus
import com.ptsl.network_sdk.data_model.entity.AuthEntity
import com.ptsl.network_sdk.network_data_worker.NetworkDataWorker
import com.ptsl.network_sdk.utils.CheckPermissionHandler
import com.ptsl.network_sdk.utils.SdkContainer
import kotlinx.coroutines.launch


class NetworkDataUploader {
    private lateinit var checkPermissionHandler: CheckPermissionHandler
    private lateinit var context: Context


    fun init(activity: AppCompatActivity) {
        checkPermissionHandler = CheckPermissionHandler(activity)
        context = activity.applicationContext
    }

    fun startUploading(
        msisdn: String,
        integratedAppVersion: String,
        sdkInitiateTimeStamp: String,
        integratedAppEventName: String,
        userLatitude: Double = 0.0,
        userLongitude: Double = 0.0,
        callback: (Boolean, UploadStatus) -> Unit
    ) {
        if (this::checkPermissionHandler.isInitialized /*&& checkPermissionHandler.isPermissionGranted()*/) {

            requestPermission { isGranted ->
                if (isGranted) {
                    SdkContainer.coroutineScope.launch {
                        val auth = AuthEntity(
                            sdkVersion = BuildConfig.SdkVersion,
                        )
                        enqueueNetworkDataWork(
                            auth,
                            msisdn = msisdn,
                            integratedAppVersion = integratedAppVersion,
                            sdkInitiateTimeStamp = sdkInitiateTimeStamp,
                            integratedAppEventName = integratedAppEventName,
                            userLatitude = userLatitude,
                            userLongitude = userLongitude
                        )
                        callback(
                            true, UploadStatus(
                                isSdkInit = true,
                                isLocationEnabled = true,
                                isPhoneStateGranted = true,
                                dataSaved = true,
                                message = "SDK Initialized Successfully"
                            )
                        )
                    }
                } else {
                    SdkContainer.coroutineScope.launch {
                        val auth = AuthEntity(
                            sdkVersion = BuildConfig.SdkVersion,
                        )
                        enqueueNetworkDataWork(
                            auth,
                            msisdn = msisdn,
                            integratedAppVersion = integratedAppVersion,
                            sdkInitiateTimeStamp = sdkInitiateTimeStamp,
                            integratedAppEventName = integratedAppEventName,
                            userLatitude = userLatitude,
                            userLongitude = userLongitude
                        )
                        callback(
                            true, UploadStatus(
                                isSdkInit = true,
                                isLocationEnabled = false,
                                isPhoneStateGranted = false,
                                dataSaved = true,
                                message = "SDK Initialized Successfully"
                            )
                        )
                    }
                }

            }

        } else {
            callback(
                false, UploadStatus(
                    isSdkInit = false,
                    isLocationEnabled = false,
                    isPhoneStateGranted = false,
                    dataSaved = false,
                    message = "SDK Initialized failed"
                )
            )
        }
    }

    fun requestPermission(callback: (Boolean) -> Unit) {
        if (this::checkPermissionHandler.isInitialized) {
            if (checkPermissionHandler.isPermissionGranted())
                callback(true)
            else
                checkPermissionHandler.requestPermission(callback = callback)
        } else {
            callback(false)
        }
    }


    private fun enqueueNetworkDataWork(
        authEntity: AuthEntity,
        msisdn: String,
        integratedAppVersion: String,
        sdkInitiateTimeStamp: String,
        integratedAppEventName: String,
        userLatitude: Double = 0.0,
        userLongitude: Double = 0.0
    ) {

        val inputData = workDataOf(
            "msisdn" to msisdn,
            "integratedAppVersion" to integratedAppVersion,
            "sdkInitiateTimeStamp" to sdkInitiateTimeStamp,
            "integratedAppEventName" to integratedAppEventName,
            "sdkVersion" to authEntity.sdkVersion,
            "userLatitude" to userLatitude,
            "userLongitude" to userLongitude
        )
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        val workRequest = OneTimeWorkRequestBuilder<NetworkDataWorker>().setConstraints(constraints)
            .setInputData(inputData)
            .build()
        WorkManager.getInstance(context).enqueue(workRequest)
    }
}