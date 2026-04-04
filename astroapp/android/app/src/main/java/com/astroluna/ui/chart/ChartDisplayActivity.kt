package com.astroluna.ui.chart

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.astroluna.ui.theme.CosmicAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class ChartDisplayActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val birthDataStr = intent.getStringExtra("birthData")
        var birthData: JSONObject? = null

        if (birthDataStr != null) {
            try {
                birthData = JSONObject(birthDataStr)
            } catch (e: Exception) {
                Toast.makeText(this, "Invalid Birth Data", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
        } else {
            Toast.makeText(this, "No Birth Data Provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            CosmicAppTheme {
                ChartDisplayScreen(
                    birthData = birthData!!,
                    onFetchChart = { bData -> fetchChartHtml(bData) }
                )
            }
        }
    }

    private suspend fun fetchChartHtml(birthData: JSONObject): String? = withContext(Dispatchers.IO) {
        try {
            val apiInterface = com.astroluna.data.api.ApiClient.api
            val dateStr = String.format("%04d-%02d-%02d", birthData.optInt("year"), birthData.optInt("month"), birthData.optInt("day"))
            val timeStr = String.format("%02d:%02d", birthData.optInt("hour"), birthData.optInt("minute"))

            val payload = com.google.gson.JsonObject().apply {
                addProperty("date", dateStr)
                addProperty("time", timeStr)
                addProperty("lat", birthData.optDouble("latitude"))
                addProperty("lng", birthData.optDouble("longitude"))
                addProperty("timezone", birthData.optDouble("timezone", 5.5))
            }

            val response = apiInterface.getRasiEngBirthChart(payload)
            if (response.isSuccessful && response.body() != null) {
                val jsonResponse = JSONObject(response.body().toString())
                if (jsonResponse.has("data")) {
                    val data = jsonResponse.getJSONObject("data")
                    generateHtml(data, birthData)
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun generateHtml(data: JSONObject, inputData: JSONObject): String {
        val planetList = data.getJSONArray("planets")
        val planetsMap = JSONObject()
        for (i in 0 until planetList.length()) {
            val p = planetList.getJSONObject(i)
            planetsMap.put(p.getString("name"), p)
        }

        val panchangam = data.getJSONObject("panchangam")
        val dasha = data.optJSONObject("dasha")
        val lagna = data.getJSONObject("houses").getJSONObject("ascendantDetails")
        val navamsa = data.getJSONObject("navamsa").getJSONArray("planets")

        val signMap = mapOf(
            "Pisces" to 0, "Aries" to 1, "Taurus" to 2, "Gemini" to 3,
            "Aquarius" to 4, "Cancer" to 5,
            "Capricorn" to 6, "Leo" to 7,
            "Sagittarius" to 8, "Scorpio" to 9, "Libra" to 10, "Virgo" to 11
        )

        val rasiBoxes = Array(12) { StringBuilder() }
        val lagnaSign = lagna.getString("signName")
        signMap[lagnaSign]?.let { idx -> rasiBoxes[idx].append("<div class='planet lagna'>Lagna</div>") }

        val pKeys = planetsMap.keys()
        while(pKeys.hasNext()) {
            val pName = pKeys.next() as String
            val pData = planetsMap.getJSONObject(pName)
            val pSign = pData.getString("signName")
            val pNameTamil = pName.take(2)
            signMap[pSign]?.let { idx ->
                rasiBoxes[idx].append("<div class='planet'>$pNameTamil</div>")
            }
        }

        val navamsaBoxes = Array(12) { StringBuilder() }
        for (i in 0 until navamsa.length()) {
             val pData = navamsa.getJSONObject(i)
             val pName = pData.getString("name")
             val pSign = pData.getString("signName")
             val pDisp = pName.take(2)
             signMap[pSign]?.let { idx ->
                 navamsaBoxes[idx].append("<div class='planet'>$pDisp</div>")
             }
        }

        return """
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body { font-family: sans-serif; padding: 10px; background: #fdfdfd; }
                    h2, h3 { text-align: center; color: #333; margin: 5px 0; }
                    .chart-container {
                        display: grid;
                        grid-template-columns: 1fr 1fr 1fr 1fr;
                        grid-template-rows: 1fr 1fr 1fr 1fr;
                        gap: 2px;
                        background: #444;
                        border: 2px solid #333;
                        width: 100%;
                        aspect-ratio: 1 / 1;
                        margin-bottom: 20px;
                    }
                    .box { background: #fff; padding: 2px; font-size: 10px; display: flex; flex-wrap: wrap; align-content: center; justify-content: center; min-height: 40px; }
                    .b0 { grid-column: 1; grid-row: 1; }
                    .b1 { grid-column: 2; grid-row: 1; }
                    .b2 { grid-column: 3; grid-row: 1; }
                    .b3 { grid-column: 4; grid-row: 1; }
                    .b4 { grid-column: 1; grid-row: 2; }
                    .center-box { grid-column: 2 / span 2; grid-row: 2 / span 2; background: #ffe; display: flex; align-items: center; justify-content: center; font-weight: bold; }
                    .b5 { grid-column: 4; grid-row: 2; }
                    .b6 { grid-column: 1; grid-row: 3; }
                    .b7 { grid-column: 4; grid-row: 3; }
                    .b8 { grid-column: 1; grid-row: 4; }
                    .b9 { grid-column: 2; grid-row: 4; }
                    .b10 { grid-column: 3; grid-row: 4; }
                    .b11 { grid-column: 4; grid-row: 4; }
                    .planet { background: #e0f7fa; padding: 1px 3px; margin: 1px; border-radius: 3px; color: #006064; font-weight: bold; }
                    .planet.lagna { background: #fce4ec; color: #880e4f; }
                    .info-table { width: 100%; border-collapse: collapse; margin-top: 20px; border-radius: 8px; overflow: hidden; box-shadow: 0 4px 6px rgba(0,0,0,0.1); }
                    .info-table td, .info-table th { border: 1px solid #eee; padding: 10px; text-align: left; font-size: 13px; }
                    .info-table th { background-color: #6200EE; color: white; text-transform: uppercase; letter-spacing: 0.5px; }
                    .info-table tr:nth-child(even) { background-color: #f8f9fa; }
                    .info-table tr:hover { background-color: #f1ecff; }
                    .kp-header { background: #6200EE; color: white; padding: 8px 15px; border-radius: 5px; margin-top: 30px; display: inline-block; font-size: 14px; }
                </style>
            </head>
            <body>
                <h3>${inputData.optString("name")}'s Chart</h3>
                <p style="text-align:center; font-size:12px; color: #666;">${inputData.optString("city")} | ${inputData.optInt("day")}-${inputData.optInt("month")}-${inputData.optInt("year")}</p>

                <div class="kp-header">Rasi & Navamsa</div>
                <div class="chart-container">
                    <div class="box b0">${rasiBoxes[0]}</div>
                    <div class="box b1">${rasiBoxes[1]}</div>
                    <div class="box b2">${rasiBoxes[2]}</div>
                    <div class="box b3">${rasiBoxes[3]}</div>
                    <div class="box b4">${rasiBoxes[4]}</div>
                    <div class="center-box">RASI</div>
                    <div class="box b5">${rasiBoxes[5]}</div>
                    <div class="box b6">${rasiBoxes[6]}</div>
                    <div class="box b7">${rasiBoxes[7]}</div>
                    <div class="box b8">${rasiBoxes[8]}</div>
                    <div class="box b9">${rasiBoxes[9]}</div>
                    <div class="box b10">${rasiBoxes[10]}</div>
                    <div class="box b11">${rasiBoxes[11]}</div>
                </div>

                <h3>Navamsa Chart</h3>
                <div class="chart-container">
                    <div class="box b0">${navamsaBoxes[0]}</div>
                    <div class="box b1">${navamsaBoxes[1]}</div>
                    <div class="box b2">${navamsaBoxes[2]}</div>
                    <div class="box b3">${navamsaBoxes[3]}</div>
                    <div class="box b4">${navamsaBoxes[4]}</div>
                    <div class="center-box">NAVAMSA</div>
                    <div class="box b5">${navamsaBoxes[5]}</div>
                    <div class="box b6">${navamsaBoxes[6]}</div>
                    <div class="box b7">${navamsaBoxes[7]}</div>
                    <div class="box b8">${navamsaBoxes[8]}</div>
                    <div class="box b9">${navamsaBoxes[9]}</div>
                    <div class="box b10">${navamsaBoxes[10]}</div>
                    <div class="box b11">${navamsaBoxes[11]}</div>
                </div>

                <div class="kp-header">Panchangam</div>
                <table class="info-table">
                    <tr><th>Tithi</th><td>${panchangam.optString("tithi")}</td></tr>
                    <tr><th>Nakshatra</th><td>${panchangam.optString("nakshatra")}</td></tr>
                    <tr><th>Yoga</th><td>${panchangam.optString("yoga")}</td></tr>
                    <tr><th>Karana</th><td>${panchangam.optString("karana")}</td></tr>
                </table>

                ${if(dasha != null) """
                <div class="kp-header">Current Dasha</div>
                <table class="info-table">
                    <tr><th>Lord</th><td>${dasha.optString("currentLord")}</td></tr>
                    <tr><th>Bhukti</th><td>${dasha.optString("bhuktiName")}</td></tr>
                    <tr><th>Ends At</th><td>${dasha.optString("endsAt").take(10)}</td></tr>
                    <tr><th>Remaining</th><td>${String.format("%.1f", dasha.optDouble("remainingYearsInCurrentDasha"))} Years</td></tr>
                </table>
                """ else ""}

                <div class="kp-header">KP Planet Details (1-6)</div>
                <table class="info-table">
                    <tr><th>Planet</th><th>Sign</th><th>Star</th><th>Sub</th><th>SSL</th><th>SSSL</th><th>SSSSL</th></tr>
                    ${run {
                        val sb = StringBuilder()
                        val pNames = listOf("Sun", "Moon", "Mars", "Mercury", "Jupiter", "Venus", "Saturn", "Rahu", "Ketu")
                        pNames.forEach { pName ->
                            val p = planetsMap.optJSONObject(pName)
                            if (p != null) {
                                val sL = p.optString("signLord").take(2)
                                val stL = p.optString("starLord").take(2)
                                val subL = p.optString("subLord").take(2)
                                val ssL = p.optString("subSubLord").take(2)
                                val sssL = p.optString("subSubSubLord").take(2)
                                val ssssL = p.optString("subSubSubSubLord").take(2)
                                sb.append("<tr><td>$pName</td><td>$sL</td><td>$stL</td><td>$subL</td><td>$ssL</td><td>$sssL</td><td>$ssssL</td></tr>")
                            }
                        }
                        sb.toString()
                    }}
                </table>

                <div class="kp-header">KP Cusp Details</div>
                <table class="info-table">
                    <tr><th>Cusp</th><th>Degree</th><th>Star Lord</th><th>Sub Lord</th></tr>
                    ${run {
                        val sb = StringBuilder()
                        val cusps = data.optJSONObject("houses")?.optJSONArray("details")
                        if (cusps != null) {
                            for (i in 0 until cusps.length()) {
                                val c = cusps.getJSONObject(i)
                                sb.append("<tr><td>${i+1}</td><td>${c.optString("degreeFormatted")}</td><td>${c.optString("starLord")}</td><td>${c.optString("subLord")}</td></tr>")
                            }
                        }
                        sb.toString()
                    }}
                </table>

                <div class="kp-header">KP Significators</div>
                <table class="info-table">
                    <tr><th>House</th><th>Level 1</th><th>Level 2</th><th>Level 3</th><th>Level 4</th></tr>
                    ${run {
                        val sb = StringBuilder()
                        val kp = data.optJSONObject("kpSignificators")?.optJSONArray("houseView")
                        if (kp != null) {
                            for (i in 0 until kp.length()) {
                                val h = kp.get(i) as JSONObject
                                val l1 = h.optJSONArray("level1")?.let { arr -> List(arr.length()) { arr.getString(it) }.joinToString(", ") } ?: ""
                                val l2 = h.optJSONArray("level2")?.let { arr -> List(arr.length()) { arr.getString(it) }.joinToString(", ") } ?: ""
                                val l3 = h.optJSONArray("level3")?.let { arr -> List(arr.length()) { arr.getString(it) }.joinToString(", ") } ?: ""
                                val l4 = h.optJSONArray("level4")?.let { arr -> List(arr.length()) { arr.getString(it) }.joinToString(", ") } ?: ""
                                sb.append("<tr><td>${h.optInt("house")}</td><td>$l1</td><td>$l2</td><td>$l3</td><td>$l4</td></tr>")
                            }
                        }
                        sb.toString()
                    }}
                </table>
            </body>
            </html>
        """
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartDisplayScreen(
    birthData: JSONObject,
    onFetchChart: suspend (JSONObject) -> String?
) {
    var htmlContent by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val result = onFetchChart(birthData)
        if (result != null) {
            htmlContent = result
        } else {
            failed = true
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Birth Chart Analysis", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF6200EE))
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (failed) {
                 Text(
                     text = "Failed to load chart data.",
                     color = Color.Red,
                     modifier = Modifier.align(Alignment.Center)
                 )
            } else if (htmlContent != null) {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            webViewClient = WebViewClient()
                        }
                    },
                    update = { webView ->
                        webView.loadDataWithBaseURL(null, htmlContent!!, "text/html", "utf-8", null)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
