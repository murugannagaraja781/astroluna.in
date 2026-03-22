package com.astroluna.data.remote.nominatim

import com.google.gson.annotations.SerializedName

data class NominatimResult(
    val place_id: Long,
    val display_name: String,
    val lat: String,
    val lon: String,
    val address: NominatimAddress?
)

data class NominatimAddress(
    val city: String?,
    val town: String?,
    val village: String?,
    val state: String?,
    val country: String?
) {
    fun getCityName(): String {
        return city ?: town ?: village ?: ""
    }
}
