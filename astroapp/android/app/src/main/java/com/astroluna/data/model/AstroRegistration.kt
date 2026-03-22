package com.astroluna.data.model

data class AstroRegistration(
    val realName: String,
    val displayName: String?,
    val gender: String?,

    val dob: String?,
    val tob: String?,
    val pob: String?,

    val cellNumber1: String,
    val cellNumber2: String?,
    val whatsAppNumber: String?,
    val email: String?,
    val address: String?,

    val aadharNumber: String?,
    val panNumber: String?,
    val astrologyExperience: String?,
    val profession: String?,

    val bankDetails: String?,
    val upiName: String?,
    val upiNumber: String?
)
