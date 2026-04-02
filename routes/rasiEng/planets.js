// routes/rasiEng/planets.js
const express = require('express');
const { DateTime } = require('luxon');
const { swissEph } = require('../../utils/rasiEng/swisseph');
const { getPlanetsWithDetails, getHouseCusps } = require('../../utils/rasiEng/calculations');

const router = express.Router();

router.post('/', (req, res) => {
    try {
        const { date, time, lat, lng, timezone = 5.5, ayanamsa = 'Lahiri' } = req.body;

        if (!date || !time || lat === undefined || lng === undefined) {
            return res.status(400).json({ error: 'Missing required fields: date, time, lat, lng' });
        }

        const offsetHours = Math.floor(Math.abs(timezone));
        const offsetMinutes = Math.round((Math.abs(timezone) - offsetHours) * 60);
        const sign = timezone >= 0 ? '+' : '-';
        const zone = `UTC${sign}${String(offsetHours).padStart(2, '0')}:${String(offsetMinutes).padStart(2, '0')}`;

        const dt = DateTime.fromFormat(`${date} ${time}`, "yyyy-MM-dd HH:mm", { zone });

        if (!dt.isValid) {
            return res.status(400).json({
                error: 'Invalid date or time format',
                reason: dt.invalidReason,
                received: { date, time }
            });
        }

        const utc = dt.toUTC();
        const jd = swissEph.julday(utc.year, utc.month, utc.day, utc.hour + utc.minute / 60 + utc.second / 3600);

        // Get house cusps first (needed for planet house placement)
        const houses = getHouseCusps(jd, lat, lng, 'Placidus', ayanamsa);

        // Get planets with full details
        const planets = getPlanetsWithDetails(jd, lat, lng, houses.cusps, ayanamsa);

        res.json({
            success: true,
            data: {
                planets,
                julianDay: jd,
                ayanamsa: houses.ayanamsaValue
            }
        });
    } catch (error) {
        console.error('Planets API error:', error);
        res.status(500).json({ error: error.message || 'Calculation failed' });
    }
});

module.exports = router;
