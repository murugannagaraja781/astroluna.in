package com.astroluna.data.remote.nominatim

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object CityRepository {
    private const val BASE_URL = "https://nominatim.openstreetmap.org/"

    private val api: NominatimApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NominatimApi::class.java)
    }

    private val cache = mutableMapOf<String, List<NominatimResult>>()

    suspend fun searchCities(query: String): List<NominatimResult> {
        if (cache.containsKey(query)) return cache[query]!!
        return try {
            val results = api.searchCity(query)
            cache[query] = results
            results
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
