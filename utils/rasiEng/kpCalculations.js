// utils/rasiEng/kpCalculations.js
const { SIGN_LORDS } = require('./config');

/**
 * Calculate KP Significators for planets and houses
 */
function getKPSignificators(planets, houses) {
    const houseLords = houses.cusps.map(cusp => SIGN_LORDS[Math.floor(cusp / 30) % 12]);

    // Planet View: For each planet, find houses it signifies
    const planetView = planets.map(p => {
        const starLord = p.starLord;

        // Level A: Houses occupied by Star Lord
        const starLordOccupantOf = planets
            .filter(pl => pl.name === starLord)
            .map(pl => pl.house)
            .filter(h => h !== undefined);

        // Level B: House occupied by planet
        const houseOccupied = p.house !== undefined ? [p.house] : [];

        // Level C: Houses owned by Star Lord
        const starLordOwnerOf = [];
        houseLords.forEach((lord, idx) => {
            if (lord === starLord) starLordOwnerOf.push(idx + 1);
        });

        // Level D: Houses owned by Planet
        const planetOwnerOf = [];
        houseLords.forEach((lord, idx) => {
            if (lord === p.name) planetOwnerOf.push(idx + 1);
        });

        // Special handling for Rahu/Ketu - they represent their sign lord
        if (p.name === 'Rahu' || p.name === 'Ketu') {
            const nodeSignLord = SIGN_LORDS[Math.floor(p.longitude / 30) % 12];
            houseLords.forEach((lord, idx) => {
                if (lord === nodeSignLord && !planetOwnerOf.includes(idx + 1)) {
                    planetOwnerOf.push(idx + 1);
                }
            });
        }

        return {
            name: p.name,
            isRetrograde: p.isRetrograde,
            levelA: [...new Set(starLordOccupantOf)].sort((a, b) => a - b),
            levelB: houseOccupied,
            levelC: [...new Set(starLordOwnerOf)].sort((a, b) => a - b),
            levelD: [...new Set(planetOwnerOf)].sort((a, b) => a - b)
        };
    });

    // House View: For each house, find signifying planets
    const houseView = Array.from({ length: 12 }, (_, i) => {
        const houseNum = i + 1;

        // Occupants of this house
        const occupants = planets.filter(p => p.house === houseNum);
        const occupantNames = occupants.map(p => p.name);

        // Level 1: Planets in star of occupants
        const planetsInStarOfOccupants = planets
            .filter(p => occupantNames.includes(p.starLord))
            .map(p => p.name);

        // Level 3: Planets in star of Lord
        const lord = houseLords[i];
        const planetsInStarOfLord = planets
            .filter(p => p.starLord === lord)
            .map(p => p.name);

        // Level 4: Lord itself
        return {
            house: houseNum,
            level1: [...new Set(planetsInStarOfOccupants)],
            level2: occupantNames,
            level3: [...new Set(planetsInStarOfLord)],
            level4: [lord],
            lord
        };
    });

    return { planetView, houseView };
}

/**
 * Get ruling planets for KP Horary
 */
function getRulingPlanets(currentTime, lat, lng, moonLongitude, ascendant) {
    const dayLords = ['Sun', 'Moon', 'Mars', 'Mercury', 'Jupiter', 'Venus', 'Saturn'];

    const dayOfWeek = currentTime.getDay();
    const dayLord = dayLords[dayOfWeek];

    const moonSignIndex = Math.floor(moonLongitude / 30) % 12;
    const moonSignLord = SIGN_LORDS[moonSignIndex];

    const moonNakIndex = Math.floor(moonLongitude / (360 / 27)) % 27;
    const starLordSequence = ['Ketu', 'Venus', 'Sun', 'Moon', 'Mars', 'Rahu', 'Jupiter', 'Saturn', 'Mercury'];
    const moonStarLord = starLordSequence[moonNakIndex % 9];

    const lagnaSignIndex = Math.floor(ascendant / 30) % 12;
    const lagnaSignLord = SIGN_LORDS[lagnaSignIndex];

    const lagnaNakIndex = Math.floor(ascendant / (360 / 27)) % 27;
    const lagnaStarLord = starLordSequence[lagnaNakIndex % 9];

    return {
        dayLord,
        moonSignLord,
        moonStarLord,
        lagnaSignLord,
        lagnaStarLord
    };
}

/**
 * Analyze KP significators for specific house matters
 */
function analyzeHouseSignificators(targetHouses, kpData) {
    const planetScores = {};

    kpData.planetView.forEach(planet => {
        let score = 0;

        // Check if planet signifies target houses
        targetHouses.forEach(house => {
            if (planet.levelA.includes(house)) score += 4; // Star lord occupancy
            if (planet.levelB.includes(house)) score += 3; // Planet occupancy
            if (planet.levelC.includes(house)) score += 2; // Star lord ownership
            if (planet.levelD.includes(house)) score += 1; // Planet ownership
        });

        if (score > 0) {
            planetScores[planet.name] = score;
        }
    });

    const sorted = Object.entries(planetScores).sort((a, b) => b[1] - a[1]);

    return {
        strongSignificators: sorted.filter(([, s]) => s >= 6).map(([n]) => n),
        moderateSignificators: sorted.filter(([, s]) => s >= 3 && s < 6).map(([n]) => n),
        weakSignificators: sorted.filter(([, s]) => s > 0 && s < 3).map(([n]) => n)
    };
}

module.exports = {
    getKPSignificators,
    getRulingPlanets,
    analyzeHouseSignificators
};
