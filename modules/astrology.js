// modules/astrology.js
const express = require('express');
const router = express.Router();
const crypto = require('crypto');
const { User, Session, Withdrawal, BillingLedger } = require('../models');

module.exports = function(io, shared) {
    const { logActivity, userSockets, broadcastAstroUpdate, REFERRAL_BONUS_AMOUNT, generateReferralCode } = shared;

    // Get Astrologer List
    router.get('/astrology/astrologers', async (req, res) => {
        try {
            const astrologers = await User.find({ role: 'astrologer' })
                .select('userId name price isOnline isChatOnline isAudioOnline isVideoOnline experience image skills isBusy phone rating')
                .lean();
            res.json({ ok: true, astrologers });
        } catch (e) { res.status(500).json({ ok: false, error: e.message }); }
    });

    // Get Session History
    router.get('/astrology/history/:userId', async (req, res) => {
        try {
            const { userId } = req.params;
            const sessions = await Session.find({
                $or: [{ astrologerId: userId }, { clientId: userId }],
                status: 'ended'
            }).sort({ endTime: -1 }).limit(50).lean();

            const populated = await Promise.all(sessions.map(async (s) => {
                const partnerId = (s.astrologerId === userId) ? s.clientId : s.astrologerId;
                const partner = await User.findOne({ userId: partnerId }).select('name').lean();
                return { ...s, partnerName: partner ? partner.name : 'Unknown User' };
            }));

            res.json({ ok: true, sessions: populated });
        } catch (e) { res.status(500).json({ ok: false, error: e.message }); }
    });

    // Referral Stats
    router.get('/referral/stats/:userId', async (req, res) => {
        try {
            const { userId } = req.params;
            const levels = await User.find({ referredBy: userId }).select('userId name role').lean();
            const earnings = await User.findOne({ userId }).select('referralEarnings referralWithdrawn');
            
            res.json({ 
                ok: true, 
                stats: { 
                    l1: levels.length, 
                    earnings: (earnings?.referralEarnings || 0),
                    withdrawn: (earnings?.referralWithdrawn || 0)
                } 
            });
        } catch (e) { res.status(500).json({ ok: false, error: e.message }); }
    });

    return router;
};
