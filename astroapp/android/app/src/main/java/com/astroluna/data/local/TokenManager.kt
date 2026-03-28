package com.astroluna.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.astroluna.data.model.AuthResponse
import com.google.gson.Gson

class TokenManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = try {
        EncryptedSharedPreferences.create(
            context,
            "secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fix for "Keystore was tampered with or new install" crash
        context.getSharedPreferences("secure_prefs", Context.MODE_PRIVATE).edit().clear().apply()
        // Try deleting file as well for safety
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            context.deleteSharedPreferences("secure_prefs")
        }

        // Retry creating
        EncryptedSharedPreferences.create(
            context,
            "secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveUserSession(auth: AuthResponse) {
        val json = Gson().toJson(auth)
        sharedPreferences.edit().putString("user_session", json).apply()
    }

    fun getUserSession(): AuthResponse? {
        val json = sharedPreferences.getString("user_session", null) ?: return null
        return try {
            Gson().fromJson(json, AuthResponse::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun clearSession() {
        sharedPreferences.edit().clear().apply()
    }

    fun isLoggedIn(): Boolean {
        return getUserSession() != null
    }

    fun updateWalletBalance(balance: Double) {
        val session = getUserSession() ?: return
        val updated = session.copy(walletBalance = balance)
        saveUserSession(updated)
    }

    fun updateProfileImage(imageUrl: String) {
        val session = getUserSession() ?: return
        val updated = session.copy(image = imageUrl)
        saveUserSession(updated)
    }

    // Daily Progress Management
    fun getDailyProgress(): Int {
        return sharedPreferences.getInt("daily_progress", 0)
    }

    fun setDailyProgress(value: Int) {
        sharedPreferences.edit().putInt("daily_progress", value).apply()
    }

    fun getLastDate(): String {
        return sharedPreferences.getString("last_progress_date", "") ?: ""
    }

    fun setLastDate(date: String) {
        sharedPreferences.edit().putString("last_progress_date", date).apply()
    }
}
