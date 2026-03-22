// utils/rasiEng/calculations.js
const { swissEph } = require('./swisseph');
const { SIGN_LORDS } = require('./config');

/**
 * Get full planet data with KP details
 */
function getPlanetsWithDetails(jd, houseCusps, ayanamsaName = 'Lahiri') {
    const rawPlanets = swissEph.getAllPlanets(jd, ayanamsaName);

    return rawPlanets.map(p => {
        const sign = swissEph.getSign(p.longitude);
        const nak = swissEph.getNakshatra(p.longitude);
        const kp = getKPDetails(p.longitude);
        const house = getPlanetHouse(p.longitude, houseCusps);

        return {
            id: p.id,
            name: p.name,
            longitude: p.longitude,
            latitude: p.latitude,
            distance: p.distance,
            speed: p.longitudeSpeed,
            isRetrograde: p.isRetrograde,
            sign: sign.name,
            signName: sign.name,
            signIndex: sign.index,
            house,
            nakshatra: nak.name,
            nakshatraName: nak.name,
            nakshatraIndex: nak.index,
            nakshatraPada: nak.pada,
            signLord: kp.signLord,
            starLord: kp.starLord,
            nakshatraLord: kp.starLord,
            subLord: kp.subLord,
            subSubLord: kp.subSubLord,
            subSubSubLord: kp.subSubSubLord
        };
    });
}

/**
 * Get house cusps with ayanamsa
 */
function getHouseCusps(jd, lat, lng, system = 'Placidus', ayanamsaName = 'Lahiri') {
    const houses = swissEph.getHouses(jd, lat, lng, system, ayanamsaName);

    const details = houses.cusps.map(cusp => {
        const kp = getKPDetails(cusp);
        const sign = swissEph.getSign(cusp);
        const nak = swissEph.getNakshatra(cusp);
        const signAbbr = ['Ari', 'Tau', 'Gem', 'Can', 'Leo', 'Vir', 'Lib', 'Sco', 'Sag', 'Cap', 'Aqu', 'Pis'][sign.index];
        return {
            ...kp,
            signName: sign.name,
            degreeFormatted: formatLongitude(cusp),
            signAbbr,
            nakshatra: nak.name,
            nakshatraIndex: nak.index,
            nakshatraPada: nak.pada
        };
    });

    const ascSign = swissEph.getSign(houses.ascendant);
    const ascNak = swissEph.getNakshatra(houses.ascendant);
    const ascendantDetails = {
        ...getKPDetails(houses.ascendant),
        signName: ascSign.name,
        degreeFormatted: formatLongitude(houses.ascendant),
        signAbbr: ['Ari', 'Tau', 'Gem', 'Can', 'Leo', 'Vir', 'Lib', 'Sco', 'Sag', 'Cap', 'Aqu', 'Pis'][ascSign.index],
        nakshatra: ascNak.name,
        nakshatraIndex: ascNak.index,
        nakshatraPada: ascNak.pada
    };

    return {
        cusps: houses.cusps,
        details,
        ascendant: houses.ascendant,
        ascendantDetails,
        mc: houses.mc,
        vertex: houses.vertex,
        ayanamsaValue: houses.ayanamsaValue,
        system
    };
}

/**
 * Get KP details (Sign Lord, Star Lord, Sub Lord, Sub-Sub Lord, SSS Lord)
 */
function getKPDetails(longitude) {
    // Sign Lord
    const signIndex = Math.floor(longitude / 30) % 12;
    const signLord = SIGN_LORDS[signIndex];

    // Star Lord (Nakshatra Lord)
    const nakSpan = 360 / 27;
    const nakIndex = Math.floor(longitude / nakSpan) % 27;
    const starLordSequence = ['Ketu', 'Venus', 'Sun', 'Moon', 'Mars', 'Rahu', 'Jupiter', 'Saturn', 'Mercury'];
    const starLord = starLordSequence[nakIndex % 9];

    // Sub Lord calculation using Vimshottari proportions
    const dashaYears = [7, 20, 6, 10, 7, 18, 16, 19, 17]; // Total = 120 years
    const totalYears = 120;
    const positionInNak = longitude % nakSpan;

    let accumulatedSub = 0;
    let subLordIndex = nakIndex % 9; // Start from star lord

    for (let i = 0; i < 9; i++) {
        const sidx = (subLordIndex + i) % 9;
        const subSpan = (dashaYears[sidx] / totalYears) * nakSpan;
        accumulatedSub += subSpan;

        if (positionInNak < accumulatedSub) {
            const subLord = starLordSequence[sidx];
            const positionInSub = positionInNak - (accumulatedSub - subSpan);

            // Sub-Sub Lord
            let accumulatedSS = 0;
            for (let j = 0; j < 9; j++) {
                const ssidx = (sidx + j) % 9;
                const ssSpan = (dashaYears[ssidx] / totalYears) * subSpan;
                accumulatedSS += ssSpan;

                if (positionInSub < accumulatedSS) {
                    const subSubLord = starLordSequence[ssidx];
                    const positionInSS = positionInSub - (accumulatedSS - ssSpan);

                    // Sub-Sub-Sub Lord
                    let accumulatedSSS = 0;
                    for (let k = 0; k < 9; k++) {
                        const sssidx = (ssidx + k) % 9;
                        const sssSpan = (dashaYears[sssidx] / totalYears) * ssSpan;
                        accumulatedSSS += sssSpan;

                        if (positionInSS < accumulatedSSS) {
                            return {
                                signLord,
                                starLord,
                                subLord,
                                subSubLord,
                                subSubSubLord: starLordSequence[sssidx]
                            };
                        }
                    }
                    return { signLord, starLord, subLord, subSubLord, subSubSubLord: subSubLord };
                }
            }
            return { signLord, starLord, subLord, subSubLord: subLord, subSubSubLord: subLord };
        }
    }

    return {
        signLord,
        starLord,
        subLord: starLord,
        subSubLord: starLord,
        subSubSubLord: starLord
    };
}

/**
 * Determine which house a planet occupies
 */
function getPlanetHouse(longitude, cusps) {
    for (let i = 0; i < 11; i++) {
        if (isBetween(longitude, cusps[i], cusps[i + 1])) {
            return i + 1;
        }
    }
    return 12;
}

function isBetween(lon, start, end) {
    if (start < end) {
        return lon >= start && lon < end;
    }
    // Wrap around 360
    return lon >= start || lon < end;
}

/**
 * Format longitude as sign position
 */
function formatLongitude(longitude) {
    const sign = swissEph.getSign(longitude);
    const degInSign = longitude % 30;
    const dms = swissEph.decimalToDms(degInSign);
    return `${sign.name} ${dms.d}°${dms.m}'${Math.floor(dms.s)}"`;
}

/**
 * Get position string (e.g., "Ari 15:30:45")
 */
function getPositionString(longitude) {
    const signAbbr = ['Ari', 'Tau', 'Gem', 'Can', 'Leo', 'Vir', 'Lib', 'Sco', 'Sag', 'Cap', 'Aqu', 'Pis'];
    const signIndex = Math.floor(longitude / 30) % 12;
    const degInSign = longitude % 30;
    const dms = swissEph.decimalToDms(degInSign);
    return `${signAbbr[signIndex]} ${String(dms.d).padStart(2, '0')}:${String(dms.m).padStart(2, '0')}:${String(Math.floor(dms.s)).padStart(2, '0')}`;
}

/**
 * Calculate Navamsa sign for a longitude
 */
function getNavamsaSign(longitude) {
    const signs = ['Aries', 'Taurus', 'Gemini', 'Cancer', 'Leo', 'Virgo', 'Libra', 'Scorpio', 'Sagittarius', 'Capricorn', 'Aquarius', 'Pisces'];
    const navamsaLon = (longitude * 9) % 360;
    const idx = Math.floor(navamsaLon / 30);
    return signs[idx];
}

module.exports = {
    getPlanetsWithDetails,
    getHouseCusps,
    getKPDetails,
    getPlanetHouse,
    formatLongitude,
    getPositionString,
    getNavamsaSign
};
