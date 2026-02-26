package com.ptsl.networksdk_event

import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.ptsl.network_sdk.NetworkDataUploader
import com.ptsl.network_sdk.utils.NetworkSdk
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import com.ptsl.network_sdk.UploadType


class MainActivity : AppCompatActivity() {
    var networkDataUploader = NetworkDataUploader()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        networkDataUploader.init(this,"ReTaiLer")

        val currentDate = SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()
        ).format(System.currentTimeMillis())

//        networkDataUploader.requestPermission { success ->
//            if (success) {
//                uploadData("MYBL-10122222", currentDate, "On Create old")
//                uploadData("MYBL-101333333", currentDate, "On Create old-1")
//                uploadData("MYBL-10144444", currentDate, "On Create old-2")
//                uploadData("MYBL-101555555", currentDate, "On Create old-3")
//            } else {
//                Log.e("Permission", "Required permissions not granted")
//            }
//
//        }

        findViewById<Button>(R.id.event_1).setOnClickListener {
            val currentDate = SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()
            ).format(System.currentTimeMillis())

            uploadData("MYBL-1000111", currentDate, "Button-1",UploadType.NetworkDataCapture)

        }

        findViewById<Button>(R.id.event_2).setOnClickListener {
            val currentDate = SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()
            ).format(System.currentTimeMillis())
            uploadData("MYBL-1023", currentDate, "Button-2",UploadType.FTPNetworkDataCapture)

        }


    }


    private fun uploadData(msisdn: String, currentDate: String, eventName: String, uploadType: UploadType) {
        val progressBar = findViewById<android.widget.ProgressBar>(R.id.progressBar)
        val resultTextView = findViewById<android.widget.TextView>(R.id.resultTextView)
        
        if (uploadType == UploadType.FTPNetworkDataCapture) {
            progressBar.visibility = android.view.View.VISIBLE
            resultTextView.text = "Starting FTP data capture (1 min delay)...\n"
        }

        networkDataUploader.startUploading(
            msisdn,
            "10.0.0",
            currentDate,
            eventName,
            uploadType = uploadType
        ) { success, status ->
            runOnUiThread {
                progressBar.visibility = android.view.View.GONE
                if (success) {
                    Log.i("UploadStatus", "SDK finished successfully for $eventName. Data: ${status.message}")
                    resultTextView.text = "Success for $eventName:\n${status.message}"
                } else {
                    Log.e("UploadStatus", "SDK failed for $eventName. Error: ${status.message}")
                    resultTextView.text = "Failed for $eventName:\n${status.message}"
                }
            }
        }
    }

}
