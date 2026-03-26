package com.astroluna.data.api

import com.astroluna.data.model.AuthResponse
import com.astroluna.data.model.PaymentInitiateRequest
import com.astroluna.data.model.PaymentInitiateResponse
import com.astroluna.data.model.SendOtpRequest
import com.astroluna.data.model.VerifyOtpRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiInterface {

    @POST("api/send-otp")
    suspend fun sendOtp(@Body request: SendOtpRequest): Response<com.google.gson.JsonObject>

    @POST("api/verify-otp")
    suspend fun verifyOtp(@Body request: VerifyOtpRequest): Response<AuthResponse>

    @POST("api/payment/create")
    suspend fun initiatePayment(@Body request: PaymentInitiateRequest): Response<PaymentInitiateResponse>

    @POST("api/phonepe/sign")
    suspend fun signPhonePe(@Body request: PaymentInitiateRequest): Response<com.astroluna.data.model.PhonePeSignResponse>

    @retrofit2.http.GET("api/phonepe/status/{transactionId}")
    suspend fun checkPaymentStatus(@retrofit2.http.Path("transactionId") transactionId: String): Response<com.google.gson.JsonObject>

    @POST("api/payment/token")
    suspend fun getPaymentToken(@Body request: PaymentInitiateRequest): Response<com.google.gson.JsonObject>

    @retrofit2.http.GET("api/user/{userId}")
    suspend fun getUserProfile(@retrofit2.http.Path("userId") userId: String): Response<com.astroluna.data.model.AuthResponse>

    // Add other endpoints as needed
    // @POST("register") ...
    @POST("api/city-autocomplete")
    suspend fun searchCity(@Body request: com.google.gson.JsonObject): Response<com.google.gson.JsonObject>

    @POST("api/city-timezone")
    suspend fun getCityTimezone(@Body request: com.google.gson.JsonObject): Response<com.google.gson.JsonObject>

    @retrofit2.http.GET("api/home/banners")
    suspend fun getBanners(): Response<com.astroluna.data.model.BannerResponse>

    @POST("api/charts/birth-chart")
    suspend fun getBirthChart(@Body request: com.google.gson.JsonObject): Response<com.google.gson.JsonObject>

    @POST("api/match/porutham")
    suspend fun getMatchPorutham(@Body request: com.google.gson.JsonObject): Response<com.google.gson.JsonObject>

    @POST("api/rasi-eng/charts/full")
    suspend fun getRasiEngBirthChart(@Body request: com.google.gson.JsonObject): retrofit2.Response<com.google.gson.JsonObject>

    @retrofit2.http.GET("api/rasi-eng/charts/full-test")
    suspend fun getRasiEngBirthChartFallback(
        @retrofit2.http.Query("date") date: String,
        @retrofit2.http.Query("time") time: String,
        @retrofit2.http.Query("lat") lat: Double,
        @retrofit2.http.Query("lng") lng: Double,
        @retrofit2.http.Query("timezone") timezone: Double
    ): retrofit2.Response<com.google.gson.JsonObject>

    @POST("api/rasi-eng/matching")
    suspend fun getRasiEngMatching(@Body request: com.google.gson.JsonObject): Response<com.google.gson.JsonObject>

    @retrofit2.http.GET("api/academy/videos")
    suspend fun getAcademyVideos(): Response<com.google.gson.JsonObject>
    @retrofit2.http.GET("api/user/{userId}/intake")
    suspend fun getUserIntake(@retrofit2.http.Path("userId") userId: String): Response<com.google.gson.JsonObject>

    @POST("api/user/intake")
    suspend fun saveUserIntake(@Body request: com.google.gson.JsonObject): Response<com.google.gson.JsonObject>

    @retrofit2.http.GET("api/chat/history/{sessionId}")
    suspend fun getChatHistory(@retrofit2.http.Path("sessionId") sessionId: String): Response<com.google.gson.JsonObject>

    @retrofit2.http.GET("api/rasi-eng/horoscope/daily")
    suspend fun getRasipalan(): Response<com.astroluna.data.model.RasipalanResponse>


    @POST("api/horoscope/generate-chart")
    suspend fun generateRasiChart(@Body request: com.google.gson.JsonObject): Response<com.google.gson.JsonObject>
    @retrofit2.http.GET("api/referral/stats/{userId}")
    suspend fun getReferralStats(@retrofit2.http.Path("userId") userId: String): Response<com.astroluna.data.model.ReferralStatsResponse>

    @POST("api/withdraw-referral")
    suspend fun withdrawReferral(@Body request: com.google.gson.JsonObject): Response<com.google.gson.JsonObject>

    @POST("api/astrologer/register")
    suspend fun registerAstrologer(@Body request: com.astroluna.data.model.AstroRegistration): Response<com.google.gson.JsonObject>

    @retrofit2.http.GET("api/admin/notifications")
    suspend fun getAdminNotifications(): Response<com.google.gson.JsonObject>

    @POST("api/admin/notifications/read")
    suspend fun markNotificationsRead(): Response<com.google.gson.JsonObject>

    @retrofit2.http.GET("api/admin/astrologers/attended")
    suspend fun getAttendedAstrologers(): Response<com.google.gson.JsonObject>

    @retrofit2.http.GET("api/astrology/astrologers")
    suspend fun getAstrologers(): Response<com.google.gson.JsonObject>
}
