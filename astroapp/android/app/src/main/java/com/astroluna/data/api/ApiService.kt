package com.astroluna.data.api

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * ApiService - HTTP client for backend communication
 *
 * PURPOSE:
 * Handles all HTTP communication with the Node.js backend server.
 * Currently only handles /register endpoint, but can be extended.
 *
 * WHY OKHTTP:
 * - Industry standard HTTP client for Android
 * - Efficient connection pooling
 * - Automatic retry and redirect handling
 * - Easy to use synchronous and async APIs
 * - Good timeout configuration
 *
 * THREAD SAFETY:
 * OkHttpClient is thread-safe and should be shared.
 * All API calls should be made from background threads.
 */
object ApiService {

    private const val TAG = "ApiService"

    // Shared OkHttpClient instance - thread-safe
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    /**
     * Result class for API operations
     */
    data class ApiResult(
        val success: Boolean,
        val error: String? = null,
        val data: JSONObject? = null
    )

    /**
     * Register userId and FCM token with the backend server
     *
     * ENDPOINT: POST /register
     * BODY: { "userId": "xxx", "fcmToken": "yyy" }
     *
     * THE SERVER STORES THIS MAPPING:
     * When someone calls this user, the server looks up the FCM token
     * and sends a push notification to wake up this device.
     *
     * MUST BE CALLED FROM BACKGROUND THREAD (uses synchronous HTTP)
     */
    fun register(serverUrl: String, userId: String, fcmToken: String): ApiResult {
        return try {
            val json = JSONObject().apply {
                put("userId", userId)
                put("fcmToken", fcmToken)
            }

            val requestBody = json.toString().toRequestBody(JSON_MEDIA_TYPE)

            val request = Request.Builder()
                .url("$serverUrl/register")
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .build()

            Log.d(TAG, "Registering: userId=$userId")

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    Log.d(TAG, "Registration successful: $responseBody")
                    ApiResult(
                        success = true,
                        data = if (responseBody.isNotEmpty()) JSONObject(responseBody) else null
                    )
                } else {
                    val errorMsg = "HTTP ${response.code}: $responseBody"
                    Log.e(TAG, "Registration failed: $errorMsg")
                    ApiResult(success = false, error = errorMsg)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Registration error", e)
            ApiResult(success = false, error = e.message ?: "Unknown error")
        }
    }

    /**
     * Initiate a call to another user
     *
     * ENDPOINT: POST /call
     * BODY: { "callerId": "xxx", "calleeId": "yyy", "callerName": "zzz" }
     */
    fun initiateCall(serverUrl: String, callerId: String, calleeId: String): ApiResult {
        return try {
            val json = JSONObject().apply {
                put("callerId", callerId)
                put("calleeId", calleeId)
                put("callerName", callerId) // Use callerId as display name
            }

            val requestBody = json.toString().toRequestBody(JSON_MEDIA_TYPE)

            val request = Request.Builder()
                .url("$serverUrl/call")
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .build()

            Log.d(TAG, "Initiating call: $callerId -> $calleeId")

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    Log.d(TAG, "Call initiated: $responseBody")
                    ApiResult(
                        success = true,
                        data = if (responseBody.isNotEmpty()) JSONObject(responseBody) else null
                    )
                } else {
                    val errorMsg = "HTTP ${response.code}: $responseBody"
                    Log.e(TAG, "Call failed: $errorMsg")
                    ApiResult(success = false, error = errorMsg)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Call error", e)
            ApiResult(success = false, error = e.message ?: "Unknown error")
        }
    }

    fun getChatHistory(serverUrl: String, sessionId: String, limit: Int = 20, before: Long? = null): ApiResult {
        return try {
            var url = "$serverUrl/api/chat/history/$sessionId?limit=$limit"
            if (before != null) {
                url += "&before=$before"
            }

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    ApiResult(success = true, data = JSONObject(responseBody))
                } else {
                    ApiResult(success = false, error = "HTTP ${response.code}")
                }
            }
        } catch (e: Exception) {
            ApiResult(success = false, error = e.message)
        }
    }
}

