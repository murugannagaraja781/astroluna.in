package com.astroluna.utils

object Localization {
    // English Strings
    private val english = mapOf(
        "home_title" to "Astro Luna",
        "home" to "Home",
        "profile" to "Profile",
        "settings" to "Settings",
        "logout" to "Logout",
        "premium_consultation" to "Premium Consultation",
        "vedic_remedies" to "Vedic Remedies",
        "gemstone_guide" to "Gemstone Guide",
        "horoscope" to "Horoscope",
        "daily_horoscope" to "Daily Horoscope",
        "chat_services" to "Chat Services",
        "video_call" to "Video Call",
        "audio_call" to "Audio Call",
        "view_all" to "View All",
        "loading" to "Loading...",
        "aries" to "Aries",
        "taurus" to "Taurus",
        "gemini" to "Gemini",
        "cancer" to "Cancer",
        "leo" to "Leo",
        "virgo" to "Virgo",
        "libra" to "Libra",
        "scorpio" to "Scorpio",
        "sagittarius" to "Sagittarius",
        "capricorn" to "Capricorn",
        "aquarius" to "Aquarius",
        "pisces" to "Pisces",
        "all" to "All",
        "love" to "Love",
        "career" to "Career",
        "finance" to "Finance",
        "marriage" to "Marriage",
        "health" to "Health",
        "education" to "Education",
        "element" to "Element",
        "lord" to "Lord",
        "category" to "Category",
        "daily_prediction" to "Daily Prediction",
        "ok" to "OK"
    )

    // Tamil Strings
    private val tamil = mapOf(
        "home_title" to "ஆஸ்ட்ரோ 5 ஸ்டார்",
        "home" to "முகப்பு",
        "profile" to "சுயவிவரம்",
        "settings" to "அமைப்புகள்",
        "logout" to "வெளியேறு",
        "premium_consultation" to "பிரீமியம் ஆலோசனை",
        "vedic_remedies" to "வேத பரிகாரங்கள்",
        "gemstone_guide" to "ரத்தின வழிகாட்டி",
        "horoscope" to "ராசி பலன்",
        "daily_horoscope" to "தினசரி ராசி பலன்",
        "chat_services" to "சாட் சேவை",
        "video_call" to "வீடியோ அழைப்பு",
        "audio_call" to "ஆடியோ அழைப்பு",
        "view_all" to "அனைத்தையும் பார்க்க",
        "loading" to "ஏற்றுகிறது...",
        "aries" to "மேஷம்",
        "taurus" to "ரிஷபம்",
        "gemini" to "மிதுனம்",
        "cancer" to "கடகம்",
        "leo" to "சிம்மம்",
        "virgo" to "கன்னி",
        "libra" to "துலாம்",
        "scorpio" to "விருச்சிகம்",
        "sagittarius" to "தனுசு",
        "capricorn" to "மகரம்",
        "aquarius" to "கும்பம்",
        "pisces" to "மீனம்",
        "all" to "அனைத்தும்",
        "love" to "காதல்",
        "career" to "வேலைவாய்ப்பு",
        "finance" to "நிதி",
        "marriage" to "திருமணம்",
        "health" to "உடல்நலம்",
        "education" to "கல்வி",
        "element" to "தத்துவம்",
        "lord" to "அதிபதி",
        "category" to "வகை",
        "daily_prediction" to "இன்றைய பலன்",
        "ok" to "சரி"
    )

    fun get(key: String, isTamil: Boolean): String {
        return if (isTamil) {
            tamil[key] ?: english[key] ?: key
        } else {
            english[key] ?: key
        }
    }
}
