package com.ptsl.network_sdk.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.system.measureTimeMillis

data class NetworkMetrics(
    val rtt: Double,
    val latency: Double
)

suspend fun calculateRttAndLatency(
    testUrl: String = "https://salesforceapptest.banglalink.net",
    hasMobileInternet: Boolean=false,
): NetworkMetrics = withContext(Dispatchers.IO) {
if(hasMobileInternet) {
    val client = OkHttpClient.Builder()
        .retryOnConnectionFailure(true)
        .build()

    var latency = 0L
    var totalRtt = 0L
    val attempts = 3

    repeat(attempts) { index ->
        val request = Request.Builder()
            .url(testUrl)
            .head()
            .build()

        val elapsed = measureTimeMillis {
            client.newCall(request).execute().use { response ->
                if (index == 0) {
                    latency = response.receivedResponseAtMillis -
                            response.sentRequestAtMillis
                }
            }
        }
        totalRtt += elapsed

    }

    NetworkMetrics(
        rtt = totalRtt.toDouble() / attempts,
        latency = latency.toDouble()
    )
}else{
    NetworkMetrics(
        rtt = 0.0,
        latency = 0.0
    )
    }
}
