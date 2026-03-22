// routes/rasiEng/dasha.js
const express = require('express');
const { DateTime } = require('luxon');
const { getVimshottariDasha, getSubPeriods, getFullDashaBreakdown } = require('../../utils/rasiEng/dashaCalculations');

const router = express.Router();

// Get Mahadasha timeline
router.post('/', (req, res) => {
    try {
        const { moonLongitude, birthDate } = req.body;

        if (moonLongitude === undefined || !birthDate) {
            return res.status(400).json({ error: 'Missing required fields: moonLongitude, birthDate' });
        }

        const dt = DateTime.fromISO(birthDate);
        if (!dt.isValid) {
            return res.status(400).json({ error: 'Invalid birthDate format. Use ISO format.' });
        }

        const timeline = getVimshottariDasha(moonLongitude, dt);

        res.json({
            success: true,
            data: timeline
        });
    } catch (error) {
        console.error('Dasha API error:', error);
        res.status(500).json({ error: error.message || 'Calculation failed' });
    }
});

// Get sub-periods (Bhukti/Antara)
router.post('/subperiods', (req, res) => {
    try {
        const { parentStart, parentEnd, parentLord, level = 1 } = req.body;

        if (!parentStart || !parentEnd || !parentLord) {
            return res.status(400).json({ error: 'Missing required fields: parentStart, parentEnd, parentLord' });
        }

        const subPeriods = getSubPeriods(parentStart, parentEnd, parentLord, level);

        res.json({
            success: true,
            data: subPeriods
        });
    } catch (error) {
        console.error('Dasha Subperiods API error:', error);
        res.status(500).json({ error: error.message || 'Calculation failed' });
    }
});

// Get full dasha breakdown with current periods
router.post('/full', (req, res) => {
    try {
        const { moonLongitude, birthDate } = req.body;

        if (moonLongitude === undefined || !birthDate) {
            return res.status(400).json({ error: 'Missing required fields: moonLongitude, birthDate' });
        }

        const dt = DateTime.fromISO(birthDate);
        if (!dt.isValid) {
            return res.status(400).json({ error: 'Invalid birthDate format. Use ISO format.' });
        }

        const breakdown = getFullDashaBreakdown(moonLongitude, dt);

        res.json({
            success: true,
            data: breakdown
        });
    } catch (error) {
        console.error('Dasha Full API error:', error);
        res.status(500).json({ error: error.message || 'Calculation failed' });
    }
});

module.exports = router;
