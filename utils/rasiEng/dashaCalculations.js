// utils/rasiEng/dashaCalculations.js
const { DateTime } = require('luxon');
const { DASHA_SEQUENCE } = require('./config');

/**
 * Calculate Vimshottari Dasha periods from Moon longitude and birth date
 */
function getVimshottariDasha(moonLongitude, birthDate) {
    const nakSpan = 360 / 27;
    const nakIndex = Math.floor(moonLongitude / nakSpan);
    const lordIndex = nakIndex % 9;

    const currentLord = DASHA_SEQUENCE[lordIndex];
    const lonInNak = moonLongitude % nakSpan;
    const elapsedPercent = lonInNak / nakSpan;

    // Calculate starting point (how much of first dasha already passed at birth)
    const daysElapsed = Math.floor(currentLord.years * 365.2425 * elapsedPercent);
    let currentDate = birthDate.minus({ days: daysElapsed });

    const timeline = [];

    for (let i = 0; i < 9; i++) {
        const idx = (lordIndex + i) % 9;
        const lord = DASHA_SEQUENCE[idx];
        const startDate = currentDate;
        const endDate = currentDate.plus({ days: Math.floor(lord.years * 365.2425) });

        timeline.push({
            lord: lord.lord,
            start: startDate.toISO(),
            end: endDate.toISO(),
            level: 1
        });

        currentDate = endDate;
    }

    return timeline;
}

/**
 * Calculate sub-periods (Bhukti) within a Mahadasha
 */
function getSubPeriods(parentStart, parentEnd, parentLord, level) {
    const start = DateTime.fromISO(parentStart);
    const end = DateTime.fromISO(parentEnd);
    const totalDuration = end.diff(start).as('milliseconds');

    const lordIndex = DASHA_SEQUENCE.findIndex(d => d.lord === parentLord);
    if (lordIndex === -1) return [];

    const timeline = [];
    let currentDate = start;

    for (let i = 0; i < 9; i++) {
        const idx = (lordIndex + i) % 9;
        const lord = DASHA_SEQUENCE[idx];
        // Proportion: (Lord Years / 120) * Total Duration
        const subDuration = (lord.years / 120) * totalDuration;
        const endDate = currentDate.plus({ milliseconds: subDuration });

        timeline.push({
            lord: lord.lord,
            start: currentDate.toISO(),
            end: endDate.toISO(),
            level: level + 1
        });

        currentDate = endDate;
    }

    return timeline;
}

/**
 * Get current running dasha period
 */
function getCurrentDasha(timeline) {
    const now = DateTime.now();

    for (const period of timeline) {
        const start = DateTime.fromISO(period.start);
        const end = DateTime.fromISO(period.end);

        if (now >= start && now < end) {
            return period;
        }
    }

    return null;
}

/**
 * Get full dasha breakdown (Mahadasha > Bhukti > Antara)
 */
function getFullDashaBreakdown(moonLongitude, birthDate) {
    const mahadasha = getVimshottariDasha(moonLongitude, birthDate);
    const currentMahadasha = getCurrentDasha(mahadasha);

    let currentBhukti = null;
    let currentAntara = null;

    if (currentMahadasha) {
        const bhuktis = getSubPeriods(
            currentMahadasha.start,
            currentMahadasha.end,
            currentMahadasha.lord,
            1
        );
        currentBhukti = getCurrentDasha(bhuktis);

        if (currentBhukti) {
            const antaras = getSubPeriods(
                currentBhukti.start,
                currentBhukti.end,
                currentBhukti.lord,
                2
            );
            currentAntara = getCurrentDasha(antaras);
        }
    }

    return {
        mahadasha,
        currentMahadasha,
        currentBhukti,
        currentAntara
    };
}

/**
 * Check Dasha Sandhi (transition period overlap)
 */
function checkDashaSandhi(timeline1, timeline2, windowMonths = 6) {
    const now = DateTime.now();
    const futureLimit = now.plus({ years: 60 });
    const overlaps = [];

    for (const d1 of timeline1) {
        const d1End = DateTime.fromISO(d1.end);
        if (d1End < now || d1End > futureLimit) continue;

        const d1StartWindow = d1End.minus({ months: windowMonths });
        const d1EndWindow = d1End.plus({ months: windowMonths });

        for (const d2 of timeline2) {
            const d2End = DateTime.fromISO(d2.end);
            if (d2End < now || d2End > futureLimit) continue;

            const d2StartWindow = d2End.minus({ months: windowMonths });
            const d2EndWindow = d2End.plus({ months: windowMonths });

            // Check overlap
            const overlapStart = d1StartWindow > d2StartWindow ? d1StartWindow : d2StartWindow;
            const overlapEnd = d1EndWindow < d2EndWindow ? d1EndWindow : d2EndWindow;

            if (overlapStart < overlapEnd) {
                overlaps.push({
                    person1Transition: `${d1.lord} -> ${getNextLord(d1.lord)}`,
                    person2Transition: `${d2.lord} -> ${getNextLord(d2.lord)}`,
                    person1Date: d1End.toFormat('dd MMM yyyy'),
                    person2Date: d2End.toFormat('dd MMM yyyy'),
                    overlapMonths: Math.round(overlapEnd.diff(overlapStart, 'months').months)
                });
            }
        }
    }

    return {
        hasSandhi: overlaps.length > 0,
        overlaps
    };
}

function getNextLord(currentLord) {
    const idx = DASHA_SEQUENCE.findIndex(d => d.lord === currentLord);
    return DASHA_SEQUENCE[(idx + 1) % 9].lord;
}

module.exports = {
    getVimshottariDasha,
    getSubPeriods,
    getCurrentDasha,
    getFullDashaBreakdown,
    checkDashaSandhi
};
