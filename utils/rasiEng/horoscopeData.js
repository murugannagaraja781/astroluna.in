// utils/rasiEng/horoscopeData.js
const fetch = require('node-fetch');
const { DateTime } = require('luxon');

const BASE_URL = 'https://raw.githubusercontent.com/abinash818/daily-horoscope-data/main/data';

// Simple in-memory cache
const cache = new Map();

/**
 * Fetch daily horoscope data for a specific date with a fallback to previous day
 * @param {string} date - ISO date string (YYYY-MM-DD)
 * @param {boolean} isFallback - internal flag to prevent infinite recursion
 */
async function fetchDailyHoroscope(date, isFallback = false) {
    // Check cache first
    if (cache.has(date)) {
        return cache.get(date);
    }

    const fileName = `horoscope_${date}.json`;
    const url = `${BASE_URL}/${fileName}`;

    try {
        const response = await fetch(url);

        if (!response.ok) {
            // If today's file isn't found and we haven't tried fallback yet, try yesterday
            if (response.status === 404 && !isFallback) {
                const yesterday = DateTime.fromISO(date).minus({ days: 1 }).toFormat('yyyy-MM-dd');
                console.log(`Horoscope for ${date} not found, trying fallback to ${yesterday}`);
                return await fetchDailyHoroscope(yesterday, true);
            }
            throw new Error(`Failed to fetch horoscope for ${date}: ${response.statusText}`);
        }

        let data = await response.json();

        // Handle Gemini API response format
        if (Array.isArray(data) && data[0] && data[0].content && data[0].content.parts) {
            let text = data[0].content.parts[0].text;
            // Remove markdown code blocks if present
            text = text.replace(/```json\n?|```/g, '').trim();
            try {
                data = JSON.parse(text);
            } catch (e) {
                console.error('Failed to parse inner JSON from Gemini response:', e);
                return null;
            }
        }

        // Cache the data (only cache successful fetches)
        if (data) {
            cache.set(date, data);

            // Strategy: Clear cache for dates older than 2 days to prevent memory leaks
            if (cache.size > 5) {
                const keys = Array.from(cache.keys()).sort();
                while (cache.size > 5) {
                    cache.delete(keys.shift());
                }
            }
        }

        return data;
    } catch (error) {
        // Only log serious errors, not 404s which are handled above
        if (!isFallback) {
            console.error('Error fetching horoscope data:', error.message);
        }
        return null;
    }
}

/**
 * Get horoscope for a specific sign from the day's data
 * @param {Array} dayData - Array of 12 sign objects
 * @param {string} sign - Rasi name (English)
 */
function getSignHoroscope(dayData, sign) {
    if (!dayData || !Array.isArray(dayData) || !sign) return null;

    const searchSign = sign.toLowerCase();

    // Support both English and Tamil sign names in query
    return dayData.find(item =>
        (item.sign_en && item.sign_en.toLowerCase() === searchSign) ||
        (item.sign_ta && item.sign_ta === sign)
    );
}

module.exports = {
    fetchDailyHoroscope,
    getSignHoroscope
};
