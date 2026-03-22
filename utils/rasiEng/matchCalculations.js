// utils/rasiEng/matchCalculations.js
const { DateTime } = require('luxon');
const { getVimshottariDasha, checkDashaSandhi: checkSandhiRaw } = require('./dashaCalculations');
const { SIGN_LORDS, NAKSHATRAS, RASIS } = require('./config');

// Yoni enemies
const YONI_ENEMIES = {
    'Horse': 'Buffalo', 'Buffalo': 'Horse',
    'Elephant': 'Lion', 'Lion': 'Elephant',
    'Dog': 'Deer', 'Deer': 'Dog',
    'Cat': 'Rat', 'Rat': 'Cat',
    'Serpent': 'Mongoose', 'Mongoose': 'Serpent',
    'Monkey': 'Goat', 'Goat': 'Monkey',
    'Tiger': 'Cow', 'Cow': 'Tiger'
};

// Lord friendships
const LORD_FRIENDSHIPS = {
    'Sun': { 'Moon': 'Friend', 'Mars': 'Friend', 'Jupiter': 'Friend', 'Venus': 'Enemy', 'Saturn': 'Enemy', 'Mercury': 'Neutral', 'Rahu': 'Enemy', 'Ketu': 'Neutral' },
    'Moon': { 'Sun': 'Friend', 'Mercury': 'Friend', 'Jupiter': 'Neutral', 'Venus': 'Neutral', 'Mars': 'Neutral', 'Saturn': 'Neutral', 'Rahu': 'Neutral', 'Ketu': 'Neutral' },
    'Mars': { 'Sun': 'Friend', 'Moon': 'Friend', 'Jupiter': 'Friend', 'Venus': 'Neutral', 'Saturn': 'Neutral', 'Mercury': 'Enemy', 'Rahu': 'Neutral', 'Ketu': 'Neutral' },
    'Mercury': { 'Sun': 'Friend', 'Venus': 'Friend', 'Jupiter': 'Neutral', 'Saturn': 'Neutral', 'Mars': 'Enemy', 'Moon': 'Enemy', 'Rahu': 'Neutral', 'Ketu': 'Neutral' },
    'Jupiter': { 'Sun': 'Friend', 'Moon': 'Friend', 'Mars': 'Friend', 'Saturn': 'Neutral', 'Mercury': 'Enemy', 'Venus': 'Enemy', 'Rahu': 'Enemy', 'Ketu': 'Neutral' },
    'Venus': { 'Mercury': 'Friend', 'Saturn': 'Friend', 'Jupiter': 'Neutral', 'Mars': 'Neutral', 'Sun': 'Enemy', 'Moon': 'Enemy', 'Rahu': 'Neutral', 'Ketu': 'Neutral' },
    'Saturn': { 'Mercury': 'Friend', 'Venus': 'Friend', 'Jupiter': 'Neutral', 'Rahu': 'Friend', 'Sun': 'Enemy', 'Moon': 'Enemy', 'Mars': 'Enemy', 'Ketu': 'Neutral' }
};

function calculatePorutham(gMoonLon, bMoonLon) {
    const nakSpan = 360 / 27;
    const gIdx = Math.floor(gMoonLon / nakSpan) % 27;
    const bIdx = Math.floor(bMoonLon / nakSpan) % 27;

    const gStar = NAKSHATRAS[gIdx];
    const bStar = NAKSHATRAS[bIdx];

    const gRasiIdx = Math.floor(gMoonLon / 30) % 12;
    const bRasiIdx = Math.floor(bMoonLon / 30) % 12;
    const gRasi = RASIS[gRasiIdx];
    const bRasi = RASIS[bRasiIdx];

    const poruthams = [
        { name: 'Dina', score: 0, max: 3, desc: 'Health & Prosperity' },
        { name: 'Gana', score: 0, max: 4, desc: 'Temperament' },
        { name: 'Mahendra', score: 0, max: 1, desc: 'Wealth & Progeny' },
        { name: 'Stree Deergha', score: 0, max: 1, desc: 'Longevity' },
        { name: 'Yoni', score: 0, max: 4, desc: 'Physical compatibility' },
        { name: 'Rasi', score: 0, max: 7, desc: 'Vamsa Vriddhi' },
        { name: 'Rasiyathipathi', score: 0, max: 5, desc: 'Lord friendship' },
        { name: 'Vasya', score: 0, max: 2, desc: 'Attraction' },
        { name: 'Rajju', score: 0, max: 5, desc: 'Mangalya Bhagya', critical: true },
        { name: 'Vedha', score: 0, max: 2, desc: 'Affliction', critical: true },
        { name: 'Nadi', score: 0, max: 2, desc: 'Physiological sync' }
    ];

    const specialStars = ['Mrigashira', 'Magha', 'Swati', 'Anuradha'];
    if (specialStars.includes(gStar.name) || specialStars.includes(bStar.name)) {
        return {
            poruthams: poruthams.map(p => ({ ...p, score: p.max })),
            totalScore: 36,
            maxScore: 36,
            verdict: 'Advisable (Special Star Immunity)',
            isSpecial: true
        };
    }

    const dist = (bIdx - gIdx + 27) % 27 + 1;

    // Dina
    const dinaRem = dist % 9;
    if ([2, 4, 6, 8, 0].includes(dinaRem)) poruthams[0].score = 3;
    if (dist === 27) poruthams[0].score = 3;

    // Gana
    const gGana = gStar.gana;
    const bGana = bStar.gana;
    if (gGana === bGana && gGana !== 'Rakshasa') poruthams[1].score = 4;
    else if (gGana === 'Rakshasa' && bGana === 'Rakshasa') poruthams[1].score = 0;
    else if ((gGana === 'Deva' && bGana === 'Manushya') || (gGana === 'Manushya' && bGana === 'Deva')) poruthams[1].score = 2;
    else if (bGana === 'Rakshasa') poruthams[1].score = 2;
    else if (gGana === 'Rakshasa') poruthams[1].score = dist > 14 ? 2 : 0;

    // Mahendra
    if ([4, 7, 10, 13, 16, 19, 22, 25].includes(dist)) poruthams[2].score = 1;

    // Stree Deergha
    if (dist > 13) poruthams[3].score = 1;

    // Yoni
    if (gStar.yoni === bStar.yoni) poruthams[4].score = 4;
    else if (YONI_ENEMIES[gStar.yoni] === bStar.yoni) poruthams[4].score = 0;
    else poruthams[4].score = 2;

    // Rasi
    const rasiDist = (bRasiIdx - gRasiIdx + 12) % 12 + 1;
    if ([7, 9, 10, 11, 12].includes(rasiDist)) poruthams[5].score = 7;
    else if (rasiDist === 1) poruthams[5].score = 3.5;
    else if (rasiDist === 6 || rasiDist === 8) {
        const rel = LORD_FRIENDSHIPS[gRasi.lord]?.[bRasi.lord] || 'Neutral';
        poruthams[5].score = (gRasi.lord === bRasi.lord || rel === 'Friend') ? 7 : 0;
    }

    // Rasiyathipathi
    const rel = LORD_FRIENDSHIPS[gRasi.lord]?.[bRasi.lord] || 'Neutral';
    if (rel === 'Friend' || gRasi.lord === bRasi.lord) poruthams[6].score = 5;
    else if (rel === 'Neutral') poruthams[6].score = 2.5;

    // Vasya
    const vasyaMap = {
        'Aries': ['Leo', 'Scorpio'], 'Taurus': ['Cancer', 'Libra'], 'Gemini': ['Virgo'],
        'Cancer': ['Scorpio', 'Sagittarius'], 'Leo': ['Libra'], 'Virgo': ['Pisces', 'Gemini'],
        'Libra': ['Virgo', 'Capricorn'], 'Scorpio': ['Cancer'], 'Sagittarius': ['Pisces'],
        'Capricorn': ['Aries', 'Aquarius'], 'Aquarius': ['Aries'], 'Pisces': ['Sagittarius', 'Capricorn']
    };
    if (vasyaMap[gRasi.name]?.includes(bRasi.name)) poruthams[7].score = 2;
    else if (vasyaMap[bRasi.name]?.includes(gRasi.name)) poruthams[7].score = 1;

    // Rajju
    poruthams[8].score = gStar.rajju === bStar.rajju ? 0 : 5;

    // Vedha
    const vedhaPairs = [
        [0, 17], [1, 16], [2, 15], [3, 14], [5, 21], [6, 20],
        [7, 19], [8, 18], [9, 26], [10, 25], [11, 24], [12, 23]
    ];
    const isVedha = vedhaPairs.some(p => (p[0] === gIdx && p[1] === bIdx) || (p[1] === gIdx && p[0] === bIdx));
    poruthams[9].score = isVedha ? 0 : 2;

    // Nadi
    if (gStar.nadi !== bStar.nadi) poruthams[10].score = 2;

    const totalScore = poruthams.reduce((sum, p) => sum + p.score, 0);

    return {
        poruthams,
        totalScore,
        maxScore: 36,
        verdict: totalScore >= 18 && poruthams[8].score > 0 && poruthams[9].score > 0 ? 'Advisable' : 'Not Advisable'
    };
}

function checkKujaDosha(planets, isFemale, lagnaSignIndex) {
    const mars = planets.find(p => p.name === 'Mars');
    if (!mars) return { hasDosha: false, details: 'Mars not found' };

    const marsLon = mars.longitude;
    const marsSign = Math.floor(marsLon / 30);
    const marsHouse = mars.house; // 1-indexed

    // 1. Immunity by Lagna
    if (lagnaSignIndex === 3 || lagnaSignIndex === 4) { // 3: Cancer, 4: Leo
        return { hasDosha: false, desc: 'Lagna Immunity: Cancer/Leo Lagna has no Kuja Dosha.' };
    }

    // 2. Immunity by Mars Rasi
    const immuneSigns = [4, 0, 7, 9, 10]; // Leo, Aries, Scorpio, Capricorn, Aquarius
    if (immuneSigns.includes(marsSign)) {
        return { hasDosha: false, desc: 'Rasi Immunity: Mars in this sign has no Dosha.' };
    }

    // 3. Gender specific rules
    const targetHouses = isFemale ? [1, 4, 8, 12] : [1, 2, 7, 8];
    const doshaHouses = [1, 2, 4, 7, 8, 12];

    let inDoshaHouse = doshaHouses.includes(marsHouse);
    if (!inDoshaHouse) return { hasDosha: false, desc: 'Mars is not in a Dosha house.' };

    // 4. Exceptions (Nivrutti)
    let isNivrutti = false;
    if (marsHouse === 2 && (marsSign === 2 || marsSign === 5)) isNivrutti = true; // Gemini, Virgo
    if (marsHouse === 12 && (marsSign === 1 || marsSign === 6)) isNivrutti = true; // Taurus, Libra
    if (marsHouse === 4 && (marsSign === 0 || marsSign === 7)) isNivrutti = true; // Aries, Scorpio
    if (marsHouse === 7 && (marsSign === 9 || marsSign === 3)) isNivrutti = true; // Capricorn, Cancer
    if (marsHouse === 8 && (marsSign === 8 || marsSign === 11)) isNivrutti = true; // Sagittarius, Pisces
    if (marsHouse === 1 && (marsSign === 0 || marsSign === 7)) isNivrutti = true; // Exception for 1st house (Own)

    if (isNivrutti) {
        return { hasDosha: false, desc: 'Dosha Nivrutti: Position in this Rasi cancels the Dosha.' };
    }

    // 5. Aspect/Conjunction (Pariharam) - Simplified
    const beneficPlanets = ['Jupiter', 'Venus', 'Moon', 'Mercury'];
    const beneficsAspecting = planets.filter(p => {
        if (!beneficPlanets.includes(p.name)) return false;
        // Conjunction or 7th aspect
        if (p.house === marsHouse || Math.abs(p.house - marsHouse) === 6) return true;
        // Jupiter's special aspects
        if (p.name === 'Jupiter') {
            const dist = (marsHouse - p.house + 12) % 12;
            if (dist === 4 || dist === 8) return true;
        }
        return false;
    });

    if (beneficsAspecting.length > 0) {
        return { hasDosha: false, desc: 'Dosha Pariharam: Beneficial aspect/conjunction cancels the Dosha.' };
    }

    const isSpecificDosha = targetHouses.includes(marsHouse);
    return {
        hasDosha: true,
        level: isSpecificDosha ? 'Severe' : 'Normal',
        desc: `Mars in ${marsHouse}th house causing ${isSpecificDosha ? 'Special' : 'General'} Kuja Dosha.`
    };
}

function getDashaSandhiComparison(gMoonLon, gBirth, bMoonLon, bBirth) {
    const gTimeline = getVimshottariDasha(gMoonLon, gBirth);
    const bTimeline = getVimshottariDasha(bMoonLon, bBirth);

    const sandhiResult = checkSandhiRaw(gTimeline, bTimeline);

    return {
        ...sandhiResult,
        verdict: sandhiResult.hasSandhi
            ? 'Caution: Dasha Sandhi overlap detected.'
            : 'Good: No Dasha Sandhi overlaps found.'
    };
}

module.exports = {
    calculatePorutham,
    checkKujaDosha,
    getDashaSandhiComparison
};
