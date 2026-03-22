// utils/rasiEng/panchangaCalc.js
const { swissEph } = require('./swisseph');

const TITHIS = [
    'Shukla Pratipada', 'Shukla Dwitiya', 'Shukla Tritiya', 'Shukla Chaturthi', 'Shukla Panchami',
    'Shukla Shashthi', 'Shukla Saptami', 'Shukla Ashtami', 'Shukla Navami', 'Shukla Dashami',
    'Shukla Ekadashi', 'Shukla Dwadashi', 'Shukla Trayodashi', 'Shukla Chaturdashi', 'Purnima',
    'Krishna Pratipada', 'Krishna Dwitiya', 'Krishna Tritiya', 'Krishna Chaturthi', 'Krishna Panchami',
    'Krishna Shashthi', 'Krishna Saptami', 'Krishna Ashtami', 'Krishna Navami', 'Krishna Dashami',
    'Krishna Ekadashi', 'Krishna Dwadashi', 'Krishna Trayodashi', 'Krishna Chaturdashi', 'Amavasya'
];

const YOGAS = [
    'Vishkumbha', 'Priti', 'Ayushman', 'Saubhagya', 'Shobhana', 'Atiganda', 'Sukarma',
    'Dhriti', 'Shula', 'Ganda', 'Vriddhi', 'Dhruva', 'Vyaghata', 'Harshana', 'Vajra',
    'Siddhi', 'Vyatipata', 'Variyan', 'Parigha', 'Shiva', 'Siddha', 'Sadhya', 'Shubha',
    'Shukla', 'Brahma', 'Aindra', 'Vaidhriti'
];

const KARANAS = [
    'Bava', 'Balava', 'Kaulava', 'Taitila', 'Gara', 'Vanija', 'Vishti',
    'Shakuni', 'Chatushpada', 'Naga', 'Kimstughna'
];

const VARAS = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];

/**
 * Calculate Panchanga for a given Julian Day
 */
function getPanchanga(jd, lat, lng, ayanamsaName = 'Lahiri') {
    // Get Sun and Moon positions
    const sun = swissEph.calcPlanetSidereal(jd, 0, ayanamsaName);
    const moon = swissEph.calcPlanetSidereal(jd, 1, ayanamsaName);

    const sunLon = sun ? sun.longitude : 0;
    const moonLon = moon ? moon.longitude : 0;

    // Tithi (Moon - Sun / 12)
    let diff = (moonLon - sunLon + 360) % 360;
    const tithiIndex = Math.floor(diff / 12) % 30;
    const paksha = tithiIndex < 15 ? 'Shukla' : 'Krishna';

    // Nakshatra
    const nakshatra = swissEph.getNakshatra(moonLon);

    // Yoga ((Sun + Moon) / 13.33...)
    const yogaValue = (sunLon + moonLon) % 360;
    const yogaIndex = Math.floor(yogaValue / (360 / 27)) % 27;

    // Karana (Half Tithi)
    const karanaIndex = Math.floor((diff * 2) / 12) % 11;

    // Vara (Day of week)
    const dateInfo = swissEph.revjul(jd);
    const jsDate = new Date(dateInfo.year, dateInfo.month - 1, dateInfo.day);
    const varaIndex = jsDate.getDay();

    // Sunrise and Sunset
    const sunriseJd = swissEph.getSunrise(jd, lat, lng);
    const sunsetJd = swissEph.getSunset(jd, lat, lng);

    const sunriseInfo = swissEph.revjul(sunriseJd);
    const sunsetInfo = swissEph.revjul(sunsetJd);

    const formatTime = (h) => {
        const hours = Math.floor(h);
        const minutes = Math.floor((h - hours) * 60);
        return `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}`;
    };

    // Sign names
    const signNames = ['Aries', 'Taurus', 'Gemini', 'Cancer', 'Leo', 'Virgo', 'Libra', 'Scorpio', 'Sagittarius', 'Capricorn', 'Aquarius', 'Pisces'];

    return {
        tithi: {
            name: TITHIS[tithiIndex],
            index: tithiIndex,
            paksha
        },
        nakshatra: {
            name: nakshatra.name,
            index: nakshatra.index,
            pada: nakshatra.pada,
            lord: nakshatra.lord || ''
        },
        yoga: {
            name: YOGAS[yogaIndex],
            index: yogaIndex
        },
        karana: {
            name: KARANAS[karanaIndex],
            index: karanaIndex
        },
        vara: {
            name: VARAS[varaIndex],
            index: varaIndex
        },
        sunrise: formatTime(sunriseInfo.hour),
        sunset: formatTime(sunsetInfo.hour),
        moonSign: signNames[Math.floor(moonLon / 30) % 12],
        sunSign: signNames[Math.floor(sunLon / 30) % 12]
    };
}

/**
 * Get Rahukalam, Yamagandam, Gulikai for the day
 */
function getMuhurtas(jd, lat, lng) {
    const sunriseJd = swissEph.getSunrise(jd, lat, lng);
    const sunsetJd = swissEph.getSunset(jd, lat, lng);

    const dayDuration = sunsetJd - sunriseJd;
    const muhurtaDuration = dayDuration / 8;

    // Rahukalam sequence: Sun=8, Mon=2, Tue=7, Wed=5, Thu=6, Fri=4, Sat=3
    const rahuSequence = [8, 2, 7, 5, 6, 4, 3];
    // Yamagandam: Sun=5, Mon=4, Tue=3, Wed=2, Thu=1, Fri=7, Sat=6
    const yamaSequence = [5, 4, 3, 2, 1, 7, 6];
    // Gulikai: Sun=7, Mon=6, Tue=5, Wed=4, Thu=3, Fri=2, Sat=1
    const guliSequence = [7, 6, 5, 4, 3, 2, 1];

    const dateInfo = swissEph.revjul(jd);
    const jsDate = new Date(dateInfo.year, dateInfo.month - 1, dateInfo.day);
    const dayOfWeek = jsDate.getDay();

    const formatJdTime = (jdVal) => {
        const info = swissEph.revjul(jdVal);
        const hours = Math.floor(info.hour);
        const minutes = Math.floor((info.hour - hours) * 60);
        return `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}`;
    };

    const rahuStart = sunriseJd + (rahuSequence[dayOfWeek] - 1) * muhurtaDuration;
    const yamaStart = sunriseJd + (yamaSequence[dayOfWeek] - 1) * muhurtaDuration;
    const guliStart = sunriseJd + (guliSequence[dayOfWeek] - 1) * muhurtaDuration;

    return {
        rahukalam: {
            start: formatJdTime(rahuStart),
            end: formatJdTime(rahuStart + muhurtaDuration)
        },
        yamagandam: {
            start: formatJdTime(yamaStart),
            end: formatJdTime(yamaStart + muhurtaDuration)
        },
        gulikai: {
            start: formatJdTime(guliStart),
            end: formatJdTime(guliStart + muhurtaDuration)
        }
    };
}

module.exports = {
    getPanchanga,
    getMuhurtas
};
