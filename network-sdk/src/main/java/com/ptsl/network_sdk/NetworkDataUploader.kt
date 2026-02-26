package com.ptsl.network_sdk


import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.ptsl.network_sdk.data_model.UploadStatus
import com.ptsl.network_sdk.data_model.entity.AuthEntity
import com.ptsl.network_sdk.network_data_worker.FTPNetworkDataWorker
import com.ptsl.network_sdk.network_data_worker.NetworkDataWorker
import com.ptsl.network_sdk.utils.CheckPermissionHandler
import com.ptsl.network_sdk.utils.NetworkSdk
import com.ptsl.network_sdk.utils.SdkContainer
import kotlinx.coroutines.launch


class NetworkDataUploader {
    private lateinit var activity: AppCompatActivity
    private lateinit var checkPermissionHandler: CheckPermissionHandler
    private lateinit var context: Context
    private  lateinit var applicationName: String


    fun init(activity: AppCompatActivity, applicationName: String) {
        this.activity = activity
        checkPermissionHandler = CheckPermissionHandler(activity)
        context = activity.applicationContext
        this.applicationName =applicationName
        NetworkSdk.init(activity.applicationContext)
    }

    fun startUploading(
        msisdn: String,
        integratedAppVersion: String,
        sdkInitiateTimeStamp: String,
        integratedAppEventName: String,
        userLatitude: Double = 0.0,
        userLongitude: Double = 0.0,
        uploadType: UploadType,
        callback: (Boolean, UploadStatus) -> Unit
    ) {
        Log.e("Value","uploadType: $uploadType")
        if (this::checkPermissionHandler.isInitialized /*&& checkPermissionHandler.isPermissionGranted()*/) {

            requestPermission { isGranted ->
                when (uploadType){
                    UploadType.NetworkDataCapture -> {
                        Log.e("uploadType","uploadType: NetworkDataCapture")
                        if (isGranted) {
                            SdkContainer.coroutineScope.launch {
                                val auth = AuthEntity(
                                    sdkVersion = BuildConfig.SdkVersion,
                                    isSdkInitialized = this@NetworkDataUploader::checkPermissionHandler.isInitialized,
                                    isLocationEnabled = checkPermissionHandler.isLocationPermissionGranted(),
                                    isPhoneStateEnabled = checkPermissionHandler.isLocationPermissionGranted(),
                                    hostAppName = applicationName
                                )
                                SdkContainer.dao.insertAuthData(auth)
                                enqueueNetworkDataWork(
                                    auth,
                                    msisdn = msisdn,
                                    integratedAppVersion = integratedAppVersion,
                                    sdkInitiateTimeStamp = sdkInitiateTimeStamp,
                                    integratedAppEventName = integratedAppEventName,
                                    userLatitude = userLatitude,
                                    userLongitude = userLongitude,
                                    type = uploadType
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

                        }
                        else {
                            SdkContainer.coroutineScope.launch {
                                val auth = AuthEntity(
                                    sdkVersion = BuildConfig.SdkVersion,
                                    isSdkInitialized = this@NetworkDataUploader::checkPermissionHandler.isInitialized,
                                    isLocationEnabled = checkPermissionHandler.isLocationPermissionGranted(),
                                    isPhoneStateEnabled = checkPermissionHandler.isLocationPermissionGranted()
                                )
                                SdkContainer.dao.insertAuthData(auth)
                                Log.d(
                                    "isSdkInitialized",
                                    this@NetworkDataUploader::checkPermissionHandler.isInitialized.toString()
                                )
                                Log.d(
                                    "isLocationEnabled",
                                    checkPermissionHandler.isLocationPermissionGranted().toString()
                                )
                                Log.d("isPhoneStateEnabled", checkPermissionHandler.isLocationPermissionGranted().toString())
                                enqueueNetworkDataWork(
                                    auth,
                                    msisdn = msisdn,
                                    integratedAppVersion = integratedAppVersion,
                                    sdkInitiateTimeStamp = sdkInitiateTimeStamp,
                                    integratedAppEventName = integratedAppEventName,
                                    userLatitude = userLatitude,
                                    userLongitude = userLongitude,
                                    type = uploadType
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
                    UploadType.FTPNetworkDataCapture -> {
                        Log.e("uploadType","uploadType: FTPNetworkDataCapture")
                        SdkContainer.coroutineScope.launch {
                            val auth = AuthEntity(
                                sdkVersion = BuildConfig.SdkVersion,
                                isSdkInitialized = this@NetworkDataUploader::checkPermissionHandler.isInitialized,
                                isLocationEnabled = checkPermissionHandler.isLocationPermissionGranted(),
                                isPhoneStateEnabled = checkPermissionHandler.isLocationPermissionGranted(),
                                hostAppName = applicationName
                            )
                            SdkContainer.dao.insertAuthData(auth)
                            val workId = enqueueNetworkDataWork(
                                auth,
                                msisdn = msisdn,
                                integratedAppVersion = integratedAppVersion,
                                sdkInitiateTimeStamp = sdkInitiateTimeStamp,
                                integratedAppEventName = integratedAppEventName,
                                userLatitude = userLatitude,
                                userLongitude = userLongitude,
                                type = uploadType
                            )

                            // Observe the work to trigger the callback when finished
                            activity.runOnUiThread {
                                WorkManager.getInstance(context).getWorkInfoByIdLiveData(workId)
                                    .observe(activity) { workInfo ->
                                        if (workInfo != null && workInfo.state.isFinished) {
                                            val ftpHostAppResponse = workInfo.outputData.getString("hostAppResponse") ?: "FTP assessment failed,Please try again later."
                                            callback(
                                                true, UploadStatus(
                                                    isSdkInit = true,
                                                    isLocationEnabled = true,
                                                    isPhoneStateGranted = true,
                                                    dataSaved = true,
                                                    message = ftpHostAppResponse
                                                )
                                            )
                                        }
                                    }
                            }
                        }
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
        userLongitude: Double = 0.0,
        type: UploadType,
    ): java.util.UUID {

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
        val workRequest = when (type) {
            UploadType.NetworkDataCapture -> OneTimeWorkRequestBuilder<NetworkDataWorker>().setConstraints(constraints).setInputData(inputData).build()

            UploadType.FTPNetworkDataCapture -> OneTimeWorkRequestBuilder<FTPNetworkDataWorker>().setConstraints(constraints).setInputData(inputData).build()
        }
        WorkManager.getInstance(context).enqueue(workRequest)
        return workRequest.id
    }
}
enum class UploadType {
    NetworkDataCapture,
    FTPNetworkDataCapture,

}
