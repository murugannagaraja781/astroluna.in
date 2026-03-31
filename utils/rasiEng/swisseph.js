// utils/rasiEng/swisseph.js
const { julian } = require('astronomia');
const { PLANET_NAMES } = require('./config');

class AstronomyEngine {
    constructor() {
        if (!AstronomyEngine.instance) {
            AstronomyEngine.instance = this;
        }
        return AstronomyEngine.instance;
    }

    static getInstance() {
        if (!AstronomyEngine.instance) {
            new AstronomyEngine();
        }
        return AstronomyEngine.instance;
    }

    julday(year, month, day, hour) {
        return julian.CalendarGregorianToJD(year, month, day + hour / 24);
    }

    revjul(jd) {
        const result = julian.JDToCalendarGregorian(jd);
        const dayFrac = result.day % 1;
        return {
            year: result.year,
            month: result.month,
            day: Math.floor(result.day),
            hour: dayFrac * 24
        };
    }

    getAyanamsa(jd, ayanamsaName = 'Lahiri') {
        const t = (jd - 2451545.0) / 36525;
        const base = 23.853056;
        const precession = (50.2797 / 3600) * (t * 100);
        let val = base + precession;

        if (ayanamsaName && ayanamsaName.includes('KP')) {
            val += 0.133333;
        }
        return val;
    }

    getHeliocentric(jd, planetId) {
        const T = (jd - 2451545.0) / 36525;

        const elements = {
            0: { L0: 100.466, L1: 36000.77, e: 0.01671, a: 1.000001, M0: 357.529, M1: 35999.05 }, // Earth
            2: { L0: 252.251, L1: 149472.67, e: 0.20563, a: 0.387098, M0: 174.79, M1: 149472.5 },   // Mercury
            3: { L0: 181.980, L1: 58517.81, e: 0.00677, a: 0.723332, M0: 50.416, M1: 58517.8 },    // Venus
            4: { L0: 355.453, L1: 19140.30, e: 0.09341, a: 1.523679, M0: 19.412, M1: 19139.86 },   // Mars
            5: { L0: 34.404, L1: 3034.75, e: 0.04849, a: 5.20336, M0: 19.895, M1: 3034.33 },       // Jupiter
            6: { L0: 49.944, L1: 1222.11, e: 0.05555, a: 9.53707, M0: 316.967, M1: 1221.33 },      // Saturn
        };

        const p = elements[planetId];
        if (!p) return { l: 0, r: 1, b: 0 };

        let L = (p.L0 + p.L1 * T) % 360;
        let M = (p.M0 + p.M1 * T) % 360;

        const Mrad = M * Math.PI / 180;
        const e = p.e;
        // Accurate Equation of Center
        const C = (2 * e - 0.25 * Math.pow(e, 3)) * Math.sin(Mrad)
            + 1.25 * e * e * Math.sin(2 * Mrad)
            + (13 / 12) * Math.pow(e, 3) * Math.sin(3 * Mrad);

        const l = (L + C * 180 / Math.PI + 360) % 360;
        const r = p.a * (1 - e * Math.cos(Mrad));

        return { l, r, b: 0 };
    }

    calcPlanetSidereal(jd, planetId, ayanamsaName = 'Lahiri') {
        const ayanamsa = this.getAyanamsa(jd, ayanamsaName);
        let tropicalLon = 0;
        let speed = 1;

        if (planetId === 0) { // Sun
            const earth = this.getHeliocentric(jd, 0);
            tropicalLon = (earth.l + 180) % 360;
            speed = 0.98;
        } else if (planetId === 1) { // Moon
            const T = (jd - 2451545.0) / 36525;
            let L = (218.3164477 + 481267.88123421 * T) % 360;
            let M = (134.9633964 + 477198.8675055 * T) % 360;
            let D = (297.8501921 + 445267.1114034 * T) % 360; // Elongation

            const Mrad = M * Math.PI / 180;
            const Drad = D * Math.PI / 180;
            // Perturbations
            const C = 6.288774 * Math.sin(Mrad)
                + 1.274027 * Math.sin((2 * Drad - Mrad))
                + 0.658314 * Math.sin(2 * Drad)
                + 0.213618 * Math.sin(2 * Mrad);
            tropicalLon = (L + C + 360) % 360;
            speed = 13.18;
        } else if (planetId === 10) { // Rahu (Mean)
            const T = (jd - 2451545.0) / 36525;
            tropicalLon = (125.044522 - 1934.136261 * T + 360) % 360;
            speed = -0.0529;
        } else {
            const earth = this.getHeliocentric(jd, 0);
            const planet = this.getHeliocentric(jd, planetId);
            const re = earth.r, le = earth.l * Math.PI / 180;
            const rp = planet.r, lp = planet.l * Math.PI / 180;
            const x = rp * Math.cos(lp) - re * Math.cos(le);
            const y = rp * Math.sin(lp) - re * Math.sin(le);
            tropicalLon = (Math.atan2(y, x) * 180 / Math.PI + 360) % 360;

            const dt = 0.01;
            const earth2 = this.getHeliocentric(jd + dt, 0);
            const planet2 = this.getHeliocentric(jd + dt, planetId);
            const x2 = planet2.r * Math.cos(planet2.l * Math.PI / 180) - earth2.r * Math.cos(earth2.l * Math.PI / 180);
            const y2 = planet2.r * Math.sin(planet2.l * Math.PI / 180) - earth2.r * Math.sin(earth2.l * Math.PI / 180);
            let lon2 = (Math.atan2(y2, x2) * 180 / Math.PI + 360) % 360;
            let diff = lon2 - tropicalLon;
            if (diff > 180) diff -= 360;
            if (diff < -180) diff += 360;
            speed = diff / dt;
        }

        const siderealLon = (tropicalLon - ayanamsa + 360) % 360;
        return { longitude: siderealLon, latitude: 0, distance: 1, longitudeSpeed: speed, isRetrograde: speed < 0 };
    }

    getHouses(jd, lat, lng, system = 'Placidus', ayanamsaName = 'Lahiri') {
        const ayanamsa = this.getAyanamsa(jd, ayanamsaName);

        // Robust GMST formula (Meeus)
        const T = (jd - 2451545.0) / 36525;
        let gmst = 280.46061837 + 360.98564736629 * (jd - 2451545.0) + 0.000387933 * T * T - T * T * T / 38710000;
        gmst = (gmst % 360 + 360) % 360;

        const lst = (gmst + lng + 360) % 360;
        const lstRad = lst * Math.PI / 180, latRad = lat * Math.PI / 180;
        const eps = 23.43929 - 0.013 * T;
        const epsRad = eps * Math.PI / 180;

        // Correct Ascendant formula (Meeus)
        const yAsc = Math.cos(lstRad);
        const xAsc = -Math.sin(lstRad) * Math.cos(epsRad) - Math.tan(latRad) * Math.sin(epsRad);
        let asc = Math.atan2(yAsc, xAsc) * 180 / Math.PI;
        asc = (asc + 360) % 360;

        const yMC = Math.sin(lstRad);
        const xMC = Math.cos(lstRad) * Math.cos(epsRad);
        let mc = Math.atan2(yMC, xMC) * 180 / Math.PI;
        mc = (mc + 360) % 360;

        let cusps = new Array(12);
        cusps[0] = asc; cusps[9] = mc; cusps[3] = (mc + 180) % 360; cusps[6] = (asc + 180) % 360;
        const sa1 = (mc - asc + 360) % 360 / 3;
        cusps[10] = (asc + sa1) % 360; cusps[11] = (asc + 2 * sa1) % 360;
        const sa2 = (cusps[3] - asc + 360) % 360 / 3;
        cusps[1] = (asc + sa2) % 360; cusps[2] = (asc + 2 * sa2) % 360;
        cusps[4] = (cusps[10] + 180) % 360; cusps[5] = (cusps[11] + 180) % 360;
        cusps[7] = (cusps[1] + 180) % 360; cusps[8] = (cusps[2] + 180) % 360;

        return {
            cusps: cusps.map(c => (c - ayanamsa + 360) % 360),
            ascendant: (asc - ayanamsa + 360) % 360,
            mc: (mc - ayanamsa + 360) % 360,
            ayanamsaValue: ayanamsa
        };
    }

    getAllPlanets(jd, lat, lng, ayanamsaName = 'Lahiri') {
        const ids = [0, 1, 2, 3, 4, 5, 6];
        const result = ids.map(id => {
            const data = this.calcPlanetSidereal(jd, id, ayanamsaName);
            return { id, name: PLANET_NAMES[id], ...data };
        });

        // Add Rahu & Ketu
        const rahu = this.calcPlanetSidereal(jd, 10, ayanamsaName);
        result.push({ id: 10, name: 'Rahu', ...rahu });
        result.push({ id: 11, name: 'Ketu', longitude: (rahu.longitude + 180) % 360, latitude: 0, distance: 1, longitudeSpeed: rahu.longitudeSpeed, isRetrograde: true });

        // Add Mandi (Gulika)
        const mandiLon = this.getMandiLongitude(jd, lat, lng, ayanamsaName);
        result.push({
            id: 100,
            name: 'Mandi',
            longitude: mandiLon,
            latitude: 0,
            distance: 1,
            longitudeSpeed: 0,
            isRetrograde: false
        });

        return result;
    }

    getMandiLongitude(jd, lat, lng, ayanamsaName = 'Lahiri') {
        const sunrise = this.getSunrise(jd, lat, lng);
        const dateInfo = this.revjul(jd);
        const jsDate = new Date(dateInfo.year, dateInfo.month - 1, dateInfo.day);
        const dayOfWeek = jsDate.getDay(); // 0=Sun, 1=Mon...

        // Mandi entry Ghatikas (Day-part)
        const dayMandiGhatikas = [26, 22, 18, 14, 10, 6, 2];
        const hoursAfterSunrise = dayMandiGhatikas[dayOfWeek] * 0.4;

        const mandiJD = sunrise + (hoursAfterSunrise / 24);
        const ascData = this.getHouses(mandiJD, lat, lng, 'Placidus', ayanamsaName); // Use Ascendant at Mandi Time
        return ascData.ascendant;
    }

    getSign(longitude) {
        const signs = ['Aries', 'Taurus', 'Gemini', 'Cancer', 'Leo', 'Virgo', 'Libra', 'Scorpio', 'Sagittarius', 'Capricorn', 'Aquarius', 'Pisces'];
        const idx = Math.floor((longitude % 360) / 30);
        return { name: signs[idx], index: idx };
    }

    getNakshatra(longitude) {
        const naks = ['Ashwini', 'Bharani', 'Krittika', 'Rohini', 'Mrigashira', 'Ardra', 'Punarvasu', 'Pushya', 'Ashlesha', 'Magha', 'Purva Phalguni', 'Uttara Phalguni', 'Hasta', 'Chitra', 'Swati', 'Vishakha', 'Anuradha', 'Jyeshtha', 'Mula', 'Purva Ashadha', 'Uttara Ashadha', 'Shravana', 'Dhanishta', 'Shatabhisha', 'Purva Bhadrapada', 'Uttara Bhadrapada', 'Revati'];
        const norm = (longitude % 360 + 360) % 360;
        const idx = Math.floor(norm / (360 / 27));
        return { name: naks[idx], index: idx, pada: Math.floor((norm % (360 / 27)) / (360 / 108)) + 1 };
    }

    decimalToDms(decimal) {
        const d = Math.floor(decimal);
        const m = Math.floor((decimal - d) * 60);
        const s = Math.round(((decimal - d) * 60 - m) * 60);
        return { d, m, s };
    }

    getSunrise(jd, lat, lng) {
        const startOfDay = Math.floor(jd - 0.5) + 0.5;
        return startOfDay + (6 - 5.5) / 24;
    }

    getSunset(jd, lat, lng) {
        const startOfDay = Math.floor(jd - 0.5) + 0.5;
        return startOfDay + (18 - 5.5) / 24;
    }
}

const instance = new AstronomyEngine();
module.exports = {
    AstronomyEngine,
    swissEph: instance
};
