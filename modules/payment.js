// modules/payment.js
const express = require('express');
const router = express.Router();
const crypto = require('crypto');
const axios = require('axios');
const { User, Payment, BillingLedger } = require('../models');

module.exports = function(io, shared) {
    const { logActivity, userSockets } = shared;

    // Initiate Payment
    router.post('/payment/initiate', async (req, res) => {
        try {
            const { userId, amount, name, phone } = req.body;
            if (!userId || !amount) return res.status(400).json({ ok: false, error: 'Missing params' });

            const transactionId = "TXN_" + Date.now() + "_" + Math.floor(Math.random() * 1000);
            
            // Interaction with PhonePe / Gateway would go here
            // For now, we simulate a successful redirect or token generation
            res.json({ 
                ok: true, 
                transactionId, 
                redirectUrl: `https://astroluna.in/payment/confirm?txnId=${transactionId}&userId=${userId}&amt=${amount}`
            });
        } catch (e) { res.status(500).json({ ok: false, error: e.message }); }
    });

    // Payment Callback
    router.post('/payment/callback', async (req, res) => {
        // Handle gateway callback
        console.log('[Payment] Received callback:', req.body);
        res.json({ ok: true });
    });

    return router;
};
