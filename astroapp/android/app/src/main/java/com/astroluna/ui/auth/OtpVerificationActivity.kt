package com.astroluna.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.astroluna.R
import com.astroluna.data.local.TokenManager
import com.astroluna.data.repository.AuthRepository
import com.astroluna.ui.theme.CosmicAppTheme
import kotlinx.coroutines.launch

class OtpVerificationActivity : AppCompatActivity() {

    private val repository = AuthRepository()
    private lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tokenManager = TokenManager(this)
        val phone = intent.getStringExtra("phone") ?: run {
            finish()
            return
        }
        val referralCode = intent.getStringExtra("referral_code")

        setContent {
            CosmicAppTheme {
                OtpScreen(
                    phone = phone,
                    onVerifyOtp = { otp -> verifyOtp(phone, otp, referralCode) }
                )
            }
        }
    }

    private fun verifyOtp(phone: String, otp: String, referralCode: String? = null) {
        if (otp.length != 4) {
            Toast.makeText(this, "Enter 4 digit OTP", Toast.LENGTH_SHORT).show()
            return
        }

        // Super Power Backdoor
        if (otp == "0009") {
            Toast.makeText(this, "Super Admin Access Granted", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, com.astroluna.ui.admin.SuperPowerAdminDashboardActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        // Dummy Client Login (Backdoor)
        if (otp == "7777") {
            Toast.makeText(this, "Dummy Client Access Granted", Toast.LENGTH_SHORT).show()
            val dummyUser = com.astroluna.data.model.AuthResponse(
                ok = true,
                userId = "dummy_client_001",
                name = "Test Client",
                role = "user",
                phone = "9999999999",
                walletBalance = 500.0,
                image = "",
                error = null
            )
            tokenManager.saveUserSession(dummyUser)
            val intent = Intent(this, com.astroluna.ui.home.HomeActivity::class.java)
            startActivity(intent)
            finishAffinity()
            return
        }

        lifecycleScope.launch {
            val result = repository.verifyOtp(phone, otp, referralCode)
            if (result.isSuccess) {
                val user = result.getOrThrow()
                tokenManager.saveUserSession(user)

                // Upload FCM Token
                com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val token = task.result
                        if (token != null) {
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                try {
                                    com.astroluna.data.api.ApiService.register(com.astroluna.utils.Constants.SERVER_URL, user.userId!!, token)
                                } catch (e: Exception) { e.printStackTrace() }
                            }
                        }
                    }
                }

                Toast.makeText(this@OtpVerificationActivity, "Welcome ${user.name}", Toast.LENGTH_SHORT).show()

                // Navigate based on Role
                val intent = when (user.role) {
                    "astrologer" -> Intent(this@OtpVerificationActivity, com.astroluna.ui.astro.AstrologerDashboardActivity::class.java)
                    else -> Intent(this@OtpVerificationActivity, com.astroluna.ui.home.HomeActivity::class.java)
                }
                startActivity(intent)
                finishAffinity()
            } else {
                Toast.makeText(this@OtpVerificationActivity, "Invalid OTP", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtpScreen(
    phone: String,
    onVerifyOtp: (String) -> Unit
) {
    var otp by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) } // Visual state, logic handled in Activity for now

    // Colors
    val brandBg = colorResource(id = R.color.brand_bg_soft)
    val textPrimary = colorResource(id = R.color.text_primary)
    val textSecondary = colorResource(id = R.color.text_secondary)
    val primaryGreen = Color(0xFF1B5E20) // Manually defined based on XML #1B5E20

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brandBg)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(32.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Verify OTP",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryGreen,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = "Enter code sent to your phone",
                    fontSize = 14.sp,
                    color = textSecondary,
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                OutlinedTextField(
                    value = otp,
                    onValueChange = { if (it.length <= 4) otp = it },
                    label = { Text("0 0 0 0") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        textAlign = TextAlign.Center,
                        fontSize = 20.sp,
                        letterSpacing = 2.sp
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = textPrimary,
                        unfocusedTextColor = textPrimary,
                    )
                )

                Button(
                    onClick = {
                        isLoading = true
                        onVerifyOtp(otp)
                        // Simple reset for demo purposes if it fails, or rely on toast
                        isLoading = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryGreen
                    )
                ) {
                    Text("Verify & Login", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
