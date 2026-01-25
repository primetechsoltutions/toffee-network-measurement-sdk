package com.ptsl.network_sdk.network_data_worker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.app.ActivityCompat
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
import com.ptsl.network_sdk.utils.NetworkEventLogger
import com.ptsl.network_sdk.utils.calculateRttAndLatency
import com.ptsl.network_sdk.utils.prepareDate
import cz.mroczis.netmonster.core.factory.NetMonsterFactory
import cz.mroczis.netmonster.core.model.connection.PrimaryConnection
import kotlinx.coroutines.suspendCancellableCoroutine
import retrofit2.HttpException
import java.io.IOException
import java.util.Locale
import kotlin.text.toDouble

class NetworkDataWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val apiService: ApiService,
    private val downloader: DownloadUploadHelper,
    private val databaseDao: NetworkDao,
) : CoroutineWorker(appContext, workerParams) {


    override suspend fun doWork(): Result {

        val msisdn = inputData.getString("msisdn") ?: ""
        val integratedAppVersion = inputData.getString("integratedAppVersion") ?: ""
        val sdkInitiateTimeStamp = inputData.getString("sdkInitiateTimeStamp") ?: ""
        val integratedAppEventName = inputData.getString("integratedAppEventName") ?: ""
        val userLatitude = inputData.getDouble("userLatitude", 0.0)
        val userLongitude = inputData.getDouble("userLongitude", 0.0)


        val authEntity = getAuth()
        val newDataList: MutableList<NetworkDataEntity> = mutableListOf()

        return try {
            // 1. Location

            val locationPair = LocationHelper.getCurrentLocation(applicationContext)
            // 2. Network data
            val dataList = getReqData(
                locationPair,
                integratedAppVersion
            ).toMutableList()
            for (data in dataList) {
                data.integratedAppEventName = integratedAppEventName
                data.msisdn = msisdn
                data.sdkInitiateTimeStamp = sdkInitiateTimeStamp
                data.userLatitude = userLatitude
                data.userLongitude = userLongitude

                newDataList.add(data)
            }
            Log.d("mergeData", "$dataList");

            // 3. Send network data
            val localData = databaseDao.getNetworkData()

            val mergeData: List<NetworkDataEntity> = newDataList + (localData ?: emptyList())
            Log.d("mergeData", "$mergeData");
//            throw Exception()
            val response = apiService.postNetworkData(
                NetworkDataRequest(
                    authEntity,
                    mergeData
                )
            )
            Log.d("Data Response", "✅ API success: $response")

            clearNetworkDataCache()
            Result.success()

        } catch (e: Exception) {
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

//            Log.e("doWork", "❌ Error: ${e.localizedMessage}", e)
            insertNetworkDataInDb(newDataList)
//            val auth = getAuth()

            // 👇 Serialize request data
            val gson = Gson()
            val failedRequest = try {
                gson.toJson(NetworkDataRequest(authEntity, newDataList))
            } catch (ex: Exception) {
                gson.toJson(
                    mapOf(
                        "error" to "Failed to serialize request", "message" to ex.message
                    )
                )
            }

            val eventLogModel = NetworkEventLogger.createNetworkRequestFailedLog(
                integratedAppEventName, errorMessage, failedRequest, statusCode
            )
            preparedLogEventData(authEntity, eventLogModel)
            Result.failure()
        }
    }


    private suspend fun getAuth(): AuthEntity = databaseDao.getPersistentAuth() ?: AuthEntity()

    /// this is the runing function///


//    private suspend fun getReqData(
//        locationPair: Pair<Double, Double>, integratedAppEventName: String
//    ): ArrayList<NetworkDataEntity> {
//        val dataList = arrayListOf<NetworkDataEntity>()
//        return try {
//            val isMobileNetworkConnected = isMobileNetworkConnected(applicationContext)
//            val activeNetworkMnc = if (isMobileNetworkConnected) getActiveNetworkMNC() else "-1"
//            var exception: Exception? = null
//            //Getting Cell Info
//            NetMonsterFactory.get(applicationContext).apply {
//                //Getting Cell Info With Self Permission
//                val cells = try {
//                    val hasLocationPermission = ActivityCompat.checkSelfPermission(
//                        applicationContext, Manifest.permission.ACCESS_FINE_LOCATION
//                    ) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
//                        applicationContext, Manifest.permission.ACCESS_COARSE_LOCATION
//                    ) == PackageManager.PERMISSION_GRANTED
//
//                    if (hasLocationPermission) {
//                        getCells()
//                    } else {
//                        exception = SecurityException("Missing location permission")
//                        null
//                    }
//                } catch (e: Exception) {
//                    exception = e
//                    null
//                }
//
//                if (cells == null) {
//                    val auth = getAuth()
//                    ///createPermissionMissingLog
//
//                    val eventLogModel = NetworkEventLogger.createPermissionMissingLog(
//                        integratedAppEventName,
//                        exception?.message ?: "N/A",
//                        exception?.stackTraceToString()
//                    )
//                    preparedLogEventData(auth, eventLogModel)
//                    return dataList
//                }
//                val metrics = calculateRttAndLatency()
//                Log.d(
//                    "NetworkMetrics",
//                    "RTT: ${"%.2f".format(metrics.rtt)} ms | Latency: ${"%.2f".format(metrics.latency)} ms"
//                )
//                cells.find {
//                    it.network?.mcc == "470"
//                }?.let {
//                    cells.forEach { cell ->
//                        if (cell.connectionStatus is PrimaryConnection) {
//                            val data = cell.prepareDate(
//                                locationPair,
//                                downloader,
//                                isMobileNetworkConnected,
//                                activeNetworkMnc,
//                                getSimCount(),
//                                rtt = String.format(Locale.US, "%.2f", metrics.rtt.toDouble()).toDouble(),
//                                latency = String.format(Locale.US, "%.2f", metrics.latency.toDouble()).toDouble()
//                            )
//                            dataList.add(data)
//                        }
//                    }
//                }
//            }
//            dataList
//        } catch (e: Exception) {
//            val auth = getAuth()
//            ///createNetworkDataFetchFailedLog
//            val eventLogModel = NetworkEventLogger.createNetworkDataFetchFailedLog(
//                integratedAppEventName, e.message ?: "N/A",
//                e.stackTraceToString()
//            )
//            preparedLogEventData(auth, eventLogModel)
//            dataList
//        }
//    }


    private suspend fun getReqData(
        locationPair: Pair<Double, Double>,
        integratedAppEventName: String
    ): ArrayList<NetworkDataEntity> {

        val dataList = arrayListOf<NetworkDataEntity>()

        return try {

            val isMobileNetworkConnected = isMobileNetworkConnected(applicationContext)
            val activeNetworkMnc = if (isMobileNetworkConnected) getActiveNetworkMNC() else "-1"
            val metrics = calculateRttAndLatency(
                hasMobileInternet = isMobileNetworkConnected
            )

            var permissionException: Exception? = null

            val cells = try {
                val hasPermission =
                    ActivityCompat.checkSelfPermission(
                        applicationContext,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED ||
                            ActivityCompat.checkSelfPermission(
                                applicationContext,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED

                if (hasPermission) {
                    NetMonsterFactory.get(applicationContext).getCells()
                } else {
                    permissionException = SecurityException("Missing location permission")
                    null
                }

            } catch (e: Exception) {
                permissionException = e
                null
            }


            // 🔹 CASE 1: Permission NOT granted → fallback data
            if (cells.isNullOrEmpty()) {

                val auth = getAuth()

                preparedLogEventData(
                    auth,
                    NetworkEventLogger.createPermissionMissingLog(
                        integratedAppEventName,
                        permissionException?.message ?: "N/A",
                        permissionException?.stackTraceToString()
                    )
                )

                // ✅ Create minimal entity WITHOUT cell info
                val speedPair = downloader.getBandWidthSpeed(networkType = "2G",hasMobileInternet= isMobileNetworkConnected,activeNetworkMnc = activeNetworkMnc, currentMnc = activeNetworkMnc)
                dataList.add(
                    NetworkDataEntity(
                        lattitude = 0.0,
                        longitude = 0.0,
                        data = if (isMobileNetworkConnected) "Mobile" else "Wifi",
                        usedSimSlot = getSimCount(),
                        dlspeed = speedPair.first,
                        ulspeed = speedPair.second,
                        rtt = String.format(Locale.US, "%.2f", metrics.rtt.toDouble()).toDouble(),
                        latency = String.format(Locale.US, "%.2f", metrics.latency.toDouble()).toDouble()
                    )
                )

                return dataList
            }

            // 🔹 CASE 2: Permission granted → full cell-based data
            cells.forEach { cell ->
                if (cell.connectionStatus is PrimaryConnection) {
                    dataList.add(
                        cell.prepareDate(
                            locationPair,
                            downloader,
                            isMobileNetworkConnected,
                            activeNetworkMnc,
                            getSimCount(),
                            rtt = String.format(Locale.US, "%.2f", metrics.rtt.toDouble()).toDouble(),
                            latency = String.format(Locale.US, "%.2f", metrics.latency.toDouble()).toDouble()
                        )
                    )
                }
            }

            dataList

        } catch (e: Exception) {
            preparedLogEventData(
                getAuth(),
                NetworkEventLogger.createNetworkDataFetchFailedLog(
                    integratedAppEventName,
                    e.message ?: "N/A",
                    e.stackTraceToString()
                )
            )

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
                    val si = subscriptionManager.getActiveSubscriptionInfo(
                        nDataSubscriptionId
                    )
                    id = si?.mnc?.toString() ?: "-1"
                }
            }

//            Log.e("getActiveNetworkMNC", "Active MNC : $id")


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

    private suspend fun preparedLogEventData(
        auth: AuthEntity,
        eventLogModel: EventLogModel
    ) {
        try {
            val logsToSend = mutableListOf<EventLogModel>()

            // Add cached logs if available
            val cachedCount = databaseDao.getNetworkDataLogEventCount()
            if (cachedCount > 0) {
                logsToSend.addAll(databaseDao.getNetworkDataLogEvent())
            }

            // Add current log
            logsToSend.add(eventLogModel)

            // Send merged logs in a single request
            apiService.postRetailerNetworkDataLogs(
                LogDataWrapper(
                    auth,
                    ArrayList(logsToSend)
                )
            )

            // Clear cache only after successful send
            if (cachedCount > 0) {
                clearNetworkDataCacheLog()
            }

        } catch (e: Exception) {
            // Cache current log if sending fails
            insertNetworkDataLogInDb(eventLogModel)
        }
    }


//    private suspend fun preparedLogEventData(auth: AuthEntity, eventLogModel: EventLogModel) {
//        try {
//            // 4. Send cached logs if available
//            if (databaseDao.getNetworkDataLogEventCount() > 0) {
//                apiService.postRetailerNetworkDataLogs(
//                    LogDataWrapper(
//                        getAuth(),
//                        databaseDao.getNetworkDataLogEvent()
//                    )
//                )
//                clearNetworkDataCacheLog()
//            }
//            apiService.postRetailerNetworkDataLogs(
//                LogDataWrapper(
//                    auth,
//                    arrayListOf(eventLogModel)
//                )
//            )
//
//        } catch (e: Exception) {
//            insertNetworkDataLogInDb(eventLogModel)
//        }
//    }

    private suspend fun insertNetworkDataInDb(
        newDataList: List<NetworkDataEntity>
    ) {
        try {
//            Log.d("newDataList", "$newDataList")

            // Insert once after modification
            if (newDataList.isNotEmpty()) {
                databaseDao.insertNetworkData(newDataList)
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