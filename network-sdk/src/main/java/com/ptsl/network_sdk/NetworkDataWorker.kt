package com.ptsl.network_sdk

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.gson.Gson
import com.ptsl.network_sdk.api.ApiService
import com.ptsl.network_sdk.data_model.NetworkDataRequest
import com.ptsl.network_sdk.data_model.entity.AuthEntity
import com.ptsl.network_sdk.data_model.entity.NetworkDataEntity
import com.ptsl.network_sdk.data_model.logger.EventLogModel
import com.ptsl.network_sdk.data_model.logger.LogDataWrapper
import com.ptsl.network_sdk.db.NetworkDao
import com.ptsl.network_sdk.dl_ul_test.DownloadUploadHelper
import com.ptsl.network_sdk.utils.prepareDate
import cz.mroczis.netmonster.core.factory.NetMonsterFactory
import cz.mroczis.netmonster.core.model.connection.PrimaryConnection
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import retrofit2.HttpException
import java.io.IOException

@HiltWorker
class NetworkDataWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val apiService: ApiService,
    private val downloader: DownloadUploadHelper,
    private val databaseDao: NetworkDao,
) : CoroutineWorker(appContext, workerParams) {

    private val locationClient = LocationServices.getFusedLocationProviderClient(appContext)

    override suspend fun doWork(): Result {
        Log.d("worker", "-------> \n Started \n <-------")
        val auth = getAuth()
        var dataList: MutableList<NetworkDataEntity> = mutableListOf()
        return try {
            // 1. Location

            val locationPair = getCurrentLocation()
            // 2. Network data
            dataList = getReqData(locationPair).toMutableList()
            // 3. Send network data
            databaseDao.getNetworkData()?.let {
                dataList.addAll(it)
            }
            val response = apiService.postNetworkData(NetworkDataRequest(auth, dataList))
            Log.d("Data Response", "✅ API success: $response")

            // 4. Send cached logs if available
            if (databaseDao.getNetworkDataLogEventCount() > 0) {
                apiService.postRetailerNetworkDataLogs(
                    LogDataWrapper(auth, databaseDao.getNetworkDataLogEvent())
                )
                clearNetworkDataCacheLog()
            }
            clearNetworkDataCache()
            Result.success()

        }
        catch (e: Exception) {
            var statusCode: Int = 0
            var errorMessage: String = ""
            when (e) {
                is HttpException -> {
                    statusCode = e.code()
                    errorMessage = "HTTP error: ${e.message}"
                }
                is IOException -> {
                    errorMessage = "Network error: ${e.message}"
                }
                else -> {
                    errorMessage = "Unexpected error: ${e.message}"
                }
            }

            Log.e("doWork", "❌ Error: ${e.localizedMessage}", e)
            insertNetworkDataInDb()
            val auth = getAuth()

            // 👇 Serialize request data
            val gson = Gson()
            val failedRequest = try {
                gson.toJson(NetworkDataRequest(auth, dataList))
            } catch (ex: Exception) {
                gson.toJson(
                    mapOf(
                        "error" to "Failed to serialize request",
                        "message" to ex.message))
            }

            val eventLogModel = EventLogModel(
                logSource = "MyBL App: ${auth.integratedAppEventName}",
                eventType = "Error",
                title = "Network Request Failed",
                description = "Failed to post network data",
                statusCode = statusCode,
                status = if (statusCode in 400..599) "HTTP Error" else "System Error",
                message = errorMessage,
                stackTrace = failedRequest,   // ✅ request body in stack trace
                os = Build.VERSION.SDK_INT.toString(),
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
            )
            preparedLogEventData(auth, eventLogModel)
            Result.failure()
        }
    }


    private suspend fun getCurrentLocation(): Pair<Double, Double> =
        suspendCancellableCoroutine { cont ->
            val hasLocationPermission =
                ActivityCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED ||
                        ActivityCompat.checkSelfPermission(
                            applicationContext,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED

            if (!hasLocationPermission) {
                cont.resume(Pair(0.00, 0.00)) {}
                return@suspendCancellableCoroutine
            }

            locationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                CancellationTokenSource().token
            ).addOnSuccessListener { location ->
                if (location != null) {
                    cont.resume(Pair(location.latitude, location.longitude)) {}
                } else {
                    cont.resume(Pair(0.0, 0.0)) {}
                }
            }.addOnFailureListener {
                cont.resume(Pair(0.0001, 0.0001)) {}
            }
        }

    private suspend fun getAuth(): AuthEntity =
        databaseDao.getPersistentAuth() ?: AuthEntity()

    private suspend fun getReqData(
        locationPair: Pair<Double, Double>,
    ): ArrayList<NetworkDataEntity> {
        val dataList = arrayListOf<NetworkDataEntity>()
        return try {
            val isMobileNetworkConnected = isMobileNetworkConnected(applicationContext)
            val activeNetworkMnc = if (isMobileNetworkConnected) getActiveNetworkMNC() else "-1"
            var exception: Exception? = null
            //Getting Cell Info
            NetMonsterFactory.get(applicationContext).apply {
                //Getting Cell Info With Self Permission
                val cells = try {
                    val hasLocationPermission =
                        ActivityCompat.checkSelfPermission(
                            applicationContext,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED ||
                                ActivityCompat.checkSelfPermission(
                                    applicationContext,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED

                    if (hasLocationPermission) {
                        getCells()
                    } else {
                        exception = SecurityException("Missing location permission")
                        null
                    }
                } catch (e: Exception) {
                    exception = e
                    null
                }

                if (cells == null) {
                    val auth = getAuth()
                    val eventLogModel = EventLogModel(
                        logSource = "MyBl App: ${auth.integratedAppEventName}",
                        eventType = "Error",
                        title = "Get Network Request Failed",
                        description = "Failed to get network data due to missing permissions",
                        statusCode = 901,
                        status = "False",
                        message = """
                    Unable to process network data.
                    isMobileNetworkConnected: $isMobileNetworkConnected
                    activeNetworkMnc: $activeNetworkMnc
                    Error: ${exception?.message ?: "N/A"}
                """.trimIndent(),
                        stackTrace = exception?.stackTraceToString()
                            ?: "No stack trace available",
                        os = Build.VERSION.SDK_INT.toString(),
                        deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
                    )
                    preparedLogEventData(auth, eventLogModel)
                    return dataList
                }
                cells.find {
                    it.network?.mcc == "470"
                }?.let {
                    cells.forEach { cell ->
                        if (cell.connectionStatus is PrimaryConnection) {
                            val data = cell.prepareDate(
                                locationPair,
                                downloader,
                                isMobileNetworkConnected,
                                activeNetworkMnc,
                                getSimCount()
                            )
                            dataList.add(data)
                        }
                    }
                }
            }
            dataList
        } catch (e: Exception) {
            val auth = getAuth()
            val eventLogModel = EventLogModel(
                logSource = "MyBL App: ${auth.integratedAppEventName}",
                eventType = "Error",
                title = "Get Network Request Failed From Exception",
                description = "Failed to get network data",
                statusCode = 902,
                status = "False",
                message = "${e.message}",
                stackTrace = e.stackTraceToString(),
                os = Build.VERSION.SDK_INT.toString(),
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
            )
            preparedLogEventData(auth, eventLogModel)
            dataList
        }
    }
    private suspend fun isMobileNetworkConnected(context: Context): Boolean {
        return try {

            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun getActiveNetworkMNC(): String {
        var id = "-1"
        try {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val subscriptionManager = SubscriptionManager.from(applicationContext)
                val nDataSubscriptionId = getDefaultDataSubscriptionId(subscriptionManager)

                if (nDataSubscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                    val si = subscriptionManager.getActiveSubscriptionInfo(nDataSubscriptionId)
                    id = si?.mnc?.toString() ?: "-1"
                }
            }

            Log.e("getActiveNetworkMNC", "Active MNC : $id")


        } catch (e: Exception) {
        }

        return "0${id}"
    }

    private fun getDefaultDataSubscriptionId(subscriptionManager: SubscriptionManager): Int {
        if (Build.VERSION.SDK_INT >= 24) {
            val nDataSubscriptionId = SubscriptionManager.getDefaultDataSubscriptionId()
            if (nDataSubscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                return nDataSubscriptionId
            }
        }

        try {
            val subscriptionClass = Class.forName(subscriptionManager.javaClass.name)
            try {
                val getDefaultDataSubscriptionId =
                    subscriptionClass.getMethod("getDefaultDataSubscriptionId")
                try {
                    return getDefaultDataSubscriptionId.invoke(subscriptionManager) as Int
                } catch (e1: IllegalAccessException) {
                    e1.printStackTrace()
                }
            } catch (e1: NoSuchMethodException) {
                e1.printStackTrace()
            }
        } catch (e1: ClassNotFoundException) {
            e1.printStackTrace()
        }

        return SubscriptionManager.INVALID_SUBSCRIPTION_ID
    }


    private suspend fun preparedLogEventData(auth: AuthEntity, eventLogModel: EventLogModel) {
        try {
            apiService.postRetailerNetworkDataLogs(LogDataWrapper(auth, arrayListOf(eventLogModel)))

        } catch (e: Exception) {
            insertNetworkDataLogInDb(eventLogModel)
        }
    }

    private suspend fun insertNetworkDataInDb() {
        try {
            val reqData = getReqData(getCurrentLocation())
            Log.e("insertNetworkDataInDb", "reqData: $reqData")

            // Mark all as offline
            reqData.forEach { it.isDataCaptureOffline = true }

            // Insert once after modification
            if (reqData.isNotEmpty()) {
                databaseDao.insertNetworkData(reqData)
            }
        } catch (e: Exception) {
            Log.e("insertNetworkDataInDb", "Error inserting network data", e)
        }
    }

    private suspend fun clearNetworkDataCache() {
        databaseDao.deleteNetworkData()
    }

    private suspend fun clearNetworkDataCacheLog() {
        databaseDao.deleteNetworkDataLogEvent()
    }


    private suspend fun insertNetworkDataLogInDb(eventLogModel: EventLogModel) {
        try {
            databaseDao.insertNetworkDataLogIntoDB(eventLogModel)
        } catch (_: Exception) {
        }
    }

    private fun getSimCount(): Int {
        return try {
            val subscriptionManager = SubscriptionManager.from(applicationContext)
            val activeSubscriptionInfoList = subscriptionManager.activeSubscriptionInfoList
            activeSubscriptionInfoList?.size ?: 0
        } catch (e: Exception) {
            Log.e("getSimCount", "Error getting SIM count", e)
            0
            }
    }
}