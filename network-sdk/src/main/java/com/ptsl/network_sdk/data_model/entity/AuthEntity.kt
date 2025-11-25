package com.ptsl.network_sdk.data_model.entity

import android.os.Parcelable
import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import com.ptsl.network_sdk.BuildConfig
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity
@Keep
data class AuthEntity(
    @PrimaryKey(autoGenerate = false)
    @SerializedName("msisdn") var msisdn: String = "",
    @SerializedName("apiKey") var apiKey: String = BuildConfig.TOKEN,
    @SerializedName("userType") var userType: Int = 1001,
    @SerializedName("sdkInitiateTimeStamp") var sdkInitiateTimeStamp: String = "",
    @SerializedName("integratedAppVersion") var integratedAppVersion: String = "",
    @SerializedName("integratedAppEventName") var integratedAppEventName: String = "Event",
    @SerializedName("sdkVersion") var sdkVersion: String = "",
    @SerializedName("userLatitude") var userLatitude: Double = 0.0,
    @SerializedName("userLongitude") var userLongitude: Double = 0.0
) : Parcelable
