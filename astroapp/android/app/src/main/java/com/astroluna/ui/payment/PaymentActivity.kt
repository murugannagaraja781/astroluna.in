package com.astroluna.ui.payment

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.astroluna.data.api.ApiClient
import com.astroluna.data.local.TokenManager
import com.astroluna.data.model.PaymentInitiateRequest
import com.phonepe.intent.sdk.api.models.transaction.TransactionRequest
import com.phonepe.intent.sdk.api.models.transaction.paymentMode.PayPagePaymentMode
import com.phonepe.intent.sdk.api.PhonePe
import com.phonepe.intent.sdk.api.PhonePeKt
import com.phonepe.intent.sdk.api.models.PhonePeEnvironment
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * PaymentActivity - Handles PhonePe Native SDK Payment
 * Uses programmatic UI to avoid layout confusion.
 */
class PaymentActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PaymentActivity"
        private const val MERCHANT_ID = "M23VW0EJ3IVEK"
        private const val B2B_PG_REQUEST_CODE = 777
        private const val USE_NATIVE_SDK = false // Toggle this to switch between Native and Web
        private const val SERVER_URL = "https://astroluna.in"
    }

    private lateinit var tokenManager: TokenManager
    private lateinit var statusText: TextView
    private lateinit var webView: android.webkit.WebView

    private val phonePeLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
        // After PhonePe app returns, verify status
        statusText.text = "Verifying Transaction..."
        checkPaymentStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Handle Deep Link Return
        if (intent?.data != null && intent.data?.scheme == "astroluna") {
            Log.d(TAG, "Deep Link Intent received in onCreate: ${intent.data}")
            finish()
        }

        // --- Programmatic UI ---
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0B1D2A")) // DeepSpaceNavy
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        val progressBar = ProgressBar(this).apply {
            isIndeterminate = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER }
        }

        statusText = TextView(this).apply {
            text = "Initializing Payment..."
            textSize = 18f
            setTextColor(Color.parseColor("#F2F4FF")) // StarWhite
            gravity = Gravity.CENTER
            setPadding(0, 30, 0, 0)
        }

        // Webview for fallback
        webView = android.webkit.WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.setSupportMultipleWindows(true)
            // Spoof Chrome User-Agent + Marker to ensure Gateway works and App is detected
            settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36 AstroApp"

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            visibility = android.view.View.GONE // Hidden by default

            // Add JavaScript Bridge
            addJavascriptInterface(AndroidBridge(), "AndroidBridge")

            webChromeClient = object : android.webkit.WebChromeClient() {
                // Intercept window.open calls (Popups) from Payment Gateway
                override fun onCreateWindow(
                    view: android.webkit.WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: android.os.Message?
                ): Boolean {
                    val newWebView = android.webkit.WebView(this@PaymentActivity)
                    val transport = resultMsg?.obj as? android.webkit.WebView.WebViewTransport
                    transport?.webView = newWebView
                    resultMsg?.sendToTarget()

                    newWebView.webViewClient = object : android.webkit.WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: android.webkit.WebView?,
                            request: android.webkit.WebResourceRequest?
                        ): Boolean {
                            val url = request?.url.toString()
                            Log.d(TAG, "Popup Navigating: $url")

                            // If popup tries to load a UPI/Intent URL, handle it via System Intent
                            if (handleDeepLink(url)) {
                                return true
                            }
                            // Otherwise, normal load (maybe intermediate redirect)
                            return false
                        }
                    }
                    return true
                }
            }

            webViewClient = object : android.webkit.WebViewClient() {
                override fun shouldOverrideUrlLoading(view: android.webkit.WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                    val url = request?.url.toString()
                    Log.d(TAG, "WebView URL: $url")

                    // Detect astroluna:// deep link (payment success/fail) and close immediately
                    if (url.startsWith("astroluna://payment-success")) {
                        handlePaymentResult("success")
                        return true
                    }
                    if (url.startsWith("astroluna://payment-failed")) {
                        handlePaymentResult("failed")
                        return true
                    }

                    // Catch standard PhonePe success/failure callbacks
                    if (url.contains("payment-success") || url.contains("status=success")) {
                        handlePaymentResult("success")
                        return true
                    }

                    if (url.contains("payment-failed") || url.contains("status=failure") || url.contains("status=failed")) {
                        handlePaymentResult("failed")
                        return true
                    }

                    return handleDeepLink(url)
                }

                override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Log.d(TAG, "Page finished: $url")

                    // Auto-detect if the callback page has loaded (backup detection)
                    if (url != null && (url.contains("/api/payment/callback") || url.contains("/payment-status"))) {
                        // Check if it's a success or failure
                        if (url.contains("status=success") || url.contains("payment-success")) {
                            handlePaymentResult("success")
                        } else if (url.contains("status=failure") || url.contains("status=failed") || url.contains("payment-failed")) {
                            handlePaymentResult("failed")
                        }
                    }
                }
            }
        }

        layout.addView(progressBar)
        layout.addView(statusText)
        layout.addView(webView)
        setContentView(layout)
        // -----------------------

        tokenManager = TokenManager(this)

        // Initialize PhonePe SDK (Only if using Native)
        if (USE_NATIVE_SDK) {
            try {
                 PhonePeKt.init(
                    context = this,
                    merchantId = MERCHANT_ID,
                    flowId = "CITIZEN_APP",
                    phonePeEnvironment = PhonePeEnvironment.RELEASE,
                    enableLogging = true,
                    appId = null
                )
            } catch (e: Exception) {
                Log.e(TAG, "PhonePe Init Error", e)
                showError("SDK Init Failed: ${e.message}")
                return
            }
        }

        val amount = intent.getDoubleExtra("amount", 0.0)
        if (amount <= 0.0) {
            showError("Invalid Amount: $amount")
            return
        }

        if (USE_NATIVE_SDK) {
            startPayment(amount)
        } else {
            startWebPayment(amount)
        }
    }

    private var pendingTransactionId: String? = null

    // --- WEB PAYMENT LOGIC (TOKEN BASED - SAME AS WEB APP) ---
    private fun startWebPayment(amount: Double) {
        val user = tokenManager.getUserSession()
        val userId = user?.userId ?: run {
            showError("User not logged in")
            return
        }

        statusText.text = "Securing Payment Session..."

        lifecycleScope.launch {
            try {
                // 1. Get Payment Token (Secure Session)
                Log.d(TAG, "Requesting token for ₹$amount")
                val request = PaymentInitiateRequest(userId, amount.toInt())
                val response = ApiClient.api.getPaymentToken(request)

                if (response.isSuccessful && response.body()?.get("ok")?.asBoolean == true) {
                    val token = response.body()?.get("token")?.asString

                    if (!token.isNullOrEmpty()) {
                        // 2. Construct URL
                        val paymentPageUrl = "$SERVER_URL/payment.html?token=$token&isApp=true"
                        Log.d(TAG, "Opening Payment Page in WebView: $paymentPageUrl")

                        runOnUiThread {
                            // Show WebView, Hide Status
                            statusText.visibility = android.view.View.GONE
                            webView.visibility = android.view.View.VISIBLE
                            webView.loadUrl(paymentPageUrl)
                        }

                    } else {
                        showError("Server did not return a valid token")
                    }
                } else {
                    val errorMsg = response.body()?.get("error")?.asString ?: "Unknown Error"
                    showError("Token Error: $errorMsg")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Token Network Error", e)
                showError("Connection Error: ${e.localizedMessage}")
            }
        }
    }

    private fun monitorWebPayment(txnId: String) {
        lifecycleScope.launch {
            var checks = 0
            while (checks < 60) { // Check for 2 minutes
                delay(3000)
                try {
                     val response = ApiClient.api.checkPaymentStatus(txnId)
                     if (response.isSuccessful && response.body()?.get("ok")?.asBoolean == true) {
                         val status = response.body()?.get("status")?.asString
                         if (status == "success" || status == "failed") {
                             handlePaymentResult(status ?: "unknown")
                             return@launch
                         }
                     }
                } catch (e: Exception) {
                    Log.e(TAG, "Monitor Error", e)
                }
                checks++
            }
        }
    }

    private fun startPayment(amountRupees: Double) {
        val user = tokenManager.getUserSession()
        val userId = user?.userId ?: run {
            showError("User not logged in")
            return
        }

        statusText.text = "Contacting Server..."

        lifecycleScope.launch {
            try {
                // 1. Get Signed Payload from Server
                Log.d(TAG, "Requesting signature for ₹$amountRupees")
                val request = PaymentInitiateRequest(userId, amountRupees.toInt())
                val response = ApiClient.api.signPhonePe(request)

                if (response.isSuccessful && response.body()?.ok == true) {
                    val body = response.body()!!
                    val payloadBase64 = body.payload
                    val checksum = body.checksum
                    pendingTransactionId = body.transactionId // Store for verification

                    if (payloadBase64.isNullOrEmpty() || checksum.isNullOrEmpty()) {
                        showError("Empty payload from server")
                        return@launch
                    }

                    statusText.text = "Launching PhonePe..."

                    // 2. Create SDK Request
                    val transactionRequest = TransactionRequest(
                        payloadBase64,
                        checksum,
                        PayPagePaymentMode
                    )

                    // 3. Launch PhonePe Intent
                    try {
                        PhonePeKt.startTransaction(
                            this@PaymentActivity,
                            transactionRequest,
                            phonePeLauncher
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "PhonePe Launch Error", e)
                        showError("Could not launch PhonePe App.\nIs it installed?")
                    }

                } else {
                    val errorMsg = response.body()?.error ?: response.errorBody()?.string() ?: "Unknown Error"
                    Log.e(TAG, "Sign API Failed: $errorMsg Code: ${response.code()}")
                    showError("Server Error (${response.code()}): $errorMsg")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Network Error", e)
                showError("Network Connection Failed: ${e.message}")
            }
        }
    }


    private fun handleDeepLink(url: String): Boolean {
        Log.d(TAG, "DeepLink Check: $url")

        if (url.startsWith("astroluna://payment-success") || url.contains("/wallet?status=success")) {
            handlePaymentResult("success")
            return true
        }

        if (url.startsWith("astroluna://payment-failed") || url.contains("/wallet?status=failure")) {
            handlePaymentResult("failed")
            return true
        }
        if (url.contains("/api/payment/callback?isApp=true")) {
             return false
        }

        val uri = android.net.Uri.parse(url)
        val scheme = uri.scheme ?: ""

        // Allow HTTP/HTTPS to load in WebView naturally
        if (scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true)) {
            return false
        }

        // For EVERYTHING else (phonepe://, tez://, upi://, intent://), try to launch external app
        try {
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Standard Launch Error", e)

            // Special Handling for 'intent://' if standard launch failed
            if (scheme.equals("intent", ignoreCase = true)) {
                try {
                    val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                    if (intent != null) {
                        startActivity(intent)
                        return true
                    }
                } catch (ex: Exception) {
                     Log.e(TAG, "Intent Parse Error", ex)
                }
            }

            Toast.makeText(this@PaymentActivity, "App not installed for this payment method", Toast.LENGTH_SHORT).show()
            return true // Consume event to prevent WebView error
        }
    }

    private fun showError(message: String) {
        runOnUiThread {
            if (::webView.isInitialized) webView.visibility = android.view.View.GONE
            statusText.text = "Error!"
            statusText.visibility = android.view.View.VISIBLE
            statusText.setTextColor(Color.RED)

            AlertDialog.Builder(this)
                .setTitle("Payment Error")
                .setMessage(message)
                .setPositiveButton("Close") { _, _ -> finish() }
                .setCancelable(false)
                .show()
        }
    }

    // override fun onActivityResult... (Removed in favor of ActivityResultLauncher)

    private fun checkPaymentStatus() {
         val txnId = pendingTransactionId
         if (txnId == null) {
             finish()
             return
         }

         lifecycleScope.launch {
             try {
                 statusText.text = "Checking Status..."
                 // Give server a moment to receive callback or process
                 delay(2000)

                 val response = ApiClient.api.checkPaymentStatus(txnId)
                 if (response.isSuccessful && response.body()?.get("ok")?.asBoolean == true) {
                     val status = response.body()?.get("status")?.asString
                     if (status == "success" || status == "failed") {
                         handlePaymentResult(status ?: "unknown")
                     } else {
                         showError("Payment Pending or Failed.\nStatus: $status")
                     }
                 } else {
                     showError("Failed to verify payment status.")
                 }
             } catch (e: Exception) {
                 Log.e(TAG, "Verification Error", e)
                 showError("Verification Network Error")
             }
         }
    }

    /**
     * JavaScript Interface for communication from WebView
     */
    inner class AndroidBridge {
        @android.webkit.JavascriptInterface
        fun onPaymentComplete(status: String) {
            Log.d(TAG, "JS Bridge: Payment Complete with status: $status")
            handlePaymentResult(status)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.data != null && intent.data?.scheme == "astroluna") {
            Log.d(TAG, "Deep Link Intent received in onNewIntent: ${intent.data}")
            finish()
        }
    }

    private fun handlePaymentResult(status: String) {
        Log.d(TAG, "Handling Result: $status. Closing activity.")
        finish()
    }
}
