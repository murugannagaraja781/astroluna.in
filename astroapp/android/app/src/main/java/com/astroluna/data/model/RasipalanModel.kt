package com.astroluna.data.model

import com.google.gson.annotations.SerializedName

data class RasipalanResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: List<RasipalanItem>?
)

data class RasipalanItem(
    @SerializedName("signId") val signId: Int? = null,
    @SerializedName("sign_en") val signNameEn: String?,
    @SerializedName("sign_ta") val signNameTa: String?,
    @SerializedName("date") val date: String?,
    @SerializedName("prediction_ta") val predictionTa: String?,
    @SerializedName("prediction_en") val predictionEn: String?,
    @SerializedName("career_ta") val careerTa: String?,
    @SerializedName("career_en") val careerEn: String?,
    @SerializedName("finance_ta") val financeTa: String?,
    @SerializedName("finance_en") val financeEn: String?,
    @SerializedName("health_ta") val healthTa: String?,
    @SerializedName("health_en") val healthEn: String?,
    @SerializedName("lucky_number") val luckyNumber: String?,
    @SerializedName("lucky_color_ta") val luckyColorTa: String?,
    @SerializedName("lucky_color_en") val luckyColorEn: String?
)
