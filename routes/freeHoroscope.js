const express = require('express');
const router = express.Router();
const { calculateBirthChart } = require('../utils/astroCalculations');

/**
 * POST /api/horoscope/generate-chart
 * Generate Free Rasi Chart from birth details
 */
router.post('/generate-chart', async (req, res) => {
    try {
        const {
            name,
            dob,
            time,
            country,
            state,
            city,
            birthPlace,
            timezone,
            latitude,
            longitude
        } = req.body;

        // Validate required fields
        if (!name || !dob || !time || !latitude || !longitude) {
            return res.status(400).json({
                ok: false,
                error: 'Missing required fields: name, dob, time, latitude, longitude'
            });
        }

        // Parse date and time
        const [day, month, year] = dob.split('/').map(Number);
        const [hours, minutes] = time.split(':').map(Number);

        // Create Date object
        const birthDate = new Date(year, month - 1, day, hours, minutes);

        // Calculate birth chart
        const chartData = calculateBirthChart(
            birthDate,
            parseFloat(latitude),
            parseFloat(longitude),
            timezone || 'Asia/Kolkata'
        );

        // Add user details to response
        const response = {
            ok: true,
            chart: {
                ...chartData,
                userDetails: {
                    name,
                    dob,
                    time,
                    birthPlace: birthPlace || `${city}, ${state}, ${country}`,
                    latitude: parseFloat(latitude),
                    longitude: parseFloat(longitude),
                    timezone: timezone || 'Asia/Kolkata'
                }
            }
        };

        res.json(response);

    } catch (error) {
        console.error('Error generating chart:', error);
        res.status(500).json({
            ok: false,
            error: error.message || 'Failed to generate chart'
        });
    }
});

module.exports = router;
