// utils/astroCalculations.js
const { DateTime } = require('luxon');
const { swissEph } = require('./rasiEng/swisseph');
const { getPlanetsWithDetails, getHouseCusps } = require('./rasiEng/calculations');
const { getPanchanga } = require('./rasiEng/panchangaCalc');

/**
 * Main function to calculate all birth chart data
 */
function calculateBirthChart(date, lat, lng, timezone = 'Asia/Kolkata') {
    // 1. Convert local time to GMT/UTC Julian Day
    const dt = DateTime.fromJSDate(date).setZone(timezone);
    const utcDt = dt.toUTC();

    const jd = swissEph.julday(
        utcDt.year,
        utcDt.month,
        utcDt.day,
        utcDt.hour + utcDt.minute / 60 + utcDt.second / 3600
    );

    // 2. Get House Cusps (Placidus system by default)
    const houseData = getHouseCusps(jd, lat, lng, 'Placidus', 'Lahiri');

    // 3. Get Planet Positions
    const planets = getPlanetsWithDetails(jd, houseData.cusps, 'Lahiri');

    // 4. Get Panchanga Data
    const panchanga = getPanchanga(jd, lat, lng, 'Lahiri');

    // 5. Structure final response
    return {
        julianDay: jd,
        ayanamsa: houseData.ayanamsaValue,
        ascendant: houseData.ascendantDetails,
        planets: planets,
        houses: houseData.details,
        panchanga: panchanga
    };
}

module.exports = {
    calculateBirthChart
};
