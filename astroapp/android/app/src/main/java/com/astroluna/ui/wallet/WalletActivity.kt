package com.astroluna.ui.wallet

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.astroluna.R
import com.astroluna.data.api.ApiClient
import com.astroluna.data.local.TokenManager
import com.astroluna.ui.theme.CosmicAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.ArrayList

class WalletActivity : ComponentActivity() {

    private lateinit var tokenManager: TokenManager
    // Simple state holding for this screen
    private val transactionsState = mutableStateListOf<JSONObject>()
    private var balanceState by mutableDoubleStateOf(0.0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Legacy ThemeManager removed
        // Note: setContentView(R.layout.activity_wallet) is typically used for XML layouts.
        // For Compose UI, setContent is used. If you intend to use an XML layout,
        // the setContent block below should be removed or adjusted.
        // Assuming the intent was to add ThemeManager.applyTheme(this) before tokenManager initialization.
        tokenManager = TokenManager(this)

        updateBalanceFromSession()

        setContent {
            CosmicAppTheme {
                WalletScreen(
                    balance = balanceState,
                    transactions = transactionsState,
                    onAddMoney = { amount ->
                         if (amount < 1) {
                            Toast.makeText(this, "Enter valid amount", Toast.LENGTH_SHORT).show()
                        } else {
                            val intent = Intent(this, com.astroluna.ui.payment.PaymentActivity::class.java)
                            intent.putExtra("amount", amount.toDouble())
                            startActivity(intent)
                        }
                    },
                    onRefreshHistory = { loadPaymentHistory() }
                )
            }
        }

        loadPaymentHistory()
    }

    override fun onResume() {
        super.onResume()
        refreshWalletBalance()
        loadPaymentHistory()

        // Listen for real-time updates
        com.astroluna.data.remote.SocketManager.onWalletUpdate { newBalance ->
             runOnUiThread {
                tokenManager.updateWalletBalance(newBalance)
                balanceState = newBalance
            }
        }
    }

    override fun onPause() {
        super.onPause()
        com.astroluna.data.remote.SocketManager.off("wallet-update")
    }

    private fun updateBalanceFromSession() {
        val user = tokenManager.getUserSession()
        balanceState = user?.walletBalance ?: 0.0
    }

    private fun refreshWalletBalance() {
        val userId = tokenManager.getUserSession()?.userId ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = ApiClient.api.getUserProfile(userId)
                if (response.isSuccessful && response.body() != null) {
                    val user = response.body()!!
                    runOnUiThread {
                        tokenManager.saveUserSession(user)
                        balanceState = user.walletBalance ?: 0.0
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun loadPaymentHistory() {
        val userId = tokenManager.getUserSession()?.userId ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("${com.astroluna.utils.Constants.SERVER_URL}/api/payment/history/$userId")
                    .get()
                    .build()

                val client = OkHttpClient()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        val json = JSONObject(body ?: "{}")
                        val data = json.optJSONArray("data")

                        val newTransactions = ArrayList<JSONObject>()
                        if (data != null) {
                            for (i in 0 until data.length()) {
                                newTransactions.add(data.getJSONObject(i))
                            }
                        }

                        runOnUiThread {
                            transactionsState.clear()
                            transactionsState.addAll(newTransactions)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(
    balance: Double,
    transactions: List<JSONObject>,
    onAddMoney: (Int) -> Unit,
    onRefreshHistory: () -> Unit
) {
    var amountInput by remember { mutableStateOf("") }

    // Premium Blue Design System
    val premiumBlue = Color(0xFF001F3F) // Deep Navy
    val electricBlue = Color(0xFF0074D9) // Vibrant Blue
    val neonCyan = Color(0xFF7FDBFF) // Bright Cyan
    val glassWhite = Color.White.copy(alpha = 0.15f)
    val cardShape = RoundedCornerShape(24.dp)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF000B18), // Ultra Dark Blue
                        Color(0xFF001F3F), // Deep Navy
                        Color(0xFF003366)  // Midnight Blue
                    )
                )
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Astro Luna Wallet",
                            color = neonCyan,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = neonCyan
                    ),
                    actions = {
                        IconButton(onClick = onRefreshHistory) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = neonCyan
                            )
                        }
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }

                // 1. Premium Balance Card (Glassmorphism + Glow)
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                            .shadow(
                                elevation = 30.dp,
                                shape = cardShape,
                                ambientColor = electricBlue.copy(alpha = 0.5f),
                                spotColor = electricBlue.copy(alpha = 0.5f)
                            )
                            .clip(cardShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        electricBlue,
                                        premiumBlue
                                    ),
                                    start = Offset(0f, 0f),
                                    end = Offset.Infinite
                                )
                            )
                            .border(
                                width = 1.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.4f),
                                        Color.Transparent,
                                        neonCyan.copy(alpha = 0.3f)
                                    )
                                ),
                                shape = cardShape
                            )
                    ) {
                        // Glossy Overlay
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(Color.White.copy(alpha = 0.1f), Color.Transparent),
                                        center = Offset(200f, 0f),
                                        radius = 600f
                                    )
                                )
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        "TOTAL BALANCE",
                                        color = neonCyan.copy(alpha = 0.8f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 2.sp
                                    )
                                    Text(
                                        "Astro Luna Premium",
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 10.sp
                                    )
                                }
                                Icon(
                                    Icons.Default.AddCircle,
                                    contentDescription = null,
                                    tint = neonCyan,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = "₹",
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = neonCyan,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 8.dp, end = 4.dp)
                                )
                                Text(
                                    text = String.format("%,d", balance.toInt()),
                                    style = MaterialTheme.typography.displayMedium.copy(
                                        fontWeight = FontWeight.Black
                                    ),
                                    color = Color.White
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "PLATINUM MEMBER",
                                    color = neonCyan,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    "VALID THRU 12/28",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // 2. Recharge Section (Glass Card)
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(cardShape)
                            .background(glassWhite)
                            .border(1.dp, Color.White.copy(alpha = 0.2f), cardShape)
                            .padding(24.dp)
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            Text(
                                text = "Quick Recharge",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )

                            OutlinedTextField(
                                value = amountInput,
                                onValueChange = { amountInput = it.filter { char -> char.isDigit() } },
                                placeholder = { Text("Enter Amount", color = Color.White.copy(alpha = 0.4f)) },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = neonCyan,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                                    focusedContainerColor = Color.Black.copy(alpha = 0.2f),
                                    unfocusedContainerColor = Color.Black.copy(alpha = 0.1f),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    cursorColor = neonCyan
                                ),
                                prefix = { Text("₹ ", color = neonCyan, fontWeight = FontWeight.Bold) },
                                singleLine = true
                            )

                            // GST Breakdown UI
                            val amt = amountInput.toDoubleOrNull() ?: 0.0
                            if (amt >= 1.0) {
                                val gst = amt * 0.18
                                val total = amt + gst
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Text("Recharge Amount", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                                        Text("₹${String.format("%.2f", amt)}", color = Color.White, fontSize = 12.sp)
                                    }
                                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Text("GST (18%)", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                                        Text("₹${String.format("%.2f", gst)}", color = Color.White, fontSize = 12.sp)
                                    }
                                    Divider(color = Color.White.copy(alpha = 0.1f), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
                                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Text("Total Payable", color = neonCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("₹${String.format("%.2f", total)}", color = neonCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                }
                            }

                            Button(
                                onClick = {
                                    val amt = amountInput.toIntOrNull() ?: 0
                                    if (amt >= 1) {
                                        onAddMoney(amt)
                                        amountInput = ""
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .shadow(
                                        elevation = 15.dp,
                                        shape = RoundedCornerShape(16.dp),
                                        spotColor = neonCyan
                                    ),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = electricBlue,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(Icons.Default.Add, null)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("RECHARGE NOW", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                            }

                            // Preset amounts
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                listOf(200, 500, 1000).forEach { amount ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (amountInput == amount.toString()) electricBlue
                                                else Color.White.copy(alpha = 0.05f)
                                            )
                                            .border(
                                                1.dp,
                                                if (amountInput == amount.toString()) neonCyan
                                                else Color.White.copy(alpha = 0.1f),
                                                RoundedCornerShape(12.dp)
                                            )
                                            .clickable { amountInput = amount.toString() }
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "₹$amount",
                                            color = if (amountInput == amount.toString()) Color.White else neonCyan,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Transactions List
                item {
                    Text(
                        text = "Transaction History",
                        style = MaterialTheme.typography.titleMedium,
                        color = neonCyan,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                items(transactions) { transaction ->
                    val amount = transaction.optDouble("amount", 0.0)
                    val status = transaction.optString("status", "pending")
                    val dateStr = transaction.optString("createdAt", "")
                    val displayDate = if (dateStr.length > 10) dateStr.substring(0, 10) else dateStr

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.03f))
                            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        if (status == "success") Color(0xFF2ECC71).copy(alpha = 0.1f)
                                        else Color(0xFFEF5350).copy(alpha = 0.1f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (status == "success") "✓" else "!",
                                    color = if (status == "success") Color(0xFF2ECC71) else Color(0xFFEF5350),
                                    fontWeight = FontWeight.Black
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (status == "success") "Add Credits" else "Payment Failed",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = displayDate,
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontSize = 11.sp
                                )
                            }

                            Text(
                                text = "+₹${amount.toInt()}",
                                color = if (status == "success") Color(0xFF2ECC71) else Color.White.copy(alpha = 0.3f),
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(30.dp)) }
            }
        }
    }
}

