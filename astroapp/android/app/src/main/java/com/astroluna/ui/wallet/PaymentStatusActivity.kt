package com.astroluna.ui.wallet

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.astroluna.MainActivity
import com.astroluna.data.local.TokenManager
import com.astroluna.ui.theme.CosmicAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PaymentStatusActivity : ComponentActivity() {

    private lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tokenManager = TokenManager(this)

        // Handle Deep Link
        val data = intent.data
        val status = data?.getQueryParameter("status")
        val txnId = data?.getQueryParameter("txnId")

        val isSuccess = status == "success"

        if (isSuccess) {
            refreshWalletBalance()
        }

        setContent {
            CosmicAppTheme {
                PaymentStatusScreen(
                    isSuccess = isSuccess,
                    txnId = txnId,
                    onGoHome = {
                        // Navigate to Wallet page after payment success
                        val intent = Intent(this, WalletActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        startActivity(intent)
                        finish()
                    }
                )
            }
        }
    }

    private fun refreshWalletBalance() {
        val userId = tokenManager.getUserSession()?.userId ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = com.astroluna.data.api.ApiClient.api.getUserProfile(userId)
                if (response.isSuccessful && response.body() != null) {
                    val user = response.body()!!
                    tokenManager.saveUserSession(user)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

@Composable
fun PaymentStatusScreen(
    isSuccess: Boolean,
    txnId: String?,
    onGoHome: () -> Unit
) {
    val bgColor = if (isSuccess) Color(0xFFE8F5E9) else Color(0xFFFBE9E7)
    val iconColor = if (isSuccess) Color(0xFF4CAF50) else Color(0xFFD32F2F)
    val icon: ImageVector = if (isSuccess) Icons.Default.Check else Icons.Default.Warning
    val title = if (isSuccess) "Payment Successful!" else "Payment Failed"
    val message = if (isSuccess) "Your wallet has been recharged.\nTxn ID: ${txnId ?: "N/A"}" else "Transaction could not be completed."

    Surface(
        color = Color.White,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(80.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = title,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = message,
                fontSize = 16.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onGoHome,
                colors = ButtonDefaults.buttonColors(containerColor = iconColor),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Return to Home", fontSize = 16.sp, color = Color.White)
            }
        }
    }
}
