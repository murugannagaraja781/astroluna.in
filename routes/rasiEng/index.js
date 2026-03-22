// routes/rasiEng/index.js
const express = require('express');
const charts = require('./charts');
const dasha = require('./dasha');
const houses = require('./houses');
const kp = require('./kp');
const matching = require('./matching');
const panchanga = require('./panchanga');
const planets = require('./planets');
const tamilDate = require('./tamilDate');
const horoscope = require('./horoscope');

const router = express.Router();

router.use('/charts', charts);
router.use('/dasha', dasha);
router.use('/houses', houses);
router.use('/kp', kp);
router.use('/matching', matching);
router.use('/panchanga', panchanga);
router.use('/planets', planets);
router.use('/tamil-date', tamilDate);
router.use('/horoscope', horoscope);

module.exports = router;
