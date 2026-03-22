const express = require('express');
const router = express.Router();
const { DateTime } = require('luxon');
const { fetchDailyHoroscope } = require('../utils/rasiEng/horoscopeData');

// Mapping for canonical rasi names if needed, but we'll try to stick to what the data provides
// or what the app previously used if possible.
const SIGN_NAME_MAP = {
    "Aries": "Mesham",
    "Taurus": "Rishabam",
    "Gemini": "Mithunam",
    "Cancer": "Kadagam",
    "Leo": "Simmam",
    "Virgo": "Kanni",
    "Libra": "Thulaam",
    "Scorpio": "Viruchigam",
    "Sagittarius": "Dhanusu",
    "Capricorn": "Magaram",
    "Aquarius": "Kumbam",
    "Pisces": "Meenam"
};

router.get('/', async (req, res) => {
    try {
        const today = DateTime.now().setZone('Asia/Kolkata').toFormat('yyyy-MM-dd');
        console.log(`Fetching Rasipalan data for: ${today}`);

        let externalData = await fetchDailyHoroscope(today);

        // If data is null or not an array, return dummy data to avoid app crash
        if (!externalData || !Array.isArray(externalData) || externalData.length === 0) {
            console.error("No horoscope data found for today or yesterday. Sending dummy data.");
            const dummyData = [
                { id: 1, en: "Aries", ta: "மேஷம்", predTa: "இன்று நீங்கள் எதிலும் நிதானத்துடன் செயல்பட வேண்டும்.", predEn: "Today you should act with patience in everything." },
                { id: 2, en: "Taurus", ta: "ரிஷபம்", predTa: "தொழில் வியாபாரத்தில் நல்ல லாபம் கிடைக்கும்.", predEn: "Good profit in business and trade." },
                { id: 3, en: "Gemini", ta: "மிதுனம்", predTa: "எதிர்பார்த்த உதவிகள் தக்க சமயத்தில் கிடைக்கும்.", predEn: "Expected help will arrive on time." },
                { id: 4, en: "Cancer", ta: "கடகம்", predTa: "உடல் ஆரோக்கியத்தில் கவனம் தேவை.", predEn: "Need to pay attention to health." },
                { id: 5, en: "Leo", ta: "சிம்மம்", predTa: "நண்பர்கள் மூலம் ஆதாயம் உண்டாகும்.", predEn: "Benefits through friends." },
                { id: 6, en: "Virgo", ta: "கன்னி", predTa: "வேலை சுமை அதிகரிக்கலாம்.", predEn: "Workload may increase." },
                { id: 7, en: "Libra", ta: "துலாம்", predTa: "பண வரவு தாராளமாக இருக்கும்.", predEn: "Cash flow will be generous." },
                { id: 8, en: "Scorpio", ta: "விருச்சிகம்", predTa: "வாழ்க்கை துணையின் ஆதரவு கிடைக்கும்.", predEn: "Support from spouse." },
                { id: 9, en: "Sagittarius", ta: "தனுசு", predTa: "பிள்ளைகள் வழியில் நல்ல செய்தி வரும்.", predEn: "Good news through children." },
                { id: 10, en: "Capricorn", ta: "மகரம்", predTa: "வீண் செலவுகள் ஏற்படும்.", predEn: "Unnecessary expenses." },
                { id: 11, en: "Aquarius", ta: "கும்பம்", predTa: "திறமைக்கு ஏற்ற அங்கீகாரம் கிடைக்கும்.", predEn: "Recognition for talent." },
                { id: 12, en: "Pisces", ta: "மீனம்", predTa: "உடல் சோர்வு நீங்கி புத்துணர்ச்சி பெறுவீர்கள்.", predEn: "Fatigue will disappear and you will be refreshed." }
            ];

            return res.json({
                success: true,
                data: dummyData.map(item => ({
                    signId: item.id,
                    sign_en: item.en,
                    sign_ta: item.ta,
                    date: today,
                    prediction_ta: item.predTa,
                    prediction_en: item.predEn,
                    career_ta: "சிறப்பு",
                    career_en: "Good",
                    finance_ta: "நன்று",
                    finance_en: "Stable",
                    health_ta: "சிறப்பு",
                    health_en: "Good",
                    lucky_number: "5",
                    lucky_color_ta: "வெள்ளை",
                    lucky_color_en: "White"
                }))
            });
        }

        // Map external data to our app's expected format (RasipalanModel.kt)
        const mappedData = externalData.map((item, index) => {
            return {
                signId: index + 1,
                sign_en: item.sign_en || "",
                sign_ta: item.sign_ta || "",
                date: today,
                prediction_ta: item.prediction_ta || item.forecast_ta || "",
                prediction_en: item.prediction_en || item.forecast_en || "",
                career_ta: item.career_ta || "சிறப்பு",
                career_en: item.career_en || "Good",
                finance_ta: item.finance_ta || "நன்று",
                finance_en: item.finance_en || "Good",
                health_ta: item.health_ta || "சிறப்பு",
                health_en: item.health_en || "Good",
                lucky_number: String(item.lucky_number || ""),
                lucky_color_ta: item.lucky_color_ta || "",
                lucky_color_en: item.lucky_color_en || ""
            };
        });

        // Wrap in success object
        const finalResponse = {
            success: true,
            data: mappedData
        };

        // Filter by sign if provided in query (useful for testing)
        const signQuery = req.query.sign;
        if (signQuery) {
            const searchStr = signQuery.toLowerCase();
            const filtered = mappedData.filter(d =>
                (d.sign_en && d.sign_en.toLowerCase() === searchStr) ||
                (d.sign_ta === signQuery)
            );
            return res.json({ success: true, data: filtered });
        }

        console.log(`Successfully fetched and mapped ${mappedData.length} Rasi items.`);
        return res.json(finalResponse);

    } catch (error) {
        console.error("Error in Rasipalan route:", error.message);
        // Always return expected object structure instead of an array to prevent Retrofit parsing crashes
        return res.json({ success: false, data: [] });
    }
});

// Helper removed from here as we want full text for the list view


module.exports = router;
