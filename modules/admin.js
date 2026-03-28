// modules/admin.js
const express = require('express');
const router = express.Router();
const { 
    User, Notification, AstrologerApplication, 
    SystemLog, Review, GlobalSettings, BillingLedger, Session
} = require('../models');

/**
 * Admin API Endpoints
 */

module.exports = function(io) {
    // Get Admin Notifications
    router.get('/notifications', async (req, res) => {
        try {
            const notifications = await Notification.find().sort({ createdAt: -1 }).limit(50);
            res.json({ ok: true, notifications });
        } catch (e) { res.status(500).json({ ok: false, error: e.message }); }
    });

    // Mark notifications as read
    router.post('/notifications/read', async (req, res) => {
        try {
            await Notification.updateMany({ read: false }, { read: true });
            res.json({ ok: true });
        } catch (e) { res.status(500).json({ ok: false, error: e.message }); }
    });

    // Get Astrologer Applications
    router.get('/astrologer-applications', async (req, res) => {
        try {
            const { status = 'pending' } = req.query;
            const applications = await AstrologerApplication.find({ status }).sort({ appliedAt: -1 });
            res.json({ ok: true, applications });
        } catch (err) { res.status(500).json({ ok: false, error: err.message }); }
    });

    // Process Application
    router.post('/astrologer/process-application', async (req, res) => {
        try {
            const { applicationId, status, notes } = req.body;
            const application = await AstrologerApplication.findOne({ applicationId });
            if (!application) return res.status(404).json({ ok: false, error: 'Not found' });

            application.status = status;
            application.processedAt = new Date();
            application.notes = notes;
            await application.save();

            if (status === 'approved') {
                await User.updateOne({ phone: application.cellNumber1 }, {
                    role: 'astrologer',
                    name: application.realName,
                    isDocumentVerified: true,
                    documentStatus: 'verified'
                }, { upsert: true });
            }
            res.json({ ok: true });
        } catch (err) { res.status(500).json({ ok: false, error: err.message }); }
    });

    // System Logs
    router.get('/system-logs', async (req, res) => {
        try {
            const logs = await SystemLog.find().sort({ timestamp: -1 }).limit(100);
            res.json({ ok: true, logs });
        } catch (e) { res.status(500).json({ ok: false, error: e.message }); }
    });

    // Global Settings (Slabs)
    router.get('/slabs', async (req, res) => {
        try {
            const settings = await GlobalSettings.findOne({ key: 'slab_percentages' });
            res.json({ ok: true, slabs: settings ? settings.value : {} });
        } catch (e) { res.status(500).json({ ok: false, error: e.message }); }
    });

    return router;
};
