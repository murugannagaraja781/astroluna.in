package com.astroluna.data.model

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class SendOtpRequest(
    @SerializedName("phone") val phone: String
)

@Keep
data class VerifyOtpRequest(
    @SerializedName("phone") val phone: String,
    @SerializedName("otp") val otp: String,
    @SerializedName("referralCode") val referralCode: String? = null
)

@Keep
data class AuthResponse(
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("userId") val userId: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("role") val role: String? = null,
    @SerializedName("phone") val phone: String? = null,
    @SerializedName("walletBalance") val walletBalance: Double? = 0.0,
    @SerializedName("referralCode") val referralCode: String? = null,
    @SerializedName("image") val image: String? = null,
    @SerializedName("error") val error: String? = null
)

@Keep
data class ReferralStatsResponse(
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("stats") val stats: ReferralStats? = null,
    @SerializedName("referrals") val referrals: ReferralGroups? = null,
    @SerializedName("error") val error: String? = null
)

@Keep
data class ReferralStats(
    @SerializedName("level1Count") val level1Count: Int = 0,
    @SerializedName("level2Count") val level2Count: Int = 0,
    @SerializedName("level3Count") val level3Count: Int = 0,
    @SerializedName("totalReferrals") val totalReferrals: Int = 0,
    @SerializedName("referralEarnings") val referralEarnings: Int = 0,
    @SerializedName("withdrawableAmount") val withdrawableAmount: Int = 0
)

@Keep
data class ReferralGroups(
    @SerializedName("l1") val l1: List<ReferredUser> = emptyList(),
    @SerializedName("l2") val l2: List<ReferredUser> = emptyList(),
    @SerializedName("l3") val l3: List<ReferredUser> = emptyList()
)

@Keep
data class ReferredUser(
    @SerializedName("userId") val userId: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("createdAt") val createdAt: String = ""
)
