// modules/content.js
const express = require('express');
const router = express.Router();
const path = require('path');
const multer = require('multer');

// Horoscope Routers (Assuming they exist in ./routes/)
const rasiEngRouter = require("../routes/rasiEng");
const rasipalanRouter = require("../routes/rasipalan");
const freeHoroscopeRouter = require("../routes/freeHoroscope");

const upload = multer({ dest: path.join(__dirname, '..', 'uploads') });

module.exports = function(io, shared) {
    // Basic Static Content
    router.use("/rasi-eng", rasiEngRouter);
    router.use("/rasipalan", rasipalanRouter);
    router.use("/horoscope", freeHoroscopeRouter);

    // File Upload
    router.post('/upload', upload.single('file'), (req, res) => {
        if (!req.file) return res.status(400).json({ ok: false, error: 'No file uploaded' });
        res.json({ ok: true, url: '/uploads/' + req.file.filename });
    });

    // Policy Routes
    router.get('/privacy-policy', (req, res) => res.sendFile(path.join(__dirname, '..', 'public', 'privacy-policy.html')));
    router.get('/terms-condition', (req, res) => res.sendFile(path.join(__dirname, '..', 'public', 'terms-condition.html')));
    router.get('/refund-cancellation-policy', (req, res) => res.sendFile(path.join(__dirname, '..', 'public', 'refund-cancellation-policy.html')));

    return router;
};
