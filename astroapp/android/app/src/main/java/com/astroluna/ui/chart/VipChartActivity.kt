package com.astroluna.ui.chart

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astroluna.ui.theme.CosmicAppTheme
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import android.app.Activity
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.astroluna.ui.intake.IntakeActivity
import org.json.JSONObject

// --- Aesthetic Constants (Premium Blue) ---
val DeepSpaceNavy = Color(0xFF000B18)
val PremiumBlue = Color(0xFF001F3F)
val NeonCyan = Color(0xFF7FDBFF)
val ElectricBlue = Color(0xFF0074D9)
val TraditionalRed = Color(0xFFFF4B2B) // Neon Red/Orange
val GridBg = Color.White.copy(alpha = 0.95f)
val BorderColor = NeonCyan

// --- Tamil Translation Constants ---
val signTamil = mapOf(
    "Aries" to "மேஷம்", "Taurus" to "ரிஷபம்", "Gemini" to "மிதுனம்", "Cancer" to "கடகம்",
    "Leo" to "சிம்மம்", "Virgo" to "கன்னி", "Libra" to "துலாம்", "Scorpio" to "விருச்சிகம்",
    "Sagittarius" to "தனுசு", "Capricorn" to "மகரம்", "Aquarius" to "கும்பம்", "Pisces" to "மீனம்"
)

val planetTamil = mapOf(
    "Sun" to "சூரியன்", "Moon" to "சந்திரன்", "Mars" to "செவ்வாய்", "Mercury" to "புதன்",
    "Jupiter" to "குரு", "Venus" to "சுக்கிரன்", "Saturn" to "சனி", "Rahu" to "ராகு",
    "Ketu" to "கேது", "Ascendant" to "லக்னம்", "Mandi" to "மாந்தி"
)

val planetAbbrTamil = mapOf(
    "Sun" to "சூரி", "Moon" to "சந்", "Mars" to "செவ்", "Mercury" to "புத",
    "Jupiter" to "குரு", "Venus" to "சுக்", "Saturn" to "சனி", "Rahu" to "ராகு",
    "Ketu" to "கேது", "Ascendant" to "லக்", "As" to "லக்", "Mandi" to "மாந்தி"
)


// --- Updated Data Models ---
data class ChartResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: ChartData?
)

data class ChartData(
    @SerializedName("planets") val planets: List<Planet>? = null,
    @SerializedName("houses") val houses: HouseData? = null,
    @SerializedName("panchanga") val panchanga: Panchanga? = null,
    @SerializedName("dasha") val dasha: List<DashaPeriod>? = null,
    @SerializedName("transits") val transits: List<Transit>? = null,
    @SerializedName("tamilDate") val tamilDate: TamilDate? = null,
    @SerializedName("navamsa") val navamsa: NavamsaData? = null
)

data class Planet(
    @SerializedName("name") val name: String,
    @SerializedName("signName") val signName: String,
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

data class HouseData(
    @SerializedName("cusps") val cusps: List<Double>,
    @SerializedName("details") val details: List<HouseDetail>? = null,
    @SerializedName("ascendantDetails") val ascendantDetails: HouseDetail? = null
)

data class HouseDetail(
    @SerializedName("signName") val signName: String,
    @SerializedName("signAbbr") val signAbbr: String? = null,
    @SerializedName("nakshatra") val nakshatra: String? = null,
    @SerializedName("nakshatraPada") val nakshatraPada: Int = 0,
    @SerializedName("starLord") val starLord: String? = null,
    @SerializedName("subLord") val subLord: String? = null,
    @SerializedName("degreeFormatted") val degreeFormatted: String? = null
)

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

data class PanchangaValue(@SerializedName("name") val name: String)
data class DashaPeriod(
    @SerializedName("lord") val lord: String? = null,
    @SerializedName("start") val start: String? = null,
    @SerializedName("end") val end: String? = null,
    @SerializedName("level") val level: Int = 1,
    @SerializedName("subPeriods") val subPeriods: List<DashaPeriod>? = null
)
data class Transit(
    @SerializedName("name") val name: String,
    @SerializedName("signName") val signName: String,
    @SerializedName("isRetrograde") val isRetrograde: Boolean
)
data class TamilDate(
    @SerializedName("day") val day: Int,
    @SerializedName("month") val month: String,
    @SerializedName("year") val year: String
)
data class NavamsaData(
    @SerializedName("planets") val planets: List<Planet>? = null,
    @SerializedName("ascendantSign") val ascendantSign: String? = null
)

class VipChartActivity : ComponentActivity() {
    private var updateReceiver: android.content.BroadcastReceiver? = null
    private val birthDataState = mutableStateOf<JSONObject>(JSONObject("{}"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val birthDataStr = intent.getStringExtra("birthData") ?: "{}"
        birthDataState.value = JSONObject(birthDataStr)

        // Register receiver for real-time updates during call
        updateReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                val newDataStr = intent?.getStringExtra("birthData")
                if (newDataStr != null) {
                    try {
                        val newData = JSONObject(newDataStr)
                        // This will trigger recomposition and the LaunchedEffect(birthDataState.value)
                        birthDataState.value = newData
                        android.util.Log.d("VipChart", "Received real-time update in ChartActivity")
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
        val filter = android.content.IntentFilter("com.astroluna.REFRESH_CHART")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(updateReceiver, filter, android.content.Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(updateReceiver, filter)
        }

        setContent {
            CosmicAppTheme {
                VipChartScreen(birthDataState.value) { finish() }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        updateReceiver?.let { unregisterReceiver(it) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VipChartScreen(birthData: JSONObject, onBack: () -> Unit) {
    val context = LocalContext.current
    val (chartState, setChartState) = remember { mutableStateOf<ChartData?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    val editLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val dataStr = result.data?.getStringExtra("birthData")
            if (dataStr != null) {
                try {
                    val newData = JSONObject(dataStr)
                    // Update our birthData and trigger a refresh
                    isLoading = true
                    errorMessage = null // Clear previous error
                    scope.launch {
                        try {
                            val resultChart = fetchFullChart(newData)
                            if (resultChart == null) {
                                errorMessage = "Server returned empty data or error. Check logs."
                            }
                            setChartState(resultChart)
                        } catch (e: Exception) {
                            errorMessage = "Failed to fetch chart data: ${e.localizedMessage ?: "Unknown error"}"
                        } finally {
                            isLoading = false
                        }
                    }
                } catch(e: Exception){
                    errorMessage = "Error parsing updated birth data: ${e.localizedMessage ?: "Unknown error"}"
                    e.printStackTrace()
                }
            }
        }
    }

    LaunchedEffect(birthData) {
        isLoading = true
        errorMessage = null
        try {
            val result = fetchFullChart(birthData)
            if (result == null) {
                errorMessage = "Server returned empty data. Please try again."
            }
            setChartState(result)
        } catch (e: Exception) {
            android.util.Log.e("VipChart", "Initial load failed", e)
            errorMessage = "Connect Error (${e.javaClass.simpleName}): ${e.localizedMessage ?: "Unknown issue"}"
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
                TopAppBar(
                    modifier = Modifier.statusBarsPadding(),
                    title = {
                    Column {
                        Text(
                            "ராசி & நவாம்ச கட்டங்கள்",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = NeonCyan
                        )
                        Text(
                            birthData.optString("name", "User"),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = NeonCyan) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .navigationBarsPadding()
            .background(Brush.verticalGradient(listOf(DeepSpaceNavy, PremiumBlue)))) {

            // --- New Client Info Header ---
            ClientInfoHeader(birthData) {
                val intent = Intent(context, IntakeActivity::class.java).apply {
                    putExtra("isEditMode", true)
                    putExtra("existingData", birthData.toString())
                }
                editLauncher.launch(intent)
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NeonCyan)
                }
            } else if (chartState == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = errorMessage ?: "Data Fetch Failed",
                            color = Color.Red,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = {
                            isLoading = true
                            errorMessage = null
                            scope.launch {
                                try {
                                    val result = fetchFullChart(birthData)
                                    if (result == null) {
                                        errorMessage = "Server returned empty data or error. Check logs."
                                    }
                                    setChartState(result)
                                } catch (e: Exception) {
                                    android.util.Log.e("VipChart", "Retry failed", e)
                                    errorMessage = "Fetch Failed (${e.javaClass.simpleName}): ${e.localizedMessage ?: "Unknown issue"}"
                                } finally {
                                    isLoading = false
                                }
                            }
                        }, colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)) {
                            Text("Retry / மீண்டும் முயற்சி செய்", color = Color.Black)
                        }
                    }
                }
            } else {
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    edgePadding = 16.dp,
                    divider = {},
                    indicator = { tabPositions ->
                        TabRowDefaults.Indicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = NeonCyan
                        )
                    }
                ) {
                    val tabs = listOf("கட்டங்கள்", "கிரக நிலைகள்", "தசா புக்தி")
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    title,
                                    fontSize = 13.sp,
                                    fontWeight = if(selectedTab == index) FontWeight.ExtraBold else FontWeight.Medium,
                                    color = if(selectedTab == index) NeonCyan else Color.White.copy(alpha = 0.6f)
                                )
                            }
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        0 -> ChartsTab(chartState!!, birthData)
                        1 -> PlanetGridTab(chartState!!)
                        2 -> if (chartState?.dasha != null) DashaListTab(chartState!!.dasha!!)
                    }
                }
            }
        }
    }
}

@Composable
fun ChartsTab(data: ChartData, birthData: JSONObject) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {

        Text("ராசி கட்டம் (Rasi Chart)", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = NeonCyan)
        Spacer(Modifier.height(12.dp))
        SouthIndianGridEnhanced(data.planets ?: emptyList(), data.houses?.ascendantDetails?.signName ?: "", "Rasi", birthData, data.panchanga?.nakshatra?.name ?: "")

        Spacer(Modifier.height(32.dp))

        Text("நவாம்ச கட்டம் (Navamsa - D9)", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = NeonCyan)
        Spacer(Modifier.height(12.dp))
        SouthIndianGridEnhanced(data.navamsa?.planets ?: emptyList(), data.navamsa?.ascendantSign ?: "", "Navamsa", birthData, "")

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
fun SouthIndianGridEnhanced(planets: List<Planet>, ascSign: String, title: String, birthData: JSONObject, starName: String) {
    val signNames = listOf("Aries", "Taurus", "Gemini", "Cancer", "Leo", "Virgo", "Libra", "Scorpio", "Sagittarius", "Capricorn", "Aquarius", "Pisces")
    val gridMap = listOf(11, 0, 1, 2, 10, -1, -1, 3, 9, -1, -1, 4, 8, 7, 6, 5)
    val ascIdx = if (ascSign.isNotEmpty()) signNames.indexOf(ascSign) else -1

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(GridBg, RoundedCornerShape(8.dp))
            .border(2.dp, NeonCyan, RoundedCornerShape(8.dp))
    ) {
        // Decorative Borders for boxes
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cellW = w / 4
            val cellH = h / 4

            // Vertical lines
            for (i in 1..3) {
                if (i == 2) {
                    // Skip center 2x2
                    drawLine(NeonCyan.copy(alpha=0.3f), Offset(i * cellW, 0f), Offset(i * cellW, cellH), strokeWidth = 1.dp.toPx())
                    drawLine(NeonCyan.copy(alpha=0.3f), Offset(i * cellW, 3 * cellH), Offset(i * cellW, h), strokeWidth = 1.dp.toPx())
                } else {
                    drawLine(NeonCyan.copy(alpha=0.3f), Offset(i * cellW, 0f), Offset(i * cellW, h), strokeWidth = 1.dp.toPx())
                }
            }

            // Horizontal lines
            for (i in 1..3) {
                if (i == 2) {
                    // Skip center 2x2
                    drawLine(NeonCyan.copy(alpha=0.3f), Offset(0f, i * cellH), Offset(cellW, i * cellH), strokeWidth = 1.dp.toPx())
                    drawLine(NeonCyan.copy(alpha=0.3f), Offset(3 * cellW, i * cellH), Offset(w, i * cellH), strokeWidth = 1.dp.toPx())
                } else {
                    drawLine(NeonCyan.copy(alpha=0.3f), Offset(0f, i * cellH), Offset(w, i * cellH), strokeWidth = 1.dp.toPx())
                }
            }

            // Central Area Decor (Pillar-like / Unified Center)
            val centralPadding = 2.dp.toPx()
            val rectPath = Path().apply {
                moveTo(cellW + centralPadding, cellH + centralPadding)
                lineTo(3 * cellW - centralPadding, cellH + centralPadding)
                lineTo(3 * cellW - centralPadding, 3 * cellH - centralPadding)
                lineTo(cellW + centralPadding, 3 * cellH - centralPadding)
                close()
            }

            // Draw a subtle background for the center "pillar" area
            drawPath(
                path = rectPath,
                brush = Brush.verticalGradient(listOf(Color(0xFFFFF9C4).copy(alpha = 0.5f), Color(0xFFFBC02D).copy(alpha = 0.1f)))
            )

            // Central Border (Thicker)
            drawPath(rectPath, NeonCyan, style = Stroke(width = 2.dp.toPx()))
        }

        // Contents
        Column(Modifier.fillMaxSize()) {
            for (row in 0..3) {
                Row(Modifier.weight(1f)) {
                    for (col in 0..3) {
                        val pos = row * 4 + col
                        val signIdx = gridMap[pos]

                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            if (signIdx != -1) {
                                val signEn = signNames[signIdx]
                                val signNo = signIdx + 1
                                val occupants = mutableListOf<String>()
                                if (signEn == ascSign) occupants.add("As")
                                planets.filter { it.signName == signEn }.forEach { occupants.add(it.name) }

                                Column(Modifier.fillMaxSize().padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(signNo.toString(), fontSize = 10.sp, color = TraditionalRed.copy(0.6f), modifier = Modifier.align(Alignment.Start))

                                    Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                        occupants.forEach { pName ->
                                            val fontSize = if (occupants.size > 3) 10.sp else 12.sp
                                            Text(
                                                text = planetAbbrTamil[pName] ?: pName.take(3),
                                                fontSize = fontSize,
                                                fontWeight = FontWeight.Bold,
                                                color = if(pName == "As") Color.Blue else Color.Black,
                                                lineHeight = (fontSize.value + 1).sp
                                            )
                                        }
                                    }
                                }
                            } else if (pos == 5) {
                                // Central Info Display (Spans 2x2 area 5,6,9,10 but we use box 5 as anchor)
                                Box(modifier = Modifier.fillMaxSize().offset(x = 0.dp), contentAlignment = Alignment.Center) {
                                    // Spanning 2 cells
                                }
                            }
                        }
                    }
                }
            }
        }

        // Overlay central text over the 2x2 hole
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val dob = "${birthData.optInt("day")}-${getMonthName(birthData.optInt("month"))}-${birthData.optInt("year")}"
                val tob = String.format("%02d:%02d", birthData.optInt("hour"), birthData.optInt("minute"))

                Text(dob, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                Text(tob, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                Spacer(Modifier.height(4.dp))
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = TraditionalRed)
                if (starName.isNotEmpty()) {
                    Text(starName, fontSize = 12.sp, color = Color.DarkGray, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

fun getMonthName(m: Int): String = listOf("", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")[m]

@Composable
fun PlanetGridTab(data: ChartData) {
    val rawPlanets = data.planets ?: emptyList()

    // Add Ascendant to the list
    val asc = data.houses?.ascendantDetails
    val planets = mutableListOf<Planet>()

    if (asc != null) {
        planets.add(Planet(
            name = "Ascendant",
            signName = asc.signName ?: "",
            degreeFormatted = asc.degreeFormatted ?: "",
            nakshatra = asc.nakshatra,
            nakshatraPada = asc.nakshatraPada,
            starLord = asc.starLord
        ))
    }
    planets.addAll(rawPlanets)

    val sun = rawPlanets.find { it.name == "Sun" }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp).background(Color.White, RoundedCornerShape(8.dp)).border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))) {
        // Table Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF2E7D32), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)) // Green Background
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val headerStyle = TextStyle(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center)
            Text("கிரகம்", modifier = Modifier.weight(1.5f), style = headerStyle)
            Text("பாகை", modifier = Modifier.weight(2.5f), style = headerStyle)
            Text("நட்சத்திரம்", modifier = Modifier.weight(2.5f), style = headerStyle)
            Text("பாதம்", modifier = Modifier.weight(1f), style = headerStyle)
            Text("ந அ", modifier = Modifier.weight(1f), style = headerStyle)
            Text("நிலை", modifier = Modifier.weight(2f), style = headerStyle)
        }

        // Data Rows
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(planets) { p ->
                val isCombust = if (sun != null && p.name != "Sun" && p.name != "Rahu" && p.name != "Ketu" && p.name != "Ascendant") {
                    // Logic for combustion - using a simple degree difference
                    val pLon = p.longitude
                    val sLon = sun.longitude
                    val diff = Math.min(Math.abs(pLon - sLon), 360 - Math.abs(pLon - sLon))
                    val limit = when(p.name) {
                        "Moon" -> 12.0
                        "Mars" -> 17.0
                        "Mercury" -> 13.0
                        "Jupiter" -> 11.0
                        "Venus" -> 9.0
                        "Saturn" -> 15.0
                        else -> 0.0
                    }
                    diff < limit
                } else false

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(0.5.dp, Color.LightGray)
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Planet Name (Red)
                    Row(modifier = Modifier.weight(1.5f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = planetAbbrTamil[p.name] ?: p.name.take(3),
                            color = Color.Red,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        if (p.isRetrograde) {
                            Text(" (வ)", color = Color.Red, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                        if (isCombust) {
                            Text(" (அ)", color = Color.Red, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    val detailStyle = TextStyle(color = Color.Blue, fontSize = 11.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)

                    // Degree (Blue)
                    Text(text = formatDegreeOnly(p.degreeFormatted), modifier = Modifier.weight(2.5f), style = detailStyle)

                    // Nakshatra (Blue)
                    val nakName = p.nakshatra ?: "-"
                    Text(text = nakName, modifier = Modifier.weight(2.5f), style = detailStyle)

                    // Pada (Blue)
                    Text(text = p.nakshatraPada.toString(), modifier = Modifier.weight(1f), style = detailStyle)

                    // Star Lord (Blue)
                    Text(text = planetAbbrTamil[p.starLord ?: ""] ?: p.starLord?.take(2) ?: "-", modifier = Modifier.weight(1f), style = detailStyle)

                    // Sign (Blue)
                    Text(text = signTamil[p.signName] ?: p.signName, modifier = Modifier.weight(2f), style = detailStyle)
                }
            }
        }
    }
}

fun formatDegreeOnly(degreeStr: String?): String {
    if (degreeStr == null) return ""
    // degreeStr is "Leo 15° 30' 45\"" or "Leo 15°30'45\""
    val regex = """(\d+°\s*\d+'\s*\d+")""".toRegex()
    val match = regex.find(degreeStr)
    return match?.value ?: degreeStr.split(" ").last()
}

@Composable
fun PlanetsTab(data: ChartData) {
    // Legacy - replaced by PlanetGridTab
    PlanetGridTab(data)
}

@Composable
fun DashaListTab(mahadashas: List<DashaPeriod>) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Box(Modifier.fillMaxWidth().background(ElectricBlue.copy(alpha = 0.2f)).padding(16.dp)) {
                Text("விம்ஷோத்தரி தசா புக்தி விபரங்கள்", color = NeonCyan, fontWeight = FontWeight.ExtraBold)
            }
        }
        items(mahadashas) { md ->
            DashaNodeInternal(md)
        }
    }
}

@Composable
fun DashaNodeInternal(period: DashaPeriod) {
    var expanded by remember { mutableStateOf(false) }
    val hasSub = !period.subPeriods.isNullOrEmpty()

    Column(Modifier.fillMaxWidth().animateContentSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = hasSub) { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val levelIndent = (period.level - 1) * 20
            Spacer(Modifier.width(levelIndent.dp))

            // Icon/Prefix based on level
            val iconColor = when(period.level) {
                1 -> TraditionalRed
                2 -> Color(0xFF2E7D32)
                3 -> Color(0xFF1976D2)
                else -> Color.DarkGray
            }

            Box(Modifier.size(32.dp).background(iconColor.copy(0.1f), CircleShape), contentAlignment = Alignment.Center) {
                val abbr = if (period.lord != null) (planetAbbrTamil[period.lord] ?: period.lord!!.take(2)) else "??"
                Text(abbr, color = iconColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                val lordName = if (period.lord != null) (planetTamil[period.lord] ?: period.lord!!) else "Unknown"
                Text(
                    text = "$lordName " + when(period.level) {
                        1 -> "மகா தசை"
                        2 -> "புக்தி"
                        3 -> "ஆந்தரம்"
                        4 -> "பிரத்யந்தரம்"
                        else -> "சிக்ஷ்ம"
                    },
                    fontWeight = if(period.level == 1) FontWeight.Bold else FontWeight.Medium,
                    fontSize = if(period.level == 1) 16.sp else 14.sp
                )
                val dateRange = if (period.start != null && period.end != null) {
                    "${period.start!!.take(10).replace("-", ".")} - ${period.end!!.take(10).replace("-", ".")}"
                } else "Date Unknown"
                Text(dateRange, fontSize = 11.sp, color = Color.Gray)
            }

            if (hasSub) {
                Icon(
                    if (expanded) Icons.Default.ArrowBack else Icons.Default.ArrowBack,
                    contentDescription = null,
                    tint = Color.Gray
                )
            }
        }

        if (expanded && hasSub) {
            period.subPeriods?.forEach { child ->
                DashaNodeInternal(child)
            }
            Divider(Modifier.padding(start = ((period.level) * 20).dp), color = Color.White.copy(alpha = 0.05f))
        }
        if (period.level == 1) {
            Divider(color = Color.White.copy(alpha = 0.1f))
        }
    }
}

private suspend fun fetchFullChart(birthData: JSONObject): ChartData? = withContext(Dispatchers.IO) {
    try {
        val payload = com.google.gson.JsonObject().apply {
            addProperty("date", String.format("%04d-%02d-%02d", birthData.optInt("year"), birthData.optInt("month"), birthData.optInt("day")))
            addProperty("time", String.format("%02d:%02d", birthData.optInt("hour"), birthData.optInt("minute")))
            addProperty("lat", birthData.optDouble("latitude", 13.0827))
            addProperty("lng", birthData.optDouble("longitude", 80.2707))
            addProperty("timezone", birthData.optDouble("timezone", 5.5))
        }

        android.util.Log.d("VipChart", "Payload (POST): $payload")
        
        // Ensure the API client is using the latest base URL if it was updated
        val api = com.astroluna.data.api.ApiClient.api
        
        // Strategy 1: POST (Standard)
        var response = api.getRasiEngBirthChart(payload)
        
        // Strategy 2: GET Fallback if POST fails (Cloudflare often blocks large POSTs)
        if (!response.isSuccessful) {
             val code = response.code()
             android.util.Log.w("VipChart", "POST failed (Code: $code), trying GET fallback...")
             response = com.astroluna.data.api.ApiClient.api.getRasiEngBirthChartFallback(
                 date = String.format("%04d-%02d-%02d", birthData.optInt("year"), birthData.optInt("month"), birthData.optInt("day")),
                 time = String.format("%02d:%02d", birthData.optInt("hour"), birthData.optInt("minute")),
                 lat = birthData.optDouble("latitude", 13.0827),
                 lng = birthData.optDouble("longitude", 80.2707),
                 timezone = birthData.optDouble("timezone", 5.5)
             )
        }

        if (response.isSuccessful && response.body() != null) {
            val chartResponse = response.body()!!
            if (chartResponse.success) {
                android.util.Log.d("VipChart", "Fetch OK")
                return@withContext chartResponse.data
            } else {
                android.util.Log.e("VipChart", "API Success but 'success'=false")
                throw Exception("Server failed to calculate data (success is false)")
            }
        } else {
             val errorBody = response.errorBody()?.string() ?: "Empty body"
             android.util.Log.e("VipChart", "API Error: ${response.code()} - $errorBody")
             throw Exception("HTTP ${response.code()}: $errorBody")
        }
    } catch (e: Exception) {
        android.util.Log.e("VipChart", "Network/Fetch Exception: ${e.message}", e)
        throw e // Rethrow so the UI can capture the exact message
    }
}

@Composable
fun ClientInfoHeader(birthData: JSONObject, onEdit: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = birthData.optString("name", "User Details"),
                    style = MaterialTheme.typography.titleLarge,
                    color = NeonCyan,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                val dob = "${birthData.optInt("day")}/${birthData.optInt("month")}/${birthData.optInt("year")}"
                val tob = String.format("%02d:%02d", birthData.optInt("hour"), birthData.optInt("minute"))
                val gender = birthData.optString("gender", "Male")
                val place = birthData.optString("city", "Unknown Place")

                Text(
                    text = "$dob | $tob | $gender",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f)
                )
                Text(
                    text = place,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }

            IconButton(
                onClick = onEdit,
                modifier = Modifier
                    .background(NeonCyan.copy(alpha = 0.2f), CircleShape)
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit Details",
                    tint = NeonCyan,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}