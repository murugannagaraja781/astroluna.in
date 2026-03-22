// utils/rasiEng/tamilDate.js
const { DateTime } = require('luxon');
const { swissEph } = require('./swisseph');

const TAMIL_MONTHS = [
    'Chithirai', 'Vaikasi', 'Aani', 'Aadi',
    'Avani', 'Purattasi', 'Aippasi', 'Karthikai',
    'Margazhi', 'Thai', 'Maasi', 'Panguni'
];

const TAMIL_YEARS = [
    'Prabhava', 'Vibhava', 'Sukla', 'Pramodoota', 'Prajorpatti',
    'Angirasa', 'Srimukha', 'Bhava', 'Yuva', 'Dhata',
    'Eeswara', 'Bahudhanya', 'Pramathi', 'Vikrama', 'Vishu',
    'Chitrabhanu', 'Swabhanu', 'Tharana', 'Parthiba', 'Viya',
    'Sarvajith', 'Sarvadhari', 'Virodhi', 'Vikruthi', 'Khara',
    'Nandhana', 'Vijaya', 'Jaya', 'Manmadha', 'Durmukhi',
    'Hevilambi', 'Vilambi', 'Vikari', 'Sarvari', 'Plava',
    'Subhakrith', 'Sobhakrith', 'Krodhi', 'Visvavasu', 'Parabhava',
    'Plavanga', 'Keelaka', 'Saumya', 'Sadharana', 'Virodhikrith',
    'Paridhabhi', 'Pramadhicha', 'Ananda', 'Rakshasa', 'Nala',
    'Pingala', 'Kalayukthi', 'Siddharthi', 'Raudhri', 'Durmathi',
    'Dhundubhi', 'Rudhiradhkari', 'Raktakshi', 'Krodhana', 'Akshaya'
];

// Chennai coordinates for sunrise calculation
const CHENNAI = { lat: 13.0827, lng: 80.2707 };

/**
 * Convert DateTime to Julian Day
 */
function dateTimeToJd(date) {
    const utc = date.toUTC();
    const hour = utc.hour + utc.minute / 60 + utc.second / 3600;
    return swissEph.julday(utc.year, utc.month, utc.day, hour);
}

/**
 * Calculate Tamil Date using Swiss Ephemeris
 */
async function getTamilDate(date, ayanamsaName = 'Lahiri') {
    try {
        const jd = dateTimeToJd(date);

        // Get sunrise for the date
        const sunriseJd = swissEph.getSunrise(jd, CHENNAI.lat, CHENNAI.lng);

        // Determine active date (before sunrise = previous day's date)
        const isBeforeSunrise = jd < sunriseJd;
        const activeDate = isBeforeSunrise ? date.minus({ days: 1 }) : date;
        const activeJd = dateTimeToJd(activeDate);
        const activeSunriseJd = isBeforeSunrise
            ? swissEph.getSunrise(activeJd, CHENNAI.lat, CHENNAI.lng)
            : sunriseJd;

        // Get Sun longitude at active sunrise
        const sun = swissEph.calcPlanetSidereal(activeSunriseJd, 0, ayanamsaName);
        if (!sun) return null;

        const sunLon = sun.longitude;
        const monthIndex = Math.floor(sunLon / 30) % 12;

        // DIRECT CALCULATION: Degree within the sign is the day of the month
        // In Tamil calendar, day changes at sunrise.
        // The degree passed since entering the sign (0-30) corresponds to the date.
        const dayCount = Math.floor(sunLon % 30) + 1;

        // Calculate Tamil year
        let tamilYearGregorian = activeDate.year;

        // Adjust year if we're in months that belong to previous Tamil year
        // (Chithirai is month 0, starts in April)
        if (monthIndex > 0 && activeDate.month < 4) {
            tamilYearGregorian = activeDate.year - 1;
        } else if (activeDate.month === 4 && monthIndex === 11) {
            tamilYearGregorian = activeDate.year - 1;
        }

        const cycleIndex = ((tamilYearGregorian - 1987) % 60 + 60) % 60;
        const tamilYearName = TAMIL_YEARS[cycleIndex];
        const kaliYear = tamilYearGregorian + 3101;
        const thiruvalluvarYear = tamilYearGregorian + 31;

        return {
            day: dayCount,
            month: TAMIL_MONTHS[monthIndex],
            monthIndex,
            year: `${tamilYearName} (${kaliYear} Kali)`,
            yearIndex: cycleIndex,
            yearNumberTamil: thiruvalluvarYear
        };
    } catch (error) {
        console.error('Tamil Date calculation error:', error);
        return null;
    }
}

/**
 * Get Tamil month boundaries for a given Gregorian year
 */
function getTamilMonthBoundaries(year, ayanamsaName = 'Lahiri') {
    const boundaries = [];

    // Start from mid-March of the year
    let currentDate = DateTime.fromObject({ year, month: 3, day: 15 }, { zone: 'Asia/Kolkata' });
    let prevMonth = -1;

    // Scan through the year
    for (let i = 0; i < 400; i++) {
        const jd = dateTimeToJd(currentDate);
        const sunriseJd = swissEph.getSunrise(jd, CHENNAI.lat, CHENNAI.lng);
        const sun = swissEph.calcPlanetSidereal(sunriseJd, 0, ayanamsaName);

        if (sun) {
            const monthIdx = Math.floor(sun.longitude / 30) % 12;

            if (monthIdx !== prevMonth && prevMonth !== -1) {
                boundaries.push({
                    month: TAMIL_MONTHS[monthIdx],
                    startDate: currentDate.toISODate()
                });
            }
            prevMonth = monthIdx;
        }

        currentDate = currentDate.plus({ days: 1 });

        // Stop after finding all 12 months
        if (boundaries.length >= 12) break;
    }

    return boundaries;
}

module.exports = {
    getTamilDate,
    getTamilMonthBoundaries
};
