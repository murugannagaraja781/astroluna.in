// routes/rasiEng/charts.js
const express = require('express');
const { DateTime } = require('luxon');
const { swissEph } = require('../../utils/rasiEng/swisseph');
const { getPlanetsWithDetails, getHouseCusps } = require('../../utils/rasiEng/calculations');
const { getKPSignificators } = require('../../utils/rasiEng/kpCalculations');
const { getVimshottariDasha } = require('../../utils/rasiEng/dashaCalculations');
const { getPanchanga, getMuhurtas } = require('../../utils/rasiEng/panchangaCalc');
const { getTamilDate } = require('../../utils/rasiEng/tamilDate');

const router = express.Router();

router.get('/test', (req, res) => {
    try {
        const jd = swissEph.julday(2026, 3, 26, 12);
        const signs = swissEph.getSign(0);
        res.json({ message: 'Charts router is working!', swissEph: 'OK', sampleJd: jd, sampleSign: signs.name });
    } catch (err) {
        res.json({ message: 'Charts router works, but SwissEph FAILED!', error: err.message });
    }
});

// Helper function to format longitude as degrees/minutes/seconds
function formatLongitude(longitude) {
    const signs = ['Aries', 'Taurus', 'Gemini', 'Cancer', 'Leo', 'Virgo',
        'Libra', 'Scorpio', 'Sagittarius', 'Capricorn', 'Aquarius', 'Pisces'];
    const signIndex = Math.floor(longitude / 30);
    const degInSign = longitude % 30;
    const deg = Math.floor(degInSign);
    const minFloat = (degInSign - deg) * 60;
    const min = Math.floor(minFloat);
    const sec = Math.round((minFloat - min) * 60);
    return `${signs[signIndex]} ${deg}° ${min}' ${sec}"`;
}

router.get('/full-test', async (req, res) => {
    try {
        const { date = "1990-01-09", time = "22:49", lat = 13.0, lng = 80.0, timezone = 5.5, ayanamsa = 'Lahiri' } = req.query;
        return handleFullChart(req, res, { date, time, lat: parseFloat(lat), lng: parseFloat(lng), timezone: parseFloat(timezone), ayanamsa });
    } catch (e) { res.status(500).json({ error: e.message }); }
});

router.post('/full', async (req, res) => {
    return handleFullChart(req, res, req.body);
});

async function handleFullChart(req, res, input) {
    try {
        const {
            date = DateTime.now().setZone('Asia/Kolkata').toFormat('yyyy-MM-dd'),
            time = '12:00',
            lat = 13.0827,
            lng = 80.2707,
            timezone = 5.5,
            ayanamsa = 'Lahiri'
        } = req.body;

        const offsetHours = Math.floor(Math.abs(timezone));
        const offsetMinutes = Math.round((Math.abs(timezone) - offsetHours) * 60);
        const sign = timezone >= 0 ? '+' : '-';
        const zone = `UTC${sign}${String(offsetHours).padStart(2, '0')}:${String(offsetMinutes).padStart(2, '0')}`;

        const dt = DateTime.fromFormat(`${date} ${time}`, "yyyy-MM-dd HH:mm", { zone });

        if (!dt.isValid) {
            return res.status(400).json({ error: 'Invalid date or time format' });
        }

        const utc = dt.toUTC();
        const jd = swissEph.julday(utc.year, utc.month, utc.day, utc.hour + utc.minute / 60 + utc.second / 3600);

        // Calculate all data in parallel for speed
        console.log('Generating full chart for:', { date, time, lat, lng });

        const [houses, panchanga, transitJD, tamilDateData] = await Promise.all([
            getHouseCusps(jd, lat, lng, 'Placidus', ayanamsa),
            getPanchanga(jd, lat, lng, ayanamsa),
            swissEph.julday(DateTime.now().toUTC().year, DateTime.now().toUTC().month, DateTime.now().toUTC().day, DateTime.now().toUTC().hour + DateTime.now().toUTC().minute / 60),
            getTamilDate(dt, ayanamsa)
        ]);
        console.log('Progress: Houses & Panchanga OK');

        const muhurtas = getMuhurtas(jd, lat, lng);

        // Map planets (depends on houses)
        const planets = getPlanetsWithDetails(jd, houses.cusps, ayanamsa).map(p => ({
            ...p,
            degreeFormatted: formatLongitude(p.longitude)
        }));

        // Calculate detailed Dasha
        const moon = planets.find(p => p.name === 'Moon');
        const moonLon = moon ? moon.longitude : 0;

        const { getFullDashaBreakdown, getVimshottariDasha, getSubPeriods } = require('../../utils/rasiEng/dashaCalculations');

        console.log('Calculating Dasha for moonLon:', moonLon);
        const [dashaBreakdown, dashaPeriods] = await Promise.all([
            getFullDashaBreakdown(moonLon, dt),
            getVimshottariDasha(moonLon, dt)
        ]);
        console.log('Progress: Dasha OK (Periods:', dashaPeriods.length, ')');

        let dashaInfo = {
            mahadashaName: "Ketu",
            bhuktiName: "Ketu",
            antaramName: "Ketu",
            remainingYearsInCurrentDasha: 0.0,
            endsAt: ""
        };

        if (dashaBreakdown.currentMahadasha) {
            const end = DateTime.fromISO(dashaBreakdown.currentMahadasha.end);
            dashaInfo = {
                mahadashaName: dashaBreakdown.currentMahadasha.lord,
                bhuktiName: dashaBreakdown.currentBhukti ? dashaBreakdown.currentBhukti.lord : dashaBreakdown.currentMahadasha.lord,
                antaramName: dashaBreakdown.currentAntara ? dashaBreakdown.currentAntara.lord : (dashaBreakdown.currentBhukti ? dashaBreakdown.currentBhukti.lord : ""),
                remainingYearsInCurrentDasha: Math.max(0, end.diff(DateTime.now(), 'years').years),
                endsAt: dashaBreakdown.currentMahadasha.end
            };
        }

        // Get Current Transits
        const rawTransits = swissEph.getAllPlanets(transitJD, ayanamsa);
        const transits = rawTransits.map(t => {
            const sign = swissEph.getSign(t.longitude);
            return {
                name: t.name,
                signName: sign.name,
                isRetrograde: t.isRetrograde
            };
        });

        // Navamsa
        const { getNavamsaSign } = require('../../utils/rasiEng/calculations');
        const navamsaPlanets = planets.map(p => ({
            name: p.name,
            signName: getNavamsaSign(p.longitude)
        }));
        const navamsaAscSign = getNavamsaSign(houses.ascendant);

        const now = DateTime.now();
        const detailedDasha = dashaPeriods.map(md => {
            const mdStart = DateTime.fromISO(md.start);
            const mdEnd = DateTime.fromISO(md.end);
            const isCurrentMD = now >= mdStart && now < mdEnd;
            const isNearMD = now.plus({ years: 10 }) >= mdStart && now.minus({ years: 2 }) <= mdEnd;

            // Level 2: Bhuktis
            const bhuktis = getSubPeriods(md.start, md.end, md.lord, 1);
            
            return {
                ...md,
                subPeriods: bhuktis.map(bh => {
                    // Level 3: Antaras - Only calculate for current or near Mahadashas to save big on JSON size
                    if (isCurrentMD || isNearMD) {
                        const antaras = getSubPeriods(bh.start, bh.end, bh.lord, 2);
                        return { ...bh, subPeriods: antaras };
                    }
                    return bh;
                })
            };
        });

        const chartData = {
            planets,
            houses,
            panchanga: {
                ...panchanga,
                ...muhurtas
            },
            dasha: detailedDasha,
            transits,
            tamilDate: tamilDateData,
            navamsa: {
                planets: navamsaPlanets,
                ascendantSign: navamsaAscSign
            }
        };

        console.log('Generating full chart for:', { date, time, lat, lng });
        res.json({
            success: true,
            version: "v5.5",
            data: chartData
        });
    } catch (error) {
        console.error('Charts Full API error:', error);
        res.status(500).json({ error: error.message || 'Calculation failed' });
    }
}

// Quick chart (planets and houses only)
router.post('/quick', (req, res) => {
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
            return res.status(400).json({ error: 'Invalid date or time format' });
        }

        const utc = dt.toUTC();
        const jd = swissEph.julday(utc.year, utc.month, utc.day, utc.hour + utc.minute / 60 + utc.second / 3600);

        const houses = getHouseCusps(jd, lat, lng, 'Placidus', ayanamsa);
        const planets = getPlanetsWithDetails(jd, houses.cusps, ayanamsa);

        res.json({
            success: true,
            data: {
                planets,
                houses
            }
        });
    } catch (error) {
        console.error('Charts Quick API error:', error);
        res.status(500).json({ error: error.message || 'Calculation failed' });
    }
});

module.exports = router;
