package com.astroluna.data.repository

import android.content.Context
import com.astroluna.data.local.CSCDatabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CSCRepository(context: Context) {

    private val dbHelper = CSCDatabaseHelper(context)

    suspend fun getCountries(): List<Map<String, String>> {
        return withContext(Dispatchers.IO) {
            dbHelper.getCountries()
        }
    }

    suspend fun getStates(countryId: String): List<Map<String, String>> {
        return withContext(Dispatchers.IO) {
            dbHelper.getStates(countryId)
        }
    }

    suspend fun getCities(stateId: String): List<Map<String, String>> {
        return withContext(Dispatchers.IO) {
            dbHelper.getCities(stateId)
        }
    }
}
