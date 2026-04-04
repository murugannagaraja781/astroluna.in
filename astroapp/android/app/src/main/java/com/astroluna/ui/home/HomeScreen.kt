package com.astroluna.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.animation.core.*
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.saveable.rememberSaveable
import com.astroluna.utils.Localization
import com.astroluna.data.model.Astrologer
import androidx.compose.ui.text.style.BaselineShift
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.astroluna.R
import com.astroluna.ui.theme.*
import com.astroluna.ui.theme.CosmicAppTheme
import com.astroluna.ui.theme.CosmicGradients
import com.astroluna.ui.theme.CosmicColors
import com.astroluna.ui.theme.CosmicShapes
import coil.compose.AsyncImage
import com.astroluna.data.api.ApiClient
import com.astroluna.data.model.Banner
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding

import androidx.compose.foundation.ExperimentalFoundationApi

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BannerSection(banners: List<Banner>) {
    if (banners.isEmpty()) return

    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { banners.size })

    // Auto-scroll logic
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(5000) // 5 seconds
            if (banners.isNotEmpty()) {
                val nextPage = (pagerState.currentPage + 1) % banners.size
                pagerState.animateScrollToPage(nextPage)
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(bottom = 24.dp)
    ) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 0.dp),
            pageSpacing = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) { page ->
             val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
             val scale by animateFloatAsState(targetValue = if (pageOffset == 0f) 1f else 0.9f, label = "scale")
             val alpha by animateFloatAsState(targetValue = if (pageOffset == 0f) 1f else 0.6f, label = "alpha")

             val banner = banners[page]

            Card(
                shape = RoundedCornerShape(18.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, PeacockGreen.copy(alpha = 0.3f)),
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
                    .fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // 1. Dynamic Background Image
                    AsyncImage(
                        model = if (banner.imageUrl.startsWith("/")) "${com.astroluna.utils.Constants.SERVER_URL}${banner.imageUrl}" else banner.imageUrl,
                        contentDescription = banner.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // 2. Gradient Overlay for Readability
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)
                                )
                            )
                    )

                    // 3. Content Text
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(24.dp)
                            .fillMaxWidth(0.7f) // Limit width so text doesn't span full image
                    ) {
                        if (!banner.title.isNullOrEmpty()) {
                            Text(
                                text = banner.title,
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                lineHeight = 30.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        if (!banner.subtitle.isNullOrEmpty()) {
                            Text(
                                text = banner.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )}

                        // CTA Pill
                        if (!banner.ctaText.isNullOrEmpty()) {
                             Box(
                                 modifier = Modifier
                                     .background(PeacockGreen, RoundedCornerShape(50))
                                     .padding(horizontal = 16.dp, vertical = 8.dp)
                             ) {
                                 Text(
                                     text = banner.ctaText,
                                     style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                     color = RoyalMidnightBlue
                                 )
                             }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Indicators
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            repeat(banners.size) { iteration ->
                val color = if (pagerState.currentPage == iteration) PeacockGreen else PeacockGreen.copy(alpha = 0.2f)
                val width by animateDpAsState(targetValue = if (pagerState.currentPage == iteration) 24.dp else 8.dp, label = "dotWidth")

                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .height(6.dp)
                        .width(width)
                        .clip(RoundedCornerShape(50))
                        .background(color)
                )
            }
        }
    }
}



// Data class wrapper for Rasi to be used in Compose
data class ComposeRasiItem(val id: Int, val name: String, val iconRes: Int, val color: Color)

// Local color definitions removed to use Theme aliases (White)

// Helper for Premium Sacred Cards
@Composable
fun PremiumCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, colorResource(id = com.astroluna.R.color.surface_border)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), // Using custom shadow wrapper if possible, or high elevation
        modifier = modifier
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(22.dp),
                spotColor = colorResource(id = com.astroluna.R.color.card_shadow),
                ambientColor = colorResource(id = com.astroluna.R.color.card_shadow)
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        content()
    }
}

@Composable
fun HomeScreen(
    walletBalance: Double,
    referralCode: String = "",
    horoscope: String,
    astrologers: List<Astrologer>,
    isLoading: Boolean,
    onWalletClick: () -> Unit,
    onChatClick: (Astrologer) -> Unit,
    onCallClick: (Astrologer, String) -> Unit,
    onRasiClick: (ComposeRasiItem) -> Unit,
    onLogoutClick: () -> Unit,
    onDrawerItemClick: (String) -> Unit = {},
    onServiceClick: (String) -> Unit = {},
    isGuest: Boolean = false
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedFilter by remember { mutableStateOf("All") }
    // Language State (Default Tamil)
    var isTamil by rememberSaveable { mutableStateOf(true) }

    // Banners & Referral Stats State
    var banners by remember { mutableStateOf<List<Banner>>(emptyList()) }
    var refStats by remember { mutableStateOf<com.astroluna.data.model.ReferralStats?>(null) }
    val tokenManager = remember { com.astroluna.data.local.TokenManager(context) }
    val userId = remember { tokenManager.getUserSession()?.userId ?: "" }

    // Fetch Banners & Stats
    LaunchedEffect(Unit) {
        // Initial Fetch
        try {
            val response = ApiClient.api.getBanners()
            if (response.isSuccessful && response.body()?.ok == true) {
                banners = response.body()!!.banners
            }
            if (!isGuest && userId.isNotEmpty()) {
                val statsRes = ApiClient.api.getReferralStats(userId)
                if (statsRes.isSuccessful && statsRes.body()?.ok == true) {
                    refStats = statsRes.body()!!.stats
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Live Update Stats (Every 30s)
        if (!isGuest && userId.isNotEmpty()) {
            while (true) {
                kotlinx.coroutines.delay(30000)
                try {
                    val rRes = ApiClient.api.getReferralStats(userId)
                    if (rRes.isSuccessful && rRes.body()?.ok == true) {
                        refStats = rRes.body()!!.stats
                    }
                } catch (e: Exception) { }
            }
        }
    }

    // Logic to filter astrologers based on selection
    val filteredAstros = remember(selectedFilter, astrologers) {
        if (selectedFilter == "All") astrologers
        else astrologers.filter { astro ->
             // Match skill or name
             astro.skills.any { it.contains(selectedFilter, ignoreCase = true) } ||
             astro.name.contains(selectedFilter, ignoreCase = true)
        }
    }

    var showLowBalanceDialog by remember { mutableStateOf(false) }

    if (showLowBalanceDialog) {
        AlertDialog(
            onDismissRequest = { showLowBalanceDialog = false },
            title = { Text("Low Balance!", fontWeight = FontWeight.Bold, color = Color.Red) },
            text = {
                Column {
                    Text("Current session ended due to insufficient funds. Please recharge to continue.", color = CosmicAppTheme.colors.textPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Current Balance: ₹${walletBalance.toInt()}", fontWeight = FontWeight.Bold, color = CosmicAppTheme.colors.accent)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLowBalanceDialog = false
                        onWalletClick()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PeacockGreen)
                ) {
                    Text("Add Funds Now", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLowBalanceDialog = false }) {
                    Text("I'll do it later", color = CosmicAppTheme.colors.textSecondary)
                }
            },
            containerColor = CosmicAppTheme.colors.cardBg,
            shape = RoundedCornerShape(16.dp)
        )
    }

    fun checkBalanceAndProceed(action: () -> Unit) {
        if (!isGuest && walletBalance < 10) { // Skip check for guest (login handles it)
            showLowBalanceDialog = true
        } else {
            action()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(
                onItemClick = { item ->
                    scope.launch { drawerState.close() }
                    onDrawerItemClick(item)
                    if (item == "Logout") onLogoutClick()
                },
                onClose = { scope.launch { drawerState.close() } }
            )
        }
    ) {
        Scaffold(
            containerColor = RoyalMidnightBlue,
            topBar = {
                HomeTopBar(
                    balance = walletBalance,
                    onWalletClick = onWalletClick,
                    onMenuClick = { scope.launch { drawerState.open() } },
                    isGuest = isGuest,
                    isTamil = isTamil,
                    onToggleLanguage = { isTamil = !isTamil }
                )
            },
            bottomBar = {
                Column(modifier = Modifier.navigationBarsPadding()) {
                    // STICKY FOOTER: Dual Yellow Buttons
                    val showFooter = selectedTab == 0 // Only show on Home tab
                    if (showFooter) {
                    StickyFooterButtons(
                        isGuest = isGuest,
                        onTabSelected = { selectedTab = it },
                        onLoginClick = onWalletClick
                    )
                }
                    HomeBottomBar(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it }
                    )
                }
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                // 🌌 1. COSMIC BACKGROUND & STARS
                Box(modifier = Modifier.fillMaxSize().background(CosmicAppTheme.backgroundBrush))
                StarField()

                if (selectedTab == 5) {
                    ReferralDashboard(
                        referralCode = referralCode,
                        isTamil = isTamil
                    )
                } else {
                    // Content Layer
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(bottom = 16.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Transparent) // Let gradient show through
                    ) {
                        // ... existing items ...
                        if (selectedTab == 0) {
                            item { TopServicesSection(onServiceClick) }
                        }
                        if (selectedTab == 0) {
                            item { DailyHoroscopeCard(horoscope) }
                            if (!isGuest && referralCode.isNotEmpty()) {
                                item { ReferAndEarnSection(referralCode, refStats) }
                            }
                        }
                        if (selectedTab == 0) {
                            item { BannerSection(banners) }
                        }
                        if (selectedTab == 0) {
                            item {
                                Text(
                                    text = Localization.get("horoscope", isTamil),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = CosmicAppTheme.colors.accent,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                            item { RasiGridSection(onRasiClick) }
                        }
                        item { CustomerStoriesSection() }
                        item {
                            val title = when(selectedTab) {
                                1 -> Localization.get("chat_services", isTamil)
                                2 -> Localization.get("video_call", isTamil)
                                3 -> Localization.get("audio_call", isTamil)
                                else -> Localization.get("premium_consultation", isTamil)
                            }
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge,
                                color = CosmicAppTheme.colors.accent,
                                modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
                            )
                        }
                        if (selectedTab != 0) {
                            item {
                                FilterBar(
                                    filters = listOf("All", "Love", "Career", "Finance", "Marriage", "Health", "Education"),
                                    selectedFilter = selectedFilter,
                                    onFilterSelected = { selectedFilter = it }
                                )
                            }
                        }
                        if (isLoading) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = PeacockGreen)
                                }
                            }
                        } else {
                            items(filteredAstros) { astro ->
                                AstrologerCard(
                                    astro = astro,
                                    onChatClick = { selectedAstro -> checkBalanceAndProceed { onChatClick(selectedAstro) } },
                                    onCallClick = { selectedAstro, type -> checkBalanceAndProceed { onCallClick(selectedAstro, type) } },
                                    selectedTab = selectedTab
                                )
                            }
                        }
                        if (selectedTab == 0) {
                            item { SupportAndPoliciesSection() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SupportAndPoliciesSection() {
    val context = LocalContext.current
    val baseUrl = "https://astroluna.in" // Update to your actual domain

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Policies & Support",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            PolicyLink("Return Policy", "$baseUrl/return-policy.html", context)
            PolicyLink("Shipping Policy", "$baseUrl/shipping-policy.html", context)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            PolicyLink("Privacy Policy", "$baseUrl/privacy-policy.html", context)
            PolicyLink("Terms & Conditions", "$baseUrl/terms-condition.html", context)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            PolicyLink("Refund Policy", "$baseUrl/refund-cancellation-policy.html", context)
            PolicyLink("Delete Account", "$baseUrl/delete-account.html", context)
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Need Help? kalpanajomr@gmail.com",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray
        )
        Text(
            text = "© 2026 Astroluna. All Rights Reserved.",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray.copy(alpha=0.6f)
        )
    }
}

@Composable
fun ReferralDashboard(referralCode: String, isTamil: Boolean) {
    var stats by remember { mutableStateOf<com.astroluna.data.model.ReferralStats?>(null) }
    var referrals by remember { mutableStateOf<com.astroluna.data.model.ReferralGroups?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val tokenManager = remember { com.astroluna.data.local.TokenManager(context) }
    val userId = remember { tokenManager.getUserSession()?.userId ?: "" }

    LaunchedEffect(Unit) {
        try {
            val response = ApiClient.api.getReferralStats(userId)
            if (response.isSuccessful && response.body()?.ok == true) {
                stats = response.body()!!.stats
                referrals = response.body()!!.referrals
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    var isWithdrawing by remember { mutableStateOf(false) }
    var withdrawalAmount by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    if (isWithdrawing) {
        AlertDialog(
            onDismissRequest = { isWithdrawing = false },
            title = { Text("Withdraw Referral Earnings") },
            text = {
                Column {
                    Text("Available: ₹${stats?.withdrawableAmount ?: 0}")
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = withdrawalAmount,
                        onValueChange = { if (it.all { char -> char.isDigit() }) withdrawalAmount = it },
                        label = { Text("Amount (Min ₹1000)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = withdrawalAmount.toIntOrNull() ?: 0
                        if (amt >= 1000 && amt <= (stats?.withdrawableAmount ?: 0)) {
                            scope.launch {
                                try {
                                    val body = com.google.gson.JsonObject().apply {
                                        addProperty("userId", userId)
                                        addProperty("amount", amt)
                                    }
                                    val res = ApiClient.api.withdrawReferral(body)
                                    if (res.isSuccessful && res.body()?.get("ok")?.asBoolean == true) {
                                        android.widget.Toast.makeText(context, "Withdrawal Requested!", android.widget.Toast.LENGTH_SHORT).show()
                                        isWithdrawing = false
                                        // Refresh stats
                                        val refresh = ApiClient.api.getReferralStats(userId)
                                        if (refresh.isSuccessful) stats = refresh.body()?.stats
                                    } else {
                                        val err = res.body()?.get("error")?.asString ?: "Failed"
                                        android.widget.Toast.makeText(context, err, android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "Error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            android.widget.Toast.makeText(context, "Invalid Amount", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PeacockGreen)
                ) {
                    Text("Withdraw")
                }
            },
            dismissButton = {
                TextButton(onClick = { isWithdrawing = false }) { Text("Cancel") }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            Text(
                text = if (isTamil) "பரிந்துரை டாஷ்போர்டு" else "Referral Dashboard",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }

        if (isLoading) {
            item {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PeacockGreen)
                }
            }
        } else {
            // New: Earnings Section
            item {
                ReferralEarningsCard(
                    earned = stats?.referralEarnings ?: 0,
                    withdrawable = stats?.withdrawableAmount ?: 0,
                    isTamil = isTamil,
                    onWithdrawClick = {
                        withdrawalAmount = (stats?.withdrawableAmount ?: 1000).toString()
                        isWithdrawing = true
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Stats Grid
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Level 1", stats?.level1Count ?: 0, Modifier.weight(1f), Color(0xFF6A1B9A))
                    StatCard("Level 2", stats?.level2Count ?: 0, Modifier.weight(1f), Color(0xFF4527A0))
                    StatCard("Level 3", stats?.level3Count ?: 0, Modifier.weight(1f), Color(0xFF283593))
                }
                Spacer(modifier = Modifier.height(12.dp))
                StatCard(
                    label = if (isTamil) "மொத்த பரிந்துரைகள்" else "Total Referrals",
                    value = stats?.totalReferrals ?: 0,
                    modifier = Modifier.fillMaxWidth(),
                    color = PeacockGreen
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                ReferAndEarnSection(referralCode)
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Referral Lists
            val groups = listOf(
                "Level 1 (Direct)" to (referrals?.l1 ?: emptyList()),
                "Level 2" to (referrals?.l2 ?: emptyList()),
                "Level 3" to (referrals?.l3 ?: emptyList())
            )

            groups.forEach { (title, users) ->
                item {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = PeacockGreen,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
                if (users.isEmpty()) {
                    item {
                        Text(
                            text = "No users found",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 8.dp, bottom = 12.dp)
                        )
                    }
                } else {
                    items(users) { user ->
                        ReferredUserItem(user)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(60.dp)) }
        }
    }
}

@Composable
fun StatCard(label: String, value: Int, modifier: Modifier = Modifier, color: Color) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.9f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
            Text(text = value.toString(), style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun ReferredUserItem(user: com.astroluna.data.model.ReferredUser) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.White.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, contentDescription = null, tint = Color.White)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = user.name, style = MaterialTheme.typography.bodyLarge, color = Color.White)
                Text(
                    text = "Joined: ${user.createdAt.take(10)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun ReferAndEarnSection(code: String, stats: com.astroluna.data.model.ReferralStats? = null) {
    val context = LocalContext.current
    val gradient = Brush.horizontalGradient(listOf(Color(0xFF6A1B9A), Color(0xFF4527A0)))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .background(gradient)
                .padding(20.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "பரிந்துரைத்து சம்பாதிக்கவும்", // Refer & Earn in Tamil
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "உங்கள் நண்பரை அழைக்கவும், நீங்கள் 3 நிலை வருமானத்தைப் பெறுவீர்கள்!",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f)
                )

                if (stats != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReferralStatChip("L1: ${stats.level1Count}", Color.White.copy(alpha = 0.2f))
                        ReferralStatChip("L2: ${stats.level2Count}", Color.White.copy(alpha = 0.2f))
                        ReferralStatChip("L3: ${stats.level3Count}", Color.White.copy(alpha = 0.2f))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = code,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        )
                    }

                    Button(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Download Astro Luna")
                                val shareUrl = "https://astroluna.in?ref=$code"
                                putExtra(Intent.EXTRA_TEXT, "Join me on Astro Luna! Get expert astrology consultations, daily horoscope and more! \n\nRegister via my link to get a join bonus: $shareUrl \n\nReferral Code: $code")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Referral Code"))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(text = "SHARE", color = Color(0xFF6A1B9A), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ReferralEarningsCard(
    earned: Int,
    withdrawable: Int,
    isTamil: Boolean,
    onWithdrawClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A237E)),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = if (isTamil) "பரிந்துரை வருவாய்" else "Referral Earnings",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(
                        text = if (isTamil) "மொத்த வருவாய்" else "Total Earned",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text("₹$earned", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (isTamil) "திரும்பப் பெறக்கூடியது" else "Withdrawable",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text("₹$withdrawable", color = PeacockGreen, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onWithdrawClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = PeacockGreen),
                enabled = withdrawable >= 1000,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (isTamil) "திரும்பப் பெறு (₹1000)" else "Withdraw (Min ₹1000)",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            if (withdrawable < 1000) {
               Text(
                   text = if (isTamil) "திரும்பப் பெற மேலும் ₹${1000 - withdrawable} தேவை" else "Need ₹${1000 - withdrawable} more to withdraw",
                   color = Color.White.copy(alpha = 0.5f),
                   style = MaterialTheme.typography.labelSmall,
                   modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)
               )
            }
        }
    }
}

@Composable
fun ReferralStatChip(text: String, bgColor: Color) {
    Box(
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun PolicyLink(label: String, url: String, context: android.content.Context) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium.copy(
            textDecoration = TextDecoration.Underline,
            fontWeight = FontWeight.Medium
        ),
        color = PeacockGreen,
        modifier = Modifier.clickable {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Cannot open link", Toast.LENGTH_SHORT).show()
            }
        }
    )
}

// --- 1. DRAWER ---
@Composable
fun AppDrawer(onItemClick: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    ModalDrawerSheet(
        drawerContainerColor = Color(0xFFF8F9FA), // Light Color (User Request)
        drawerContentColor = Color.DarkGray
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF8F9FA)) // Light BG
                .padding(24.dp)
        ) {
            // Close Button Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Close,
                        contentDescription = "Close Drawer",
                        tint = Color.Red // Red Color (User Request)
                    )
                }
            }

            // Profile Section
            Image(
                painter = painterResource(id = com.astroluna.R.drawable.ic_person_placeholder),
                contentDescription = "Profile",
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .border(2.dp, Color.Gray, CircleShape)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text("User Profile", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.DarkGray) // Strong Gray
            Text("Edit Profile", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Drawer Items
        val items = listOf("Home", "Profile", "Astrologer Registration", "Terms & Conditions", "Privacy Policy", "Settings", "Logout")
        items.forEach { item ->
            NavigationDrawerItem(
                label = {
                    Text(
                        text = item,
                        color = if(item == "Logout") Color.Red else Color.DarkGray, // Strong Gray / Red for logout might be nice, but strict request says "fornt garay color stonrg"
                        fontWeight = FontWeight.Bold
                    )
                },
                selected = false,
                onClick = {
                    when (item) {
                        "Terms & Conditions" -> {
                            onClose()
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://astroluna.in/terms-condition.html")))
                        }
                        "Privacy Policy" -> {
                            onClose()
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://astroluna.in/privacy-policy.html")))
                        }
                        else -> onItemClick(item)
                    }
                },
                colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(24.dp))
    }
}

// --- 2. HEADER ---
// --- 2. HEADER ---
@Composable
fun HomeTopBar(
    balance: Double,
    onWalletClick: () -> Unit,
    onMenuClick: () -> Unit,
    isGuest: Boolean = false,
    isTamil: Boolean,
    onToggleLanguage: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CosmicAppTheme.headerBrush)
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // LEFT: Menu + Logo + Title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
             IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Menu,
                    contentDescription = "Menu",
                    tint = Color.White
                )
             }

             // 2. Added App Logo
             Image(
                 painter = painterResource(id = com.astroluna.R.drawable.app_logo), // Updated resource reference
                 contentDescription = "Logo",
                 modifier = Modifier.size(32.dp)
             )

             Spacer(modifier = Modifier.width(8.dp))

             Text(
                text = "Astro Luna",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                ),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // RIGHT: Wallet (Premium Look)
        if (!isGuest) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.15f))
                    .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(50))
                    .clickable { onWalletClick() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AddCircle,
                        contentDescription = null,
                        tint = Color(0xFF7FDBFF), // Neon Cyan
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "₹${balance.toInt()}",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black),
                        color = Color.White
                    )
                }
            }
        } else {
            Button(
                onClick = onWalletClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f)),
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text(
                    text = "Login",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
        }
    }
}

// --- 3. RASI ITEM (Fitted BG + Border) ---
@Composable
fun RasiItemView(item: ComposeRasiItem, onClick: (ComposeRasiItem) -> Unit) {
    // Animation: Gentle Pulse (User Request: "icon show with animation")
    val infiniteTransition = rememberInfiniteTransition(label = "RasiPulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(84.dp)
            .clickable { onClick(item) }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(72.dp) // Restored Original Size
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .background(item.color.copy(alpha = 0.12f), CosmicShapes.ZodiacShape)
                .border(1.dp, item.color.copy(alpha = 0.25f), CosmicShapes.ZodiacShape)
        ) {
             Image(
                painter = painterResource(id = item.iconRes),
                contentDescription = item.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                colorFilter = ColorFilter.tint(item.color) // User Request: "icon is drak color but not black"
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = Localization.get(item.name.lowercase(), true),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = Color.White.copy(alpha = 0.9f), // Visible on Dark Cosmic container
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// --- 4. ASTROLOGER CARD (Green Border, Animation, Shadow) ---
@Composable
fun AstrologerCard(
    astro: Astrologer,
    onChatClick: (Astrologer) -> Unit,
    onCallClick: (Astrologer, String) -> Unit,
    selectedTab: Int
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val showChat = (selectedTab == 0 || selectedTab == 1)
    val showVideo = (selectedTab == 0 || selectedTab == 2 || selectedTab == 3)
    val showCall = (selectedTab == 0 || selectedTab == 3)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .shadow(
                elevation = if (astro.isOnline) 8.dp else 2.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = if (astro.isOnline) Color.Gray else Color.Black
            )
            .clickable {
                val intent = Intent(context, com.astroluna.ui.profile.AstrologerProfileActivity::class.java).apply {
                    putExtra("astro_name", astro.name)
                    putExtra("astro_exp", astro.experience.toString())
                    putExtra("astro_skills", if(astro.skills.isNotEmpty()) astro.skills.joinToString(", ") else "Vedic, Tarot")
                    putExtra("astro_id", astro.userId)
                    putExtra("is_chat_online", astro.isChatOnline)
                    putExtra("is_audio_online", astro.isAudioOnline)
                    putExtra("is_video_online", astro.isVideoOnline)
                    putExtra("astro_image", astro.image)
                    putExtra("astro_price", astro.price)
                    putExtra("astro_gender", astro.gender)
                }
                context.startActivity(intent)
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEEEEEE))
    ) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            // 1. LEFT COLUMN: Avatar, Rating, Orders
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(84.dp)
            ) {
                Box(modifier = Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                    // Profile Image
                    val placeholderRes = if (astro.gender == "Female") com.astroluna.R.drawable.default_female else com.astroluna.R.drawable.default_male
                    AsyncImage(
                        model = astro.image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        placeholder = painterResource(id = placeholderRes),
                        error = painterResource(id = placeholderRes),
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .border(1.dp, Color.LightGray, CircleShape)
                            .background(Color(0xFFF5F5F5))
                    )
                    // Verified Badge (Overlap)
                    if (astro.isVerified) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White,
                            shadowElevation = 2.dp,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = (-2).dp, y = (-2).dp)
                                .size(22.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Verified",
                                tint = Color(0xFF3B82F6),
                                modifier = Modifier.padding(2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Rating & Orders
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = String.format("%.1f", astro.rating),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.Black
                    )
                }
                Text(
                    text = "${if(astro.orders > 0) astro.orders else (3000..5000).random()} Orders",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }

            // 2. RIGHT COLUMN: Details & Actions
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                // Name and Price
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = astro.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF1E293B),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Price
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "₹ ${astro.price}/min",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFE44D3A)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Skills
                StaticInfoRow(Icons.Default.FlashOn, if(astro.skills.isNotEmpty()) astro.skills.take(2).joinToString(", ") else "Vedic, Vastu")
                // Languages
                StaticInfoRow(Icons.Default.Translate, "Hindi, English")
                // Experience
                StaticInfoRow(Icons.Default.History, "Exp: ${astro.experience} Years")

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    AstrologerActionButton(
                        text = "Chat",
                        icon = Icons.Default.Chat,
                        isBusy = astro.isBusy,
                        isOnline = astro.isChatOnline,
                        baseColor = Color(0xFF0074D9),
                        onClick = { onChatClick(astro) }
                    )
                    AstrologerActionButton(
                        text = "Video",
                        icon = Icons.Default.Videocam,
                        isBusy = astro.isBusy,
                        isOnline = astro.isVideoOnline,
                        baseColor = Color(0xFFE44D3A),
                        onClick = { onCallClick(astro, "Video") }
                    )
                    AstrologerActionButton(
                        text = "Call",
                        icon = Icons.Default.Call,
                        isBusy = astro.isBusy,
                        isOnline = astro.isAudioOnline,
                        baseColor = Color(0xFF2ECC40),
                        onClick = { onCallClick(astro, "Audio") }
                    )
                }
            }
        }
    }
}

@Composable
fun StaticInfoRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Icon(icon, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun HomeBottomBar(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    NavigationBar(
        containerColor = Color.White,
        contentColor = PeacockGreen
    ) {
        val items = listOf(
            Triple("Home", androidx.compose.material.icons.Icons.Default.Home, 0),
            Triple("Chat", androidx.compose.material.icons.Icons.Default.Send, 1),
            Triple("Refer", androidx.compose.material.icons.Icons.Default.Person, 5),
            Triple("Call", androidx.compose.material.icons.Icons.Default.Phone, 3),
            Triple("Profile", androidx.compose.material.icons.Icons.Default.Person, 4)
        )

        items.forEach { (label, icon, index) ->
            NavigationBarItem(
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label) },
                selected = selectedTab == index,
                onClick = { onTabSelected(index) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White,
                    selectedTextColor = PeacockGreen,
                    indicatorColor = PeacockGreen,
                    unselectedIconColor = Color.Gray.copy(alpha = 0.6f),
                    unselectedTextColor = Color.Gray.copy(alpha = 0.6f)
                )
            )
        }
    }
}

@Composable
fun DailyHoroscopeCard(content: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF1F5F9)) // Slate 100
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFF6366F1).copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFF6366F1),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Daily Horoscope",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = Color(0xFF0F172A) // Slate 900
                    )
                    Text(
                        text = "Your guidance from the cosmos",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8) // Slate 400
                    )
                }
            }

            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium.copy(
                    lineHeight = 22.sp,
                    letterSpacing = 0.2.sp
                ),
                color = Color(0xFF475569), // Slate 600
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun RasiGridSection(onClick: (ComposeRasiItem) -> Unit) {
    val rasiItems = listOf(
        ComposeRasiItem(1, "Aries", com.astroluna.R.drawable.ic_rasi_aries_premium, AriesRed),
        ComposeRasiItem(2, "Taurus", com.astroluna.R.drawable.ic_rasi_taurus_premium_copy, TaurusGreen),
        ComposeRasiItem(3, "Gemini", com.astroluna.R.drawable.ic_rasi_gemini_premium_copy, GeminiGreen),
        ComposeRasiItem(4, "Cancer", com.astroluna.R.drawable.ic_rasi_cancer_premium_copy, CancerBlue),
        ComposeRasiItem(5, "Leo", com.astroluna.R.drawable.ic_rasi_leo_premium, LeoGold),
        ComposeRasiItem(6, "Virgo", com.astroluna.R.drawable.ic_rasi_virgo_premium, VirgoOlive),
        ComposeRasiItem(7, "Libra", com.astroluna.R.drawable.ic_rasi_libra_premium_copy, LibraPink),
        ComposeRasiItem(8, "Scorpio", com.astroluna.R.drawable.ic_rasi_scorpio_premium, ScorpioMaroon),
        ComposeRasiItem(9, "Sagittarius", com.astroluna.R.drawable.ic_rasi_sagittarius_premium, SagPurple),
        ComposeRasiItem(10, "Capricorn", com.astroluna.R.drawable.ic_rasi_capricorn_premium_copy, CapTeal),
        ComposeRasiItem(11, "Aquarius", com.astroluna.R.drawable.ic_rasi_aquarius_premium, AquaBlue),
        ComposeRasiItem(12, "Pisces", com.astroluna.R.drawable.ic_rasi_pisces_premium_copy, PiscesIndigo)
    )

    // User Request: "12 rasi contain have one box that box bf use that bg" (Customer Style)
    // User Request: "12 rasi contain have one box that box bf use that bg"
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, PeacockGreen.copy(alpha = 0.15f)),
        modifier = Modifier.padding(16.dp).fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(top = 24.dp, bottom = 12.dp, start = 8.dp, end = 8.dp)) {
            val rows = rasiItems.chunked(4)
            for (rowItems in rows) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (item in rowItems) {
                        RasiItemView(item, onClick)
                    }
                }
            }
        }
    }
}

// Duplicate definitions removed


@Composable
fun InfoRow(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.Gray,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = Color.DarkGray,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AstrologerActionButton(
    text: String,
    icon: ImageVector,
    isBusy: Boolean,
    isOnline: Boolean,
    baseColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val buttonColor = when {
        isBusy -> Color.Red
        !isOnline -> Color.LightGray
        else -> Color.Transparent // Outlined for Online
    }

    val contentColor = when {
        isBusy -> Color.White
        !isOnline -> Color.White
        else -> baseColor
    }

    val borderColor = when {
        isBusy -> Color.Red
        !isOnline -> Color.LightGray
        else -> baseColor
    }

    Surface(
        onClick = if (isOnline) onClick else ({}),
        enabled = isOnline,
        shape = RoundedCornerShape(50), // Pill shape
        color = buttonColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        modifier = modifier.height(36.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}



@Composable
fun FilterBar(filters: List<String>, selectedFilter: String, onFilterSelected: (String) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(bottom = 16.dp)
    ) {
        items(filters) { filter ->
            val isSelected = filter == selectedFilter
            val containerColor = if (isSelected) Color(0xFF4CAF50) else Color.White
            val contentColor = if (isSelected) Color.White else Color.Black
            val borderColor = if (isSelected) Color.Transparent else Color.Gray.copy(alpha = 0.3f)

            Surface(
                onClick = { onFilterSelected(filter) },
                shape = RoundedCornerShape(50),
                color = containerColor,
                contentColor = contentColor,
                border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
                modifier = Modifier.height(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = filter,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@Composable
fun CircularActionButton(
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = color,
        contentColor = Color.White,
        modifier = Modifier.size(40.dp),
        shadowElevation = 4.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

// 🌌 COSMIC ANIMATIONS

@Composable
fun StarField() {
    // 🌌 1. BACKGROUND STAR PARTICLE ANIMATION
    val stars = remember { List(40) { Triple(Math.random().toFloat(), Math.random().toFloat(), Math.random().toFloat()) } }

    val infiniteTransition = rememberInfiniteTransition(label = "StarAnim")
    val animProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Reverse),
        label = "StarAlpha"
    )

    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        stars.forEachIndexed { index, (x, y, starSize) ->
            val phase = (index % 10) / 10f
            val baseAlpha = (animProgress + phase) % 1f
            drawCircle(
                color = Color.White,
                radius = 1.5.dp.toPx() * (starSize + 0.2f),
                center = androidx.compose.ui.geometry.Offset(x * size.width, y * size.height),
                alpha = baseAlpha * 0.4f // Low opacity
            )
        }
    }
}

@Composable
fun TopServicesSection(onServiceClick: (String) -> Unit = {}) {
    val context = LocalContext.current
    val services: List<Pair<String, Int>> = listOf(
        "Free\nHoroscope" to com.astroluna.R.drawable.ic_free_kundali,
        "Horoscope\nMatch" to com.astroluna.R.drawable.ic_match,
        "Daily\nHoroscope" to com.astroluna.R.drawable.ic_daily_horoscope,
        "Astro\nAcademy" to com.astroluna.R.drawable.ic_academy,
        "Free\nServices" to com.astroluna.R.drawable.ic_free_services
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 8.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        services.forEach { (name, icon) ->
            ServiceItem(name, icon) {
                onServiceClick(name)
            }
        }
    }
}

@Composable
fun ServiceItem(name: String, iconRes: Int, onClick: () -> Unit) {
    // MARKETPLACE SHORTCUT STYLE: White, 12dp, Thin Red Outline
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, colorResource(id = com.astroluna.R.color.marketplace_red)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .size(width = 80.dp, height = 90.dp)
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(40.dp) // Slightly larger for better visibility
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    lineHeight = 13.sp
                ),
                color = Color.DarkGray
            )
        }
    }
}

@Composable
fun CustomerStoriesSection() {
    val stories = listOf(
        Triple("Akshay Sharma", "Sharjah, Dubai", "I talked to Asha ma'am on Anytime..."),
        Triple("Priya Singh", "Mumbai, India", "Very accurate prediction about my..."),
        Triple("Rahul Verma", "Delhi, India", "Helped me resolve my marriage...")
    )

    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        Text(
            text = "Customer Stories",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.Black,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            stories.forEach { (name, loc, review) ->
                CustomerStoryCard(name, loc, review)
            }
        }
    }
}

@Composable
fun CustomerStoryCard(name: String, loc: String, review: String) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray.copy(alpha=0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.width(260.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            Image(
                painter = painterResource(id = com.astroluna.R.drawable.ic_person_placeholder),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(imageVector = Icons.Default.Menu, contentDescription=null, modifier=Modifier.size(16.dp), tint=Color.Gray) // 3-dot placeholder
                }
                Text(text = loc, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = review, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(text = "more", style = MaterialTheme.typography.labelSmall, color = Color.Red)
            }
        }
    }
}

@Composable
fun StickyFooterButtons(
    isGuest: Boolean,
    onTabSelected: (Int) -> Unit,
    onLoginClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Chat Button
        Button(
            onClick = {
                if (isGuest) {
                    onLoginClick()
                } else {
                    onTabSelected(1) // Tab 1 = Chat
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = colorResource(id = com.astroluna.R.color.marketplace_yellow), contentColor = Color.Black),
            shape = RoundedCornerShape(50),
            modifier = Modifier.weight(1f).height(46.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            Icon(imageVector = androidx.compose.material.icons.Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.Black)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Chat with Astro",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Talk Button
        Button(
            onClick = {
                 if (isGuest) {
                    onLoginClick()
                } else {
                    onTabSelected(3) // Tab 3 = Call
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = colorResource(id = com.astroluna.R.color.marketplace_yellow), contentColor = Color.Black),
            shape = RoundedCornerShape(50),
            modifier = Modifier.weight(1f).height(46.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            Icon(imageVector = androidx.compose.material.icons.Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.Black)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Talk to Astro",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
