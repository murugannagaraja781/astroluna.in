package com.astroluna.data.model

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class ChartResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("data") val data: ChartData? = null,
    @SerializedName("version") val version: String? = null
)

@Keep
data class ChartData(
    @SerializedName("planets") val planets: List<Planet>? = null,
    @SerializedName("houses") val houses: HouseData? = null,
    @SerializedName("panchanga") val panchanga: Panchanga? = null,
    @SerializedName("dasha") val dasha: List<DashaPeriod>? = null,
    @SerializedName("transits") val transits: List<Transit>? = null,
    @SerializedName("tamilDate") val tamilDate: TamilDate? = null,
    @SerializedName("kpSignificators") val kpSignificators: KPSignificators? = null,
    @SerializedName("navamsa") val navamsa: NavamsaData? = null
)

@Keep
data class Planet(
    @SerializedName("name") val name: String? = null,
    @SerializedName("signName") val signName: String? = null,
    @SerializedName("longitude") val longitude: Double = 0.0,
    @SerializedName("isRetrograde") val isRetrograde: Boolean = false,
    @SerializedName("signIndex") val signIndex: Int = 0,
    @SerializedName("house") val house: Int = 0,
    @SerializedName("nakshatra") val nakshatra: String? = null,
    @SerializedName("nakshatraPada") val nakshatraPada: Int = 0,
    @SerializedName("degreeFormatted") val degreeFormatted: String? = null,
    @SerializedName("signLord") val signLord: String? = null,
    @SerializedName("starLord") val starLord: String? = null,
    @SerializedName("subLord") val subLord: String? = null
)

@Keep
data class HouseData(
    @SerializedName("cusps") val cusps: List<Double>? = null,
    @SerializedName("details") val details: List<HouseDetail>? = null,
    @SerializedName("ascendantDetails") val ascendantDetails: HouseDetail? = null
)

@Keep
data class HouseDetail(
    @SerializedName("signName") val signName: String? = null,
    @SerializedName("signAbbr") val signAbbr: String? = null,
    @SerializedName("nakshatra") val nakshatra: String? = null,
    @SerializedName("nakshatraPada") val nakshatraPada: Int = 0,
    @SerializedName("starLord") val starLord: String? = null,
    @SerializedName("subLord") val subLord: String? = null,
    @SerializedName("degreeFormatted") val degreeFormatted: String? = null
)

@Keep
data class Panchanga(
    @SerializedName("tithi") val tithi: PanchangaValue? = null,
    @SerializedName("nakshatra") val nakshatra: PanchangaValue? = null,
    @SerializedName("yoga") val yoga: PanchangaValue? = null,
    @SerializedName("karana") val karana: PanchangaValue? = null,
    @SerializedName("vara") val vara: PanchangaValue? = null,
    @SerializedName("sunrise") val sunrise: String? = null,
    @SerializedName("sunset") val sunset: String? = null,
    @SerializedName("moonSign") val moonSign: String? = null,
    @SerializedName("sunSign") val sunSign: String? = null
)

@Keep
data class PanchangaValue(@SerializedName("name") val name: String = "")

@Keep
data class DashaPeriod(
    @SerializedName("lord") val lord: String? = null,
    @SerializedName("start") val start: String? = null,
    @SerializedName("end") val end: String? = null,
    @SerializedName("level") val level: Int = 0,
    @SerializedName("subPeriods") val subPeriods: List<DashaPeriod>? = null
)

@Keep
data class Transit(
    @SerializedName("name") val name: String? = null,
    @SerializedName("signName") val signName: String? = null,
    @SerializedName("isRetrograde") val isRetrograde: Boolean? = null
)

@Keep
data class TamilDate(
    @SerializedName("day") val day: Int? = null,
    @SerializedName("month") val month: String? = null,
    @SerializedName("year") val year: String? = null
)

@Keep
data class NavamsaData(
    @SerializedName("planets") val planets: List<Planet>? = null,
    @SerializedName("ascendantSign") val ascendantSign: String? = null
)

@Keep
data class KPSignificators(
    @SerializedName("planetView") val planetView: List<KPPlanet>? = null,
    @SerializedName("houseView") val houseView: List<KPHouse>? = null
)

@Keep
data class KPPlanet(
    @SerializedName("name") val name: String = "",
    @SerializedName("levelA") val levelA: List<Int> = emptyList(),
    @SerializedName("levelB") val levelB: List<Int> = emptyList(),
    @SerializedName("levelC") val levelC: List<Int> = emptyList(),
    @SerializedName("levelD") val levelD: List<Int> = emptyList()
)

@Keep
data class KPHouse(
    @SerializedName("house") val house: Int = 0,
    @SerializedName("level1") val level1: List<String> = emptyList(),
    @SerializedName("level2") val level2: List<String> = emptyList(),
    @SerializedName("level3") val level3: List<String> = emptyList(),
    @SerializedName("level4") val level4: List<String> = emptyList(),
    @SerializedName("lord") val lord: String = ""
)
