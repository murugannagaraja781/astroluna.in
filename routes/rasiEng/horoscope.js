// routes/rasiEng/horoscope.js
const express = require('express');
const { DateTime } = require('luxon');
const { fetchDailyHoroscope, getSignHoroscope } = require('../../utils/rasiEng/horoscopeData');

const router = express.Router();

/**
 * GET /api/rasi-eng/horoscope/daily
 * Query params:
 * - date (optional, defaults to today in IST)
 * - sign (optional, returns full list if omitted)
 */
router.get('/daily', async (req, res) => {
    try {
        let { date, sign } = req.query;

        // Default to today in IST (Asia/Kolkata)
        if (!date) {
            date = DateTime.now().setZone('Asia/Kolkata').toFormat('yyyy-MM-dd');
        }

        let data = await fetchDailyHoroscope(date);

        if (!data || !Array.isArray(data) || data.length === 0) {
            console.error("No horoscope data found for today or yesterday. Sending dummy data.");
            // Generate basic dummy data for 12 rasis
            const dummyData = [
                { sign_en: "Aries", sign_ta: "மேஷம்", prediction_ta: "இன்று நீங்கள் எதிலும் நிதானத்துடன் செயல்பட வேண்டும்.", prediction_en: "Today you should act with patience in everything." },
                { sign_en: "Taurus", sign_ta: "ரிஷபம்", prediction_ta: "தொழில் வியாபாரத்தில் நல்ல லாபம் கிடைக்கும்.", prediction_en: "Good profit in business and trade." },
                { sign_en: "Gemini", sign_ta: "மிதுனம்", prediction_ta: "எதிர்பார்த்த உதவிகள் தக்க சமயத்தில் கிடைக்கும்.", prediction_en: "Expected help will arrive on time." },
                { sign_en: "Cancer", sign_ta: "கடகம்", prediction_ta: "உடல் ஆரோக்கியத்தில் கவனம் தேவை.", prediction_en: "Need to pay attention to health." },
                { sign_en: "Leo", sign_ta: "சிம்மம்", prediction_ta: "நண்பர்கள் மூலம் ஆதாயம் உண்டாகும்.", prediction_en: "Benefits through friends." },
                { sign_en: "Virgo", sign_ta: "கன்னி", prediction_ta: "வேலை சுமை அதிகரிக்கலாம்.", prediction_en: "Workload may increase." },
                { sign_en: "Libra", sign_ta: "துலாம்", prediction_ta: "பண வரவு தாராளமாக இருக்கும்.", prediction_en: "Cash flow will be generous." },
                { sign_en: "Scorpio", sign_ta: "விருச்சிகம்", prediction_ta: "வாழ்க்கை துணையின் ஆதரவு கிடைக்கும்.", prediction_en: "Support from spouse." },
                { sign_en: "Sagittarius", sign_ta: "தனுசு", prediction_ta: "பிள்ளைகள் வழியில் நல்ல செய்தி வரும்.", prediction_en: "Good news through children." },
                { sign_en: "Capricorn", sign_ta: "மகரம்", prediction_ta: "வீண் செலவுகள் ஏற்படும்.", prediction_en: "Unnecessary expenses." },
                { sign_en: "Aquarius", sign_ta: "கும்பம்", prediction_ta: "திறமைக்கு ஏற்ற அங்கீகாரம் கிடைக்கும்.", prediction_en: "Recognition for talent." },
                { sign_en: "Pisces", sign_ta: "மீனம்", prediction_ta: "உடல் சோர்வு நீங்கி புத்துணர்ச்சி பெறுவீர்கள்.", prediction_en: "Fatigue will disappear and you will be refreshed." }
            ].map(d => ({
                ...d, career_ta: "சிறப்பு", career_en: "Good", finance_ta: "நன்று", finance_en: "Good",
                health_ta: "சிறப்பு", health_en: "Good", lucky_number: "6", lucky_color_ta: "வெள்ளை", lucky_color_en: "White"
            }));
            data = dummyData;
        }

        const mappedData = data.map((item, index) => ({
            ...item,
            signId: index + 1
        }));

        if (sign) {
            const signData = getSignHoroscope(mappedData, sign);
            if (!signData) {
                return res.status(404).json({
                    success: false,
                    error: `Sign '${sign}' not found in horoscope data for ${date}`
                });
            }
            return res.json({
                success: true,
                data: signData
            });
        }

        res.json({
            success: true,
            date,
            count: mappedData.length,
            data: mappedData
        });

    } catch (error) {
        console.error('Daily Horoscope API error:', error);
        res.status(500).json({
            success: false,
            error: error.message || 'Internal server error'
        });
    }
});

/**
 * POST /api/rasi-eng/horoscope/daily
 * Same as GET but using body
 */
router.post('/daily', async (req, res) => {
    try {
        let { date, sign } = req.body;

        if (!date) {
            date = DateTime.now().setZone('Asia/Kolkata').toFormat('yyyy-MM-dd');
        }

        let data = await fetchDailyHoroscope(date);

        if (!data || !Array.isArray(data) || data.length === 0) {
            console.error("No horoscope data found for today or yesterday. Sending dummy data.");
            // Generate basic dummy data for 12 rasis
            const dummyData = [
                { sign_en: "Aries", sign_ta: "மேஷம்", prediction_ta: "இன்று நீங்கள் எதிலும் நிதானத்துடன் செயல்பட வேண்டும்.", prediction_en: "Today you should act with patience in everything." },
                { sign_en: "Taurus", sign_ta: "ரிஷபம்", prediction_ta: "தொழில் வியாபாரத்தில் நல்ல லாபம் கிடைக்கும்.", prediction_en: "Good profit in business and trade." },
                { sign_en: "Gemini", sign_ta: "மிதுனம்", prediction_ta: "எதிர்பார்த்த உதவிகள் தக்க சமயத்தில் கிடைக்கும்.", prediction_en: "Expected help will arrive on time." },
                { sign_en: "Cancer", sign_ta: "கடகம்", prediction_ta: "உடல் ஆரோக்கியத்தில் கவனம் தேவை.", prediction_en: "Need to pay attention to health." },
                { sign_en: "Leo", sign_ta: "சிம்மம்", prediction_ta: "நண்பர்கள் மூலம் ஆதாயம் உண்டாகும்.", prediction_en: "Benefits through friends." },
                { sign_en: "Virgo", sign_ta: "கன்னி", prediction_ta: "வேலை சுமை அதிகரிக்கலாம்.", prediction_en: "Workload may increase." },
                { sign_en: "Libra", sign_ta: "துலாம்", prediction_ta: "பண வரவு தாராளமாக இருக்கும்.", prediction_en: "Cash flow will be generous." },
                { sign_en: "Scorpio", sign_ta: "விருச்சிகம்", prediction_ta: "வாழ்க்கை துணையின் ஆதரவு கிடைக்கும்.", prediction_en: "Support from spouse." },
                { sign_en: "Sagittarius", sign_ta: "தனுசு", prediction_ta: "பிள்ளைகள் வழியில் நல்ல செய்தி வரும்.", prediction_en: "Good news through children." },
                { sign_en: "Capricorn", sign_ta: "மகரம்", prediction_ta: "வீண் செலவுகள் ஏற்படும்.", prediction_en: "Unnecessary expenses." },
                { sign_en: "Aquarius", sign_ta: "கும்பம்", prediction_ta: "திறமைக்கு ஏற்ற அங்கீகாரம் கிடைக்கும்.", prediction_en: "Recognition for talent." },
                { sign_en: "Pisces", sign_ta: "மீனம்", prediction_ta: "உடல் சோர்வு நீங்கி புத்துணர்ச்சி பெறுவீர்கள்.", prediction_en: "Fatigue will disappear and you will be refreshed." }
            ].map(d => ({
                ...d, career_ta: "சிறப்பு", career_en: "Good", finance_ta: "நன்று", finance_en: "Good",
                health_ta: "சிறப்பு", health_en: "Good", lucky_number: "6", lucky_color_ta: "வெள்ளை", lucky_color_en: "White"
            }));
            data = dummyData;
        }

        const mappedData = data.map((item, index) => ({
            ...item,
            signId: index + 1
        }));

        if (sign) {
            const signData = getSignHoroscope(mappedData, sign);
            if (!signData) {
                return res.status(404).json({
                    success: false,
                    error: `Sign '${sign}' not found in horoscope data for ${date}`
                });
            }
            return res.json({
                success: true,
                data: signData
            });
        }

        res.json({
            success: true,
            date,
            count: mappedData.length,
            data: mappedData
        });

    } catch (error) {
        console.error('Daily Horoscope API error:', error);
        res.status(500).json({
            success: false,
            error: error.message || 'Internal server error'
        });
    }
});

module.exports = router;
