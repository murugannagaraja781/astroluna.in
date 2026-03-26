
const { DateTime } = require('luxon');
const { swissEph } = require('./utils/rasiEng/swisseph');
const { getPlanetsWithDetails, getHouseCusps } = require('./utils/rasiEng/calculations');
const { getKPSignificators } = require('./utils/rasiEng/kpCalculations');
const { getVimshottariDasha, getFullDashaBreakdown, getSubPeriods } = require('./utils/rasiEng/dashaCalculations');
const { getPanchanga, getMuhurtas } = require('./utils/rasiEng/panchangaCalc');
const { getTamilDate } = require('./utils/rasiEng/tamilDate');

async function test() {
    try {
        const date = "1990-01-09";
        const time = "22:49";
        const lat = 10.0463; // Alanganallur roughly
        const lng = 78.0435;
        const timezone = 5.5;
        const ayanamsa = 'Lahiri';

        const offsetHours = Math.floor(Math.abs(timezone));
        const offsetMinutes = Math.round((Math.abs(timezone) - offsetHours) * 60);
        const sign = timezone >= 0 ? '+' : '-';
        const zone = `UTC${sign}${String(offsetHours).padStart(2, '0')}:${String(offsetMinutes).padStart(2, '0')}`;

        const dt = DateTime.fromFormat(`${date} ${time}`, "yyyy-MM-dd HH:mm", { zone });
        console.log("DateTime context:", dt.toString());

        const utc = dt.toUTC();
        const jd = swissEph.julday(utc.year, utc.month, utc.day, utc.hour + utc.minute / 60 + utc.second / 3600);
        console.log("Julian Day:", jd);

        const [houses, panchanga, transitJD, tamilDateData] = await Promise.all([
            getHouseCusps(jd, lat, lng, 'Placidus', ayanamsa),
            getPanchanga(jd, lat, lng, ayanamsa),
            swissEph.julday(DateTime.now().toUTC().year, DateTime.now().toUTC().month, DateTime.now().toUTC().day, DateTime.now().toUTC().hour + DateTime.now().toUTC().minute / 60),
            getTamilDate(dt, ayanamsa)
        ]);

        console.log("Houses & Panchanga calculated");

        const planets = getPlanetsWithDetails(jd, houses.cusps, ayanamsa).map(p => ({
            ...p,
            degreeFormatted: p.longitude.toString() // simplified for test
        }));

        const moon = planets.find(p => p.name === 'Moon');
        const moonLon = moon ? moon.longitude : 0;

        const [dashaBreakdown, dashaPeriods] = await Promise.all([
            getFullDashaBreakdown(moonLon, dt),
            getVimshottariDasha(moonLon, dt)
        ]);

        console.log("Dasha calculated", dashaPeriods.length);

        console.log("SUCCESS");
    } catch (err) {
        console.error("TEST FAILED:", err);
    }
}

test();
