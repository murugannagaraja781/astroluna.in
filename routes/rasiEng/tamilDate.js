// routes/rasiEng/tamilDate.js
const express = require('express');
const { DateTime } = require('luxon');
const { getTamilDate, getTamilMonthBoundaries } = require('../../utils/rasiEng/tamilDate');

const router = express.Router();

router.post('/', async (req, res) => {
    try {
        const { date, ayanamsa = 'Lahiri' } = req.body;

        if (!date) {
            return res.status(400).json({ error: 'Missing required field: date' });
        }

        const dt = DateTime.fromISO(date, { zone: 'Asia/Kolkata' });
        if (!dt.isValid) {
            return res.status(400).json({ error: 'Invalid date format. Use ISO format (YYYY-MM-DD).' });
        }

        const tamilDate = await getTamilDate(dt, ayanamsa);

        if (!tamilDate) {
            return res.status(500).json({ error: 'Tamil date calculation failed' });
        }

        res.json({
            success: true,
            data: tamilDate
        });
    } catch (error) {
        console.error('Tamil Date API error:', error);
        res.status(500).json({ error: error.message || 'Calculation failed' });
    }
});

// Get month boundaries for a year
router.post('/boundaries', (req, res) => {
    try {
        const { year, ayanamsa = 'Lahiri' } = req.body;

        if (!year) {
            return res.status(400).json({ error: 'Missing required field: year' });
        }

        const boundaries = getTamilMonthBoundaries(year, ayanamsa);

        res.json({
            success: true,
            data: boundaries
        });
    } catch (error) {
        console.error('Tamil Date Boundaries API error:', error);
        res.status(500).json({ error: error.message || 'Calculation failed' });
    }
});

module.exports = router;
