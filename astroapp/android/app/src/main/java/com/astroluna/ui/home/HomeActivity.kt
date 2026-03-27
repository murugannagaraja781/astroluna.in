package com.astroluna.ui.home

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.astroluna.R
import com.astroluna.data.local.TokenManager
import com.astroluna.data.model.Astrologer
import com.astroluna.data.remote.SocketManager

import com.astroluna.ui.wallet.WalletActivity
import com.astroluna.utils.showErrorAlert
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import com.astroluna.ui.dashboard.RasiDetailDialog

class HomeActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "HomeActivity"
        private val SERVER_URL = com.astroluna.utils.Constants.SERVER_URL
    }

    private lateinit var tokenManager: TokenManager

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // State Holders
    private val _walletBalance = MutableStateFlow(0.0)
    private val _horoscope = MutableStateFlow("Loading Horoscope...")
    private val _astrologers = MutableStateFlow<List<Astrologer>>(emptyList())
    private val _isLoading = MutableStateFlow(true)
    private val _userSession = MutableStateFlow<com.astroluna.data.model.AuthResponse?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Legacy ThemeManager removed.

        tokenManager = TokenManager(this)

        setContent {
            // Retrieve Page Overrides
            val context = androidx.compose.ui.platform.LocalContext.current
            val pageName = "HomeActivity"

            // Default Colors (if not set, returns 0/Transparent/Default)
            val customBg = com.astroluna.utils.PageThemeManager.getPageColor(context, pageName, com.astroluna.utils.PageThemeManager.ATTR_BG, 0)
            val customCard = com.astroluna.utils.PageThemeManager.getPageColor(context, pageName, com.astroluna.utils.PageThemeManager.ATTR_CARD, 0)
            val customFont = com.astroluna.utils.PageThemeManager.getPageColor(context, pageName, com.astroluna.utils.PageThemeManager.ATTR_FONT, 0)
            val customBtn = com.astroluna.utils.PageThemeManager.getPageColor(context, pageName, com.astroluna.utils.PageThemeManager.ATTR_BUTTON, 0)

            // Dynamic Cosmic Theme
            com.astroluna.ui.theme.CosmicAppTheme {
                val balance by _walletBalance.collectAsState()
                val horoscope by _horoscope.collectAsState()
                val astrologers by _astrologers.collectAsState()
                val isLoading by _isLoading.collectAsState()
                val session by _userSession.collectAsState()
                val referralCode = session?.referralCode ?: ""

                var selectedRasiItem by remember { mutableStateOf<ComposeRasiItem?>(null) }

                // Dialog removed in favor of Activity navigation


                HomeScreen(
                    walletBalance = balance,
                    referralCode = referralCode,
                    horoscope = horoscope,
                    astrologers = astrologers,
                    isLoading = isLoading,
                    onWalletClick = {
                        startActivity(Intent(this, com.astroluna.ui.wallet.WalletActivity::class.java))
                    },
                    onChatClick = { astro ->
                        val intent = Intent(this, com.astroluna.ui.intake.IntakeActivity::class.java).apply {
                            putExtra("partnerId", astro.userId)
                            putExtra("partnerName", astro.name)
                            putExtra("partnerImage", astro.image)
                            putExtra("type", "chat")
                        }
                        startActivity(intent)
                    },
                    onCallClick = { astro, type ->
                        val intent = Intent(this, com.astroluna.ui.intake.IntakeActivity::class.java).apply {
                            putExtra("partnerId", astro.userId)
                            putExtra("partnerName", astro.name)
                            putExtra("partnerImage", astro.image)
                            putExtra("type", type)
                        }
                        startActivity(intent)
                    },
                    onRasiClick = { item ->
                        // Launch RasipalanActivity with filtering extras
                        val intent = Intent(this, com.astroluna.ui.rasipalan.RasipalanActivity::class.java).apply {
                            putExtra("signId", item.id)
                            putExtra("signName", item.name)
                        }
                        startActivity(intent)
                    },
                    onLogoutClick = {
                        tokenManager.clearSession()
                        val intent = Intent(this, com.astroluna.ui.auth.LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    },
                    onDrawerItemClick = { item ->
                        when(item) {
                            "Logout" -> {
                                tokenManager.clearSession()
                                val intent = Intent(this, com.astroluna.ui.auth.LoginActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(intent)
                                finish()
                            }
                            "Settings" -> {
                                startActivity(Intent(this, com.astroluna.ui.settings.SettingsActivity::class.java))
                            }
                            "Profile" -> {
                                startActivity(Intent(this, com.astroluna.ui.profile.UserProfileActivity::class.java))
                            }
                            "Astrologer Registration" -> {
                                startActivity(Intent(this, com.astroluna.ui.astro.AstrologerRegistrationActivity::class.java))
                            }
                            else -> {
                                // Handle Navigation
                                // Toast.makeText(context, "$item Clicked", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onServiceClick = { serviceName ->
                        handleServiceClick(serviceName)
                    }
                )
            }
        }

        // Logout & Socket Logic kept same/adapted
        // Note: Logout button is not yet in HomeScreen (User didn't explicitly ask for it, but should probably be in Drawer or Profile?)
        // The original code had a logout button in XML. I will assume it's okay to omit for this "Screen" demo, or I can add it to TopBar later.

        // Load data
        _userSession.value = tokenManager.getUserSession()
        loadWalletBalance()
        loadDailyHoroscope()
        loadAstrologers()

        // Setup Socket for real-time updates
        setupSocket()
    }

    // Composable State (Must be hoisted or handled via callback to Compose)
    // Since this is an Activity hosting Compose content, the easiest way is to push state to the Compose root.
    // However, we are declaring `showRasiDialog` inside the Activity class which is not Composable.
    // We should move `showRasiDialog` logic into the `setContent`.

    /* Removed legacy showRasiDialog function */

    private fun loadWalletBalance() {
        val session = tokenManager.getUserSession()
        val balance = session?.walletBalance ?: 0.0
        _walletBalance.value = balance
    }

    private fun refreshWalletBalance() {
        val userId = tokenManager.getUserSession()?.userId ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = com.astroluna.data.api.ApiClient.api.getUserProfile(userId)
                if (response.isSuccessful && response.body() != null) {
                    val user = response.body()!!
                    val balance = user.walletBalance ?: 0.0
                    tokenManager.saveUserSession(user)
                    _userSession.value = user
                    _walletBalance.value = balance
                }
            } catch (e: Exception) {
                Log.e(TAG, "Balance refresh failed", e)
            }
        }
    }

    private fun loadDailyHoroscope() {
        lifecycleScope.launch {
            try {
                _horoscope.value = fetchHoroscope()
            } catch (e: Exception) {
                Log.e(TAG, "Error loading horoscope", e)
                _horoscope.value = "Good progress will occur today as Chandrashtama has passed."
            }
        }
    }

    private suspend fun fetchHoroscope(): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$SERVER_URL/api/rasi-eng/horoscope/daily")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val resString = response.body?.string() ?: "[]"
                try {
                    val jsonObj = org.json.JSONObject(resString)
                    val dataArray = if (jsonObj.has("data")) {
                        jsonObj.getJSONArray("data")
                    } else if (jsonObj.has("list")) {
                        jsonObj.getJSONArray("list")
                    } else {
                        null
                    }

                    if (dataArray != null && dataArray.length() > 0) {
                        // Return the first one or find a default
                        return@withContext dataArray.getJSONObject(0).optString("prediction_ta", "Today is a good day!")
                    }

                    // Fallback to old format
                    jsonObj.optString("prediction_ta", jsonObj.optString("content", "Today is a good day!"))
                } catch (e: Exception) {
                    try {
                        val jsonArray = org.json.JSONArray(resString)
                        if (jsonArray.length() > 0) {
                            jsonArray.getJSONObject(0).optString("prediction_ta", "Today is a good day!")
                        } else {
                            "Today is a good day!"
                        }
                    } catch (e2: Exception) {
                        "Today is a good day!"
                    }
                }
            } else {
                "Today is a good day!"
            }
        }
    }


    private fun loadAstrologers() {
        _isLoading.value = true
        lifecycleScope.launch {
            try {
                val list = fetchAstrologers()
                _astrologers.value = list
            } catch (e: Exception) {
                Log.e(TAG, "Error loading astrologers", e)
                // showErrorAlert("Failed to load astrologers") // Toast
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun fetchAstrologers(): List<Astrologer> = withContext(Dispatchers.IO) {
        val socket = SocketManager.getSocket()
        val result = mutableListOf<Astrologer>()

        // Fallback or Initial Load via HTTP
        try {
            val request = Request.Builder()
                .url("$SERVER_URL/api/astrology/astrologers")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "{}")
                    val arr = json.optJSONArray("astrologers") ?: JSONArray()
                    for (i in 0 until arr.length()) {
                        result.add(parseAstrologer(arr.getJSONObject(i)))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "HTTP fallback failed", e)
        }
        result.sortWith(
            compareByDescending<Astrologer> {
                it.isOnline || it.isChatOnline || it.isAudioOnline || it.isVideoOnline
            }.thenByDescending { it.experience }
        )
        result
    }

    private fun parseAstrologer(json: JSONObject): Astrologer {
        val skillsArr = json.optJSONArray("skills")
        val skills = mutableListOf<String>()
        if (skillsArr != null) {
            for (i in 0 until skillsArr.length()) {
                skills.add(skillsArr.getString(i))
            }
        }

        return Astrologer(
            userId = json.optString("userId", ""),
            name = json.optString("name", "Astrologer"),
            phone = json.optString("phone", ""),
            skills = skills,
            price = json.optInt("price", 15),
            isOnline = json.optBoolean("isOnline", false),
            isChatOnline = json.optBoolean("isChatOnline", false),
            isAudioOnline = json.optBoolean("isAudioOnline", false),
            isVideoOnline = json.optBoolean("isVideoOnline", false),
            image = json.optString("image", ""),
            experience = json.optInt("experience", 0),
            isVerified = json.optBoolean("isVerified", false),
            walletBalance = json.optDouble("walletBalance", 0.0),
            isBusy = json.optBoolean("isBusy", false)
        )
    }

    private fun setupSocket() {
        SocketManager.init()
        val socket = SocketManager.getSocket()
        val session = tokenManager.getUserSession()
        if (session != null) {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                val fcmToken = if (task.isSuccessful) task.result else null
                SocketManager.registerUser(session.userId ?: "", fcmToken)
            }
        }

        socket?.on("astro-list") { args ->
            val data = args[0] as JSONObject
            val arr = data.optJSONArray("list") ?: JSONArray()
            val list = mutableListOf<Astrologer>()
            for (i in 0 until arr.length()) {
                list.add(parseAstrologer(arr.getJSONObject(i)))
            }
            // Sort: Online first (Any online status), then Experience
            val sortedList = list.sortedWith(
                compareByDescending<Astrologer> {
                    it.isOnline || it.isChatOnline || it.isAudioOnline || it.isVideoOnline
                }.thenByDescending { it.experience }
            )
            _astrologers.value = sortedList
            _isLoading.value = false
        }

        socket?.on("astrologer-update") { args ->
            val data = args[0] as JSONArray
            val list = mutableListOf<Astrologer>()
            for (i in 0 until data.length()) {
                list.add(parseAstrologer(data.getJSONObject(i)))
            }
            val sortedList = list.sortedWith(
                compareByDescending<Astrologer> {
                    it.isOnline || it.isChatOnline || it.isAudioOnline || it.isVideoOnline
                }.thenByDescending { it.experience }
            )
            lifecycleScope.launch(Dispatchers.Main) {
                _astrologers.value = sortedList
            }
        }

        socket?.on("astro-status-change") { args ->
            // Update individual status in list
            val data = args[0] as JSONObject
            val userId = data.optString("userId")

            // Check for specific service fields or fallback to master online
            val service = data.optString("service") // "chat", "call", "video"
            val isEnabled = data.optBoolean("isEnabled", false)
            val isMasterOnline = data.optBoolean("isOnline", false)

            val currentList = _astrologers.value.toMutableList()
            val index = currentList.indexOfFirst { it.userId == userId }
            if (index != -1) {
                val astro = currentList[index]
                val updatedAstro = if (service.isNotEmpty()) {
                    when (service) {
                        "chat" -> astro.copy(isChatOnline = isEnabled)
                        "call", "audio" -> astro.copy(isAudioOnline = isEnabled)
                        "video" -> astro.copy(isVideoOnline = isEnabled)
                        else -> astro
                    }.copy(
                        // Re-evaluate master online status
                        isOnline = isEnabled || (if(service=="chat") false else astro.isChatOnline) ||
                                              (if(service=="call") false else astro.isAudioOnline) ||
                                              (if(service=="video") false else astro.isVideoOnline)
                    )
                } else {
                    astro.copy(isOnline = isMasterOnline)
                }

                currentList[index] = updatedAstro
                _astrologers.value = currentList
            }
        }

        socket?.on("wallet-update") { args ->
            val data = args[0] as JSONObject
            val balance = data.optDouble("balance", 0.0)
            _walletBalance.value = balance
            tokenManager.updateWalletBalance(balance)
            _userSession.value = tokenManager.getUserSession()
        }

        socket?.emit("get-astrologers")
    }

    private fun startChat(astro: Astrologer) {
        initiateSession(astro.userId, "chat", astro.name, astro.image)
    }

    private fun startCall(astro: Astrologer, type: String) {
        initiateSession(astro.userId, type, astro.name, astro.image)
    }

    private fun initiateSession(astrologerId: String, type: String, astroName: String, astroImage: String) {
        val intent = Intent(this, com.astroluna.ui.intake.IntakeActivity::class.java).apply {
            putExtra("partnerId", astrologerId)
            putExtra("partnerName", astroName)
            putExtra("partnerImage", astroImage)
            putExtra("type", type)
        }
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        loadWalletBalance()
        refreshWalletBalance()
        // Ensure astrologer list is fresh when returning to the screen
        SocketManager.getSocket()?.emit("get-astrologers")
    }

    override fun onDestroy() {
        super.onDestroy()
        // SocketManager.disconnect() - kept same
    }

    private fun handleServiceClick(serviceName: String) {
        when (serviceName.replace("\n", " ")) {
            "Free Horoscope" -> {
                val intent = Intent(this, com.astroluna.ui.horoscope.FreeHoroscopeActivity::class.java)
                startActivity(intent)
            }
            "Horoscope Match" -> {
                val intent = Intent(this, com.astroluna.ui.intake.IntakeActivity::class.java).apply {
                    putExtra("type", "match")
                }
                startActivity(intent)
            }
            "Daily Horoscope" -> {
                val intent = Intent(this, com.astroluna.ui.rasipalan.RasipalanActivity::class.java)
                startActivity(intent)
            }
            "Astro Academy" -> {
                val intent = Intent(this, com.astroluna.ui.academy.AcademyActivity::class.java)
                startActivity(intent)
            }
            "Free Services" -> {
                android.app.AlertDialog.Builder(this)
                    .setTitle("Contact Us")
                    .setMessage("For free services, contact us at: info@astroluna.in")
                    .setPositiveButton("OK", null)
                    .show()
            }
            else -> {
                Toast.makeText(this, "$serviceName clicked", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

