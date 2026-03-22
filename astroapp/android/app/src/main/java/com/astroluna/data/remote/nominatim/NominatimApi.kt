package com.astroluna.data.remote.nominatim

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface NominatimApi {
    @GET("search")
    suspend fun searchCity(
        @Query("q") query: String,
        @Query("format") format: String = "json",
        @Query("addressdetails") addressDetails: Int = 1,
        @Query("countrycodes") countryCodes: String = "in",
        @Query("limit") limit: Int = 10,
        @Header("User-Agent") userAgent: String = "astrolunaApp"
    ): List<NominatimResult>
}
