package com.astroluna.data.model

/**
 * Astrologer data model
 * Represents an astrologer in the system with their online status and pricing
 */
data class Astrologer(
    val userId: String,
    val name: String,
    val phone: String = "",
    val skills: List<String> = emptyList(),
    val price: Int = 15,
    val isOnline: Boolean = false,
    val isChatOnline: Boolean = false,
    val isAudioOnline: Boolean = false,
    val isVideoOnline: Boolean = false,
    val image: String = "",
    val experience: Int = 0,
    val isVerified: Boolean = false,
    val walletBalance: Double = 0.0,
    val rating: Double = 5.0,
    val orders: Int = 0,
    val isBusy: Boolean = false
)
