// routes/rasiEng/panchanga.js
const express = require('express');
const { DateTime } = require('luxon');
const { swissEph } = require('../../utils/rasiEng/swisseph');
const { getPanchanga, getMuhurtas } = require('../../utils/rasiEng/panchangaCalc');

const router = express.Router();

router.post('/', (req, res) => {
    try {
        const { date, time, lat = 13.08, lng = 80.27, timezone = 5.5, ayanamsa = 'Lahiri' } = req.body;

        if (!date) {
            return res.status(400).json({ error: 'Missing required field: date' });
        }

        const offsetHours = Math.floor(Math.abs(timezone));
        const offsetMinutes = Math.round((Math.abs(timezone) - offsetHours) * 60);
        const sign = timezone >= 0 ? '+' : '-';
        const zone = `UTC${sign}${String(offsetHours).padStart(2, '0')}:${String(offsetMinutes).padStart(2, '0')}`;

        const dt = DateTime.fromFormat(`${date} ${time || '12:00'}`, "yyyy-MM-dd HH:mm", { zone });

        if (!dt.isValid) {
            return res.status(400).json({ error: 'Invalid date or time format' });
        }

        const utc = dt.toUTC();
        const jd = swissEph.julday(utc.year, utc.month, utc.day, utc.hour + utc.minute / 60 + utc.second / 3600);

        const panchanga = getPanchanga(jd, lat, lng, ayanamsa);
        const muhurtas = getMuhurtas(jd, lat, lng);

        res.json({
            success: true,
            data: {
                ...panchanga,
                ...muhurtas,
                date: date,
                location: { lat, lng }
            }
        });
    } catch (error) {
        console.error('Panchanga API error:', error);
        res.status(500).json({ error: error.message || 'Calculation failed' });
    }
});

module.exports = router;
