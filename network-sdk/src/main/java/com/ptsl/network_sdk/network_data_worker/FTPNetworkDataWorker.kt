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
import androidx.work.workDataOf
import com.google.gson.Gson
import com.ptsl.network_sdk.api.ApiService
import com.ptsl.network_sdk.data_model.FTPCellInfoGetDataRequest
import com.ptsl.network_sdk.data_model.FTPNetworkDataRequest
import com.ptsl.network_sdk.data_model.NetworkDataRequest
import com.ptsl.network_sdk.data_model.entity.AuthEntity
import com.ptsl.network_sdk.data_model.entity.FTPCellInfoGetRequest
import com.ptsl.network_sdk.data_model.entity.FTPNetworkDataEntity
import com.ptsl.network_sdk.db.NetworkDao
import com.ptsl.network_sdk.dl_ul_test.DownloadUploadHelper
import com.ptsl.network_sdk.utils.CommonUtils
import com.ptsl.network_sdk.utils.prepareFTPData
import cz.mroczis.netmonster.core.factory.NetMonsterFactory
import cz.mroczis.netmonster.core.model.connection.PrimaryConnection
import kotlinx.coroutines.delay
import retrofit2.HttpException
import java.io.IOException

class FTPNetworkDataWorker (
    appContext: Context,
    workerParams: WorkerParameters,
    private val apiService: ApiService,
    private val downloader: DownloadUploadHelper,
    private val databaseDao: NetworkDao,
) : CoroutineWorker(appContext, workerParams){
    override suspend fun doWork(): Result {

        val msisdn = inputData.getString("msisdn") ?: ""
        val integratedAppVersion = inputData.getString("integratedAppVersion") ?: ""
        val sdkInitiateTimeStamp = inputData.getString("sdkInitiateTimeStamp") ?: ""
        val integratedAppEventName = inputData.getString("integratedAppEventName") ?: ""
        val userLatitude = inputData.getDouble("userLatitude", 0.0)
        val userLongitude = inputData.getDouble("userLongitude", 0.0)


        val authEntity = getAuth()

        return try {

            // 1. Location
            val locationPair = LocationHelper.getCurrentLocation(applicationContext)

            // 2. Network data (Single Object with MNC filtering)
            val ftpData = getReqData(locationPair)

            ftpData.integratedAppEventName = integratedAppEventName
            ftpData.msisdn = msisdn
            ftpData.sdkInitiateTimeStamp = sdkInitiateTimeStamp
            ftpData.userLatitude = userLatitude
            ftpData.userLongitude = userLongitude
            ftpData.integratedAppVersion = integratedAppVersion

            Log.d("msisdn", msisdn)

//            throw Exception()
            // 3. Enrich data with Cell Info API
            try {
                if (ftpData.cid != 0 && ftpData.enb != 0) {
                    val enrichmentRequest = FTPCellInfoGetDataRequest(
                        auth = authEntity,
                        data = FTPCellInfoGetRequest(
                            eNB = ftpData.enb,
                            cID = ftpData.cid
                        )
                    )
                    val enrichmentResponse = apiService.postFTPCellInfo(enrichmentRequest)
                    if (enrichmentResponse.statusCode==200 && enrichmentResponse.data.isNotEmpty()) {
                        val cellInfo = enrichmentResponse.data.first()
                        ftpData.apply {
                            eNodeBName = cellInfo.eNodeBName
                            cellName = cellInfo.cellName
                            eNodeBId = cellInfo.eNodeBId
                            sector = cellInfo.sector
                            nbhDlThroughputMbps = cellInfo.nbhDlThroughputMbps
                            nbhTrafficGb = cellInfo.nbhTrafficGb
                        }
                        Log.d("FTPNetworkDataWorker", "✅ Cell Info Enriched: ${cellInfo.cellName}")
                    } else {
                        Log.e("FTPNetworkDataWorker", "❌ Cell Info Enrichment failed: ${enrichmentResponse.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e("FTPNetworkDataWorker", "⚠️ Enrichment Error: ${e.message}")
            }
            val response = apiService.postFTPNetworkData(
                FTPNetworkDataRequest(
                    auth = authEntity,
                    data = ftpData
                )
            )

            // 4. Send network data (Single Object)
            // Evaluate pass/fail conditions
            val isRsrpPass = Math.abs(ftpData.rsrp) <= 105
            val isDlSpeedPass = ftpData.dlSpeed > 5000.0 // > 5 MB (Kbps)
            val isNbhDlThroughputPass = ftpData.nbhDlThroughputMbps > 5.0

            val isPass = isRsrpPass && isDlSpeedPass && isNbhDlThroughputPass
            val testResult = if (isPass) "Pass" else "Failed"
            val statusStr = if (isPass) "Success" else "Failed"
            val statusCode = if (isPass) 200 else 400
            val messageStr = if (isPass) "Your network assessment was successful." else "Your network assessment failed."

            // Construct response format
            val responseMap = mapOf(
                "status" to statusStr,
                "testResult" to testResult,
                "statusCode" to statusCode,
                "message" to messageStr,
                "data" to mapOf(
                    "assessmentId" to response.data.assessmentId,
                    "networkData" to mapOf(
                        "RSRP" to ftpData.rsrp,
                        "SNR" to ftpData.snr,
                        "RSRQ" to ftpData.rsrq
                    ),
                    "cellInfo" to mapOf(
                        "cellName" to ftpData.cellName,
                        "eNodeBName" to ftpData.eNodeBName,
                        "nbhDlThroughputMbps" to ftpData.nbhDlThroughputMbps,
                        "nbhTrafficGB" to ftpData.nbhTrafficGb
                    ),
                    "speedPair" to mapOf(
                        "ulSpeedKbps" to ftpData.ulSpeed,
                        "dlSpeedKbps" to ftpData.dlSpeed
                    ),
                    "userInfo" to mapOf(
                        "msisdn" to ftpData.msisdn,
                        "deviceManufacture" to ftpData.deviceManufacture,
                        "deviceModel" to ftpData.deviceModel,
                        "deviceOsVersion" to ftpData.deviceOsVersion,
                        "latitude" to ftpData.latitude,
                        "longitude" to ftpData.longitude
                    )
                )
            )

            // Serialize the captured data to return to host app
            val gson = Gson()
            val capturedDataJson = gson.toJson(responseMap)
            val demoResult = workDataOf("hostAppResponse" to capturedDataJson)
            Log.e("FTPNetworkDataWorker", "Captured FTP Data: $capturedDataJson")
            Result.success(demoResult)

        } catch (e: Exception) {
            Result.failure()
        }
    }

    private suspend fun getAuth(): AuthEntity = databaseDao.getPersistentAuth() ?: AuthEntity()

    private suspend fun getReqData(
        locationPair: Pair<Double, Double>,
    ): FTPNetworkDataEntity {

        return try {
            val isMobileNetworkConnected = isMobileNetworkConnected(applicationContext)
            val activeNetworkMnc = if (isMobileNetworkConnected) getActiveNetworkMNC() else "-1"
            
            // Normalize MNC for check (e.g. "03" -> "3")
            val normalizedActiveMnc = activeNetworkMnc.removePrefix("0")
            if (isMobileNetworkConnected && normalizedActiveMnc != "3") {
                return FTPNetworkDataEntity().apply { technologyType = "SKIP_MNC_MISMATCH" }
            }

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
                    null
                }

            } catch (e: Exception) {
                null
            }

            if (cells.isNullOrEmpty()) {
                return FTPNetworkDataEntity()
            }

            var resultData = FTPNetworkDataEntity()
            for (cell in cells) {
                if (cell.connectionStatus is PrimaryConnection) {
                    val cellMnc = cell.network?.mnc ?: ""
                    val normalizedCellMnc = cellMnc.removePrefix("0")
                    
                    if (normalizedCellMnc == "3") {
                        resultData = cell.prepareFTPData(
                            locationPair,
                            downloader,
                            isMobileNetworkConnected,
                            activeNetworkMnc,
                            getSimCount(),
                            applicationContext
                        )
                        break
                    }
                }
            }

            resultData

        } catch (e: Exception) {
            FTPNetworkDataEntity()
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

    private fun getActiveNetworkMNC(): String {
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
        } catch (_: Exception) { }
        return "0${id}"
    }

    private fun getDefaultDataSubscriptionId(subscriptionManager: SubscriptionManager): Int {
        if (Build.VERSION.SDK_INT >= 24) {
            val nDataSubscriptionId = SubscriptionManager.getDefaultDataSubscriptionId()
            if (nDataSubscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                return nDataSubscriptionId
            }
        }
        return SubscriptionManager.INVALID_SUBSCRIPTION_ID
    }

    private fun getSimCount(): Int {
        return try {
            val subscriptionManager = SubscriptionManager.from(applicationContext)
            val activeSubscriptionInfoList = subscriptionManager.activeSubscriptionInfoList
            activeSubscriptionInfoList?.size ?: 0
        } catch (e: Exception) {
            0
        }
    }
}