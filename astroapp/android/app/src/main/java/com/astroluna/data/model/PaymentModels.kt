package com.astroluna.data.model

data class PaymentInitiateRequest(
    val userId: String,
    val amount: Int,
    val isApp: Boolean = true
)

data class PaymentInitiateResponse(
    val ok: Boolean,
    val merchantTransactionId: String?,
    val paymentUrl: String?,
    val error: String?,
    val useWebFlow: Boolean?
)

data class PhonePeSignResponse(
    val ok: Boolean,
    val payload: String?,
    val checksum: String?,
    val transactionId: String?,
    val error: String?
)
