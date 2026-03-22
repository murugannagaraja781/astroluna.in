// routes/rasiEng/kp.js
const express = require('express');
const { DateTime } = require('luxon');
const { swissEph } = require('../../utils/rasiEng/swisseph');
const { getPlanetsWithDetails, getHouseCusps } = require('../../utils/rasiEng/calculations');
const { getKPSignificators, getRulingPlanets, analyzeHouseSignificators } = require('../../utils/rasiEng/kpCalculations');

const router = express.Router();

// Get KP Significators
router.post('/', (req, res) => {
    try {
        const { date, time, lat, lng, timezone = 5.5, ayanamsa = 'KP (Krishnamurti)' } = req.body;

        if (!date || !time || lat === undefined || lng === undefined) {
            return res.status(400).json({ error: 'Missing required fields: date, time, lat, lng' });
        }

        const [year, month, day] = date.split('-').map(Number);
        const [hour, minute] = time.split(':').map(Number);

        const zone = `UTC${timezone >= 0 ? '+' : ''}${timezone}`;
        const dt = DateTime.fromObject({ year, month, day, hour, minute }, { zone });
        const utc = dt.toUTC();

        const jd = swissEph.julday(utc.year, utc.month, utc.day, utc.hour + utc.minute / 60);

        const houses = getHouseCusps(jd, lat, lng, 'Placidus', ayanamsa);
        const planets = getPlanetsWithDetails(jd, houses.cusps, ayanamsa);

        const kpSignificators = getKPSignificators(planets, houses);

        res.json({
            success: true,
            data: {
                planets,
                houses,
                kpSignificators
            }
        });
    } catch (error) {
        console.error('KP API error:', error);
        res.status(500).json({ error: error.message || 'Calculation failed' });
    }
});

// Get Ruling Planets (for Horary)
router.post('/ruling', (req, res) => {
    try {
        const { lat = 13.08, lng = 80.27, ayanamsa = 'KP (Krishnamurti)' } = req.body;

        const now = DateTime.now().toUTC();
        const jd = swissEph.julday(now.year, now.month, now.day, now.hour + now.minute / 60);

        const houses = getHouseCusps(jd, lat, lng, 'Placidus', ayanamsa);
        const planets = getPlanetsWithDetails(jd, houses.cusps, ayanamsa);

        const moon = planets.find(p => p.name === 'Moon');
        if (!moon) {
            return res.status(500).json({ error: 'Moon position not found' });
        }

        const ruling = getRulingPlanets(
            new Date(),
            lat,
            lng,
            moon.longitude,
            houses.ascendant
        );

        res.json({
            success: true,
            data: {
                ruling,
                currentTime: now.toISO(),
                moonPosition: moon.longitude,
                ascendant: houses.ascendant
            }
        });
    } catch (error) {
        console.error('KP Ruling API error:', error);
        res.status(500).json({ error: error.message || 'Calculation failed' });
    }
});

// Analyze house significators
router.post('/analyze', (req, res) => {
    try {
        const { date, time, lat, lng, timezone = 5.5, ayanamsa = 'KP (Krishnamurti)', targetHouses } = req.body;

        if (!date || !time || lat === undefined || lng === undefined || !targetHouses) {
            return res.status(400).json({ error: 'Missing required fields: date, time, lat, lng, targetHouses' });
        }

        const [year, month, day] = date.split('-').map(Number);
        const [hour, minute] = time.split(':').map(Number);

        const zone = `UTC${timezone >= 0 ? '+' : ''}${timezone}`;
        const dt = DateTime.fromObject({ year, month, day, hour, minute }, { zone });
        const utc = dt.toUTC();

        const jd = swissEph.julday(utc.year, utc.month, utc.day, utc.hour + utc.minute / 60);

        const houses = getHouseCusps(jd, lat, lng, 'Placidus', ayanamsa);
        const planets = getPlanetsWithDetails(jd, houses.cusps, ayanamsa);
        const kpData = getKPSignificators(planets, houses);

        const analysis = analyzeHouseSignificators(targetHouses, kpData);

        res.json({
            success: true,
            data: {
                targetHouses,
                analysis
            }
        });
    } catch (error) {
        console.error('KP Analyze API error:', error);
        res.status(500).json({ error: error.message || 'Calculation failed' });
    }
});

module.exports = router;
