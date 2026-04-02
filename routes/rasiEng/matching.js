// routes/rasiEng/matching.js
const express = require('express');
const { DateTime } = require('luxon');
const { calculatePorutham, checkKujaDosha, getDashaSandhiComparison } = require('../../utils/rasiEng/matchCalculations');
const { getHouseCusps, getPlanetsWithDetails } = require('../../utils/rasiEng/calculations');
const { swissEph } = require('../../utils/rasiEng/swisseph');

const router = express.Router();

router.post('/', (req, res) => {
    try {
        let { girlData, boyData, ayanamsa = 'KP (Krishnamurti)' } = req.body;

        if (!girlData || !boyData) {
            return res.status(400).json({ error: 'Both girlData and boyData are required' });
        }

        const processProfile = (profile) => {
            if (profile.planets && profile.houses) return profile;

            if (!profile.dob) {
                throw new Error(`Date of birth missing for ${profile.name || 'profile'}`);
            }

            const dt = DateTime.fromFormat(`${profile.dob} ${profile.tob || '12:00'}`, 'yyyy-MM-dd HH:mm');
            if (!dt.isValid) {
                throw new Error(`Invalid date/time format for ${profile.name || 'profile'}: ${profile.dob} ${profile.tob}`);
            }
            const utc = dt.toUTC();
            const jd = swissEph.julday(utc.year, utc.month, utc.day, utc.hour + utc.minute / 60);

            const houses = getHouseCusps(jd, profile.lat, profile.lng, 'Placidus', ayanamsa);
            const planets = getPlanetsWithDetails(jd, profile.lat, profile.lng, houses.cusps, ayanamsa);

            return { ...profile, planets, houses };
        };

        girlData = processProfile(girlData);
        boyData = processProfile(boyData);

        const gMoon = girlData.planets.find(p => p.name === 'Moon');
        const bMoon = boyData.planets.find(p => p.name === 'Moon');

        if (!gMoon || !bMoon) {
            return res.status(400).json({ error: 'Moon position not found in data' });
        }

        const result = calculatePorutham(gMoon.longitude, bMoon.longitude);

        // Additional checks
        const gLagna = girlData.houses && girlData.houses.cusps && girlData.houses.cusps[0] ? Math.floor(girlData.houses.cusps[0] / 30) : 0;
        const bLagna = boyData.houses && boyData.houses.cusps && boyData.houses.cusps[0] ? Math.floor(boyData.houses.cusps[0] / 30) : 0;

        const girlDosha = checkKujaDosha(girlData.planets, true, gLagna);
        const boyDosha = checkKujaDosha(boyData.planets, false, bLagna);

        // Dasha Sandhi
        let sandhi = { hasSandhi: false, overlaps: [], verdict: 'N/A' };
        if (girlData.dob && boyData.dob) {
            sandhi = getDashaSandhiComparison(
                gMoon.longitude,
                DateTime.fromFormat(`${girlData.dob} ${girlData.tob || '12:00'}`, 'yyyy-MM-dd HH:mm'),
                bMoon.longitude,
                DateTime.fromFormat(`${boyData.dob} ${boyData.tob || '12:00'}`, 'yyyy-MM-dd HH:mm')
            );
        }

        const getStarInfo = (moon) => {
            const nakSpan = 360 / 27;
            const idx = Math.floor(moon.longitude / nakSpan) % 27;
            const rasiIdx = Math.floor(moon.longitude / 30) % 12;
            const nak = require('../../utils/rasiEng/config').NAKSHATRAS[idx];
            const rasi = require('../../utils/rasiEng/config').RASIS[rasiIdx];
            return { rasi: rasi.name, nakshatra: nak.name };
        };

        const boyInfo = getStarInfo(bMoon);
        const girlInfo = getStarInfo(gMoon);

        res.json({
            success: true,
            data: {
                ...result,
                percentage: Math.round((result.totalScore / result.maxScore) * 100),
                boy: boyInfo,
                girl: girlInfo,
                girlDosha,
                boyDosha,
                sandhi
            }
        });
    } catch (error) {
        console.error('Matching API error:', error);
        res.status(500).json({ error: error.message || 'Calculation failed' });
    }
});

module.exports = router;
