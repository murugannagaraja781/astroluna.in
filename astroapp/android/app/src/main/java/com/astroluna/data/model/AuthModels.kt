package com.astroluna.data.model

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class SendOtpRequest(
    val phone: String
)

@Keep
data class VerifyOtpRequest(
    val phone: String,
    val otp: String,
    val referralCode: String? = null
)

@Keep
data class AuthResponse(
    val ok: Boolean,
    val userId: String?,
    val name: String?,
    val role: String?,
    val phone: String?,
    val walletBalance: Double? = 0.0,
    val referralCode: String? = null,
    val image: String?,
    val error: String?
)
@Keep
data class ReferralStatsResponse(
    val ok: Boolean,
    val stats: ReferralStats?,
    val referrals: ReferralGroups?,
    val error: String?
)

@Keep
data class ReferralStats(
    val level1Count: Int,
    val level2Count: Int,
    val level3Count: Int,
    val totalReferrals: Int,
    val referralEarnings: Int = 0,
    val withdrawableAmount: Int = 0
)

@Keep
data class ReferralGroups(
    val l1: List<ReferredUser>,
    val l2: List<ReferredUser>,
    val l3: List<ReferredUser>
)

@Keep
data class ReferredUser(
    val userId: String,
    val name: String,
    val createdAt: String
)
