package com.ptsl.networksdk_event

import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.ptsl.network_sdk.NetworkDataUploader
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@FlowPreview
@ExperimentalCoroutinesApi
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var networkDataUploader: NetworkDataUploader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        networkDataUploader.init(this)

        val currentDate = SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()
        ).format(System.currentTimeMillis())

        networkDataUploader.requestPermission { success ->
            if (success){
                uploadData("MYBL-10122222", currentDate, "On Create old")
                uploadData("MYBL-101333333", currentDate, "On Create old-1")
                uploadData("MYBL-10144444", currentDate, "On Create old-2")
                uploadData("MYBL-101555555", currentDate, "On Create old-3")
            }else{
                Log.e("Permission", "Required permissions not granted")
            }

        }

        findViewById<Button>(R.id.event_1).setOnClickListener {
            val currentDate = SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()
            ).format(System.currentTimeMillis())
            networkDataUploader.requestPermission { success ->
               if (success){
                   uploadData("MYBL-1000111", currentDate, "Button-1")

               }else{
                   Log.e("Permission", "Required permissions not granted")
               }

            }
        }

        findViewById<Button>(R.id.event_2).setOnClickListener {
            val currentDate = SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()
            ).format(System.currentTimeMillis())
            networkDataUploader.requestPermission { success ->
                if (success){
                    networkDataUploader.startUploading(
                        "MYBL-101",// You can provide the PNL ID Here.
                        "10.0.0",
                        currentDate,
                        "Event-22",
                    ){ success ->
                        if (success) {
                            Log.i("UploadStatus", "SDK started successfully for Event-2")
                        } else {
                            Log.e("UploadStatus", "SDK failed to start for Event-2")
                        }
                    }
                }else{
                    Log.e("Permission", "Required permissions not granted")
                }

            }
        }
        findViewById<Button>(R.id.event_2).setOnClickListener {
            val currentDate = SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()
            ).format(System.currentTimeMillis())
            networkDataUploader.requestPermission { success ->
                if (success){

                }else{
                    Log.e("Permission", "Required permissions not granted")
                }

            }
        }

    }


    private fun uploadData(msisdn: String, currentDate: String, eventName: String) {
        networkDataUploader.requestPermission { success ->
            if (success) {
                networkDataUploader.startUploading(msisdn, "10.0.0", currentDate, eventName) { success ->
                    if (success) {
                        Log.i("UploadStatus", "SDK started successfully for $eventName")
                    } else {
                        Log.e("UploadStatus", "SDK failed to start for $eventName")
                    }
                }
            } else {
                Log.e("Permission", "Required permissions not granted")
            }
        }
    }

}
