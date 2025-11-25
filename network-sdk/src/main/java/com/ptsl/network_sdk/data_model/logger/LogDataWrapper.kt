package com.ptsl.network_sdk.data_model.logger

import android.os.Parcelable
import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import com.ptsl.network_sdk.data_model.entity.AuthEntity
import kotlinx.parcelize.Parcelize

@Parcelize
@Keep
data class LogDataWrapper(
    @SerializedName("networkMyBLUserModel") val auth: AuthEntity,
    @SerializedName("eventlog") val eventLog: List<EventLogModel>
) : Parcelable
