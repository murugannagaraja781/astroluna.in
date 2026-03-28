// modules/auth.js
const express = require('express');
const router = express.Router();
const crypto = require('crypto');
const { User, Withdrawal, BillingLedger } = require('../models');

/**
 * Authentication & Profile APIs
 */

module.exports = function(io, shared) {
    const { otpStore, generateReferralCode, logActivity, userSockets, REFERRAL_BONUS_AMOUNT } = shared;
    
    // Get Profile
    router.get('/user/:userId', async (req, res) => {
        try {
            const user = await User.findOne({ userId: req.params.userId });
            if (!user) return res.status(404).json({ ok: false, error: 'Not found' });
            res.json({ 
                ok: true, 
                userId: user.userId, 
                name: user.name, 
                phone: user.phone, 
                role: user.role, 
                walletBalance: user.walletBalance,
                isOnline: user.isOnline,
                isAvailable: user.isAvailable,
                isBusy: user.isBusy,
                totalEarnings: user.totalEarnings,
                image: user.image
            });
        } catch (e) { res.status(500).json({ ok: false, error: e.message }); }
    });

    // Send OTP
    router.post('/send-otp', (req, res) => {
        const { phone } = req.body;
        if (!phone) return res.status(400).json({ ok: false, error: 'Phone missing' });
        
        const otp = Math.floor(1000 + Math.random() * 9000).toString();
        // Super Admin Bypass
        if (phone === '9876543210') return res.json({ ok: true }); 
        
        // Test Accounts
        if (phone === '8000000001' || phone === '9000000001') {
            otpStore.set(phone, { otp: '0101', expires: Date.now() + 300000 });
            return res.json({ ok: true });
        }
        
        otpStore.set(phone, { otp, expires: Date.now() + 300000 });
        console.log(`[OTP] ${phone} -> ${otp}`);
        res.json({ ok: true });
    });

    // Verify OTP
    router.post('/verify-otp', async (req, res) => {
        const { phone, otp } = req.body;
        const entry = otpStore.get(phone);
        
        if (phone === '9876543210' && otp === '1369') { // Super Admin Backdoor
            let user = await User.findOne({ phone });
            if (!user) {
                user = await User.create({
                    userId: crypto.randomUUID(), phone, name: 'Super Admin', role: 'superadmin', walletBalance: 100000
                });
            }
            return res.json({ ok: true, userId: user.userId, role: user.role, name: user.name });
        }

        if (!entry || entry.otp !== otp) return res.status(400).json({ ok: false, error: 'Invalid' });
        
        otpStore.delete(phone);
        let user = await User.findOne({ phone });
        if (!user) {
            const userId = crypto.randomUUID();
            user = await User.create({
                userId, phone, name: `User_${crypto.randomBytes(2).toString('hex')}`, role: 'client', walletBalance: 108
            });
        }
        res.json({ ok: true, userId: user.userId, role: user.role, name: user.name, walletBalance: user.walletBalance });
    });

    return router;
};
