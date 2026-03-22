// utils/rasiEng/config.js
const config = {
    port: process.env.PORT || 3001,
    cors: {
        origin: ['http://localhost:3000', 'http://localhost:5173', 'http://127.0.0.1:5173'],
        credentials: true
    }
};

// Ayanamsa mapping
const AYANAMSA_MAP = {
    'Lahiri (Chitra Paksha)': 1,
    'Lahiri': 1,
    'KP (Krishnamurti)': 5,
    'KP': 5,
    'KP Straight': 5,
    'Raman': 3,
    'Fagan/Bradley': 0,
    'J.N. Bhasin': 8
};

// House system mapping
const HOUSE_SYSTEM_MAP = {
    'Placidus': 'P',
    'Koch': 'K',
    'Equal': 'E',
    'WholeSign': 'W',
    'Porphyry': 'O',
    'Regiomontanus': 'R',
    'Campanus': 'C'
};

// Planet IDs for Swiss Ephemeris
const PLANET_IDS = {
    SUN: 0,
    MOON: 1,
    MERCURY: 2,
    VENUS: 3,
    MARS: 4,
    JUPITER: 5,
    SATURN: 6,
    URANUS: 7,
    NEPTUNE: 8,
    PLUTO: 9,
    MEAN_NODE: 10,  // Rahu
    TRUE_NODE: 11
};

const PLANET_NAMES = ['Sun', 'Moon', 'Mercury', 'Venus', 'Mars', 'Jupiter', 'Saturn', 'Uranus', 'Neptune', 'Pluto', 'Rahu', 'Ketu'];

// Nakshatra data
const NAKSHATRAS = [
    { name: 'Ashwini', lord: 'Ketu', gana: 'Deva', yoni: 'Horse', nadi: 'Vata', rajju: 'Pada' },
    { name: 'Bharani', lord: 'Venus', gana: 'Manushya', yoni: 'Elephant', nadi: 'Pitta', rajju: 'Pada' },
    { name: 'Krittika', lord: 'Sun', gana: 'Rakshasa', yoni: 'Goat', nadi: 'Kapha', rajju: 'Kati' },
    { name: 'Rohini', lord: 'Moon', gana: 'Manushya', yoni: 'Serpent', nadi: 'Kapha', rajju: 'Kati' },
    { name: 'Mrigashira', lord: 'Mars', gana: 'Deva', yoni: 'Serpent', nadi: 'Pitta', rajju: 'Nabhi' },
    { name: 'Ardra', lord: 'Rahu', gana: 'Manushya', yoni: 'Dog', nadi: 'Vata', rajju: 'Nabhi' },
    { name: 'Punarvasu', lord: 'Jupiter', gana: 'Deva', yoni: 'Cat', nadi: 'Vata', rajju: 'Kantha' },
    { name: 'Pushya', lord: 'Saturn', gana: 'Deva', yoni: 'Goat', nadi: 'Pitta', rajju: 'Kantha' },
    { name: 'Ashlesha', lord: 'Mercury', gana: 'Rakshasa', yoni: 'Cat', nadi: 'Kapha', rajju: 'Siro' },
    { name: 'Magha', lord: 'Ketu', gana: 'Rakshasa', yoni: 'Rat', nadi: 'Kapha', rajju: 'Siro' },
    { name: 'Purva Phalguni', lord: 'Venus', gana: 'Manushya', yoni: 'Rat', nadi: 'Pitta', rajju: 'Kantha' },
    { name: 'Uttara Phalguni', lord: 'Sun', gana: 'Manushya', yoni: 'Cow', nadi: 'Vata', rajju: 'Kantha' },
    { name: 'Hasta', lord: 'Moon', gana: 'Deva', yoni: 'Buffalo', nadi: 'Vata', rajju: 'Nabhi' },
    { name: 'Chitra', lord: 'Mars', gana: 'Rakshasa', yoni: 'Tiger', nadi: 'Pitta', rajju: 'Nabhi' },
    { name: 'Swati', lord: 'Rahu', gana: 'Deva', yoni: 'Buffalo', nadi: 'Kapha', rajju: 'Kati' },
    { name: 'Vishakha', lord: 'Jupiter', gana: 'Rakshasa', yoni: 'Tiger', nadi: 'Kapha', rajju: 'Kati' },
    { name: 'Anuradha', lord: 'Saturn', gana: 'Deva', yoni: 'Deer', nadi: 'Pitta', rajju: 'Pada' },
    { name: 'Jyeshtha', lord: 'Mercury', gana: 'Rakshasa', yoni: 'Deer', nadi: 'Vata', rajju: 'Pada' },
    { name: 'Mula', lord: 'Ketu', gana: 'Rakshasa', yoni: 'Dog', nadi: 'Vata', rajju: 'Pada' },
    { name: 'Purva Ashadha', lord: 'Venus', gana: 'Manushya', yoni: 'Monkey', nadi: 'Pitta', rajju: 'Kati' },
    { name: 'Uttara Ashadha', lord: 'Sun', gana: 'Manushya', yoni: 'Mongoose', nadi: 'Kapha', rajju: 'Kati' },
    { name: 'Shravana', lord: 'Moon', gana: 'Deva', yoni: 'Monkey', nadi: 'Kapha', rajju: 'Nabhi' },
    { name: 'Dhanishta', lord: 'Mars', gana: 'Rakshasa', yoni: 'Lion', nadi: 'Pitta', rajju: 'Nabhi' },
    { name: 'Shatabhisha', lord: 'Rahu', gana: 'Rakshasa', yoni: 'Horse', nadi: 'Vata', rajju: 'Kantha' },
    { name: 'Purva Bhadrapada', lord: 'Jupiter', gana: 'Manushya', yoni: 'Lion', nadi: 'Vata', rajju: 'Kantha' },
    { name: 'Uttara Bhadrapada', lord: 'Saturn', gana: 'Manushya', yoni: 'Cow', nadi: 'Pitta', rajju: 'Siro' },
    { name: 'Revati', lord: 'Mercury', gana: 'Deva', yoni: 'Elephant', nadi: 'Kapha', rajju: 'Siro' }
];

// Dasha sequence
const DASHA_SEQUENCE = [
    { lord: 'Ketu', years: 7 },
    { lord: 'Venus', years: 20 },
    { lord: 'Sun', years: 6 },
    { lord: 'Moon', years: 10 },
    { lord: 'Mars', years: 7 },
    { lord: 'Rahu', years: 18 },
    { lord: 'Jupiter', years: 16 },
    { lord: 'Saturn', years: 19 },
    { lord: 'Mercury', years: 17 }
];

// Sign Lords
const SIGN_LORDS = ['Mars', 'Venus', 'Mercury', 'Moon', 'Sun', 'Mercury', 'Venus', 'Mars', 'Jupiter', 'Saturn', 'Saturn', 'Jupiter'];

// Sign Names
const SIGN_NAMES = ['Aries', 'Taurus', 'Gemini', 'Cancer', 'Leo', 'Virgo', 'Libra', 'Scorpio', 'Sagittarius', 'Capricorn', 'Aquarius', 'Pisces'];

// Rasi data for matching
const RASIS = [
    { name: 'Aries', lord: 'Mars' },
    { name: 'Taurus', lord: 'Venus' },
    { name: 'Gemini', lord: 'Mercury' },
    { name: 'Cancer', lord: 'Moon' },
    { name: 'Leo', lord: 'Sun' },
    { name: 'Virgo', lord: 'Mercury' },
    { name: 'Libra', lord: 'Venus' },
    { name: 'Scorpio', lord: 'Mars' },
    { name: 'Sagittarius', lord: 'Jupiter' },
    { name: 'Capricorn', lord: 'Saturn' },
    { name: 'Aquarius', lord: 'Saturn' },
    { name: 'Pisces', lord: 'Jupiter' }
];

module.exports = {
    config,
    AYANAMSA_MAP,
    HOUSE_SYSTEM_MAP,
    PLANET_IDS,
    PLANET_NAMES,
    NAKSHATRAS,
    DASHA_SEQUENCE,
    SIGN_LORDS,
    SIGN_NAMES,
    RASIS
};
