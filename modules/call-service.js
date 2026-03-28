// modules/call-service.js
const express = require('express');
const router = express.Router();
const admin = require('firebase-admin');
const path = require('path');
const fs = require('fs');
const { User, CallRequest } = require('../models');

// WebRTC TURN Server Config
const TURN_SERVER_URL = process.env.TURN_SERVER_URL || "turn:turn.astroluna.in:3478?transport=udp";
const TURN_SERVER_URL_TCP = process.env.TURN_SERVER_URL_TCP || "turn:turn.astroluna.in:3478?transport=tcp";
const TURN_SERVER_URL_TLS = process.env.TURN_SERVER_URL_TLS || "turns:turn.astroluna.in:5349";
const TURN_SERVER_USERNAME = process.env.TURN_SERVER_USERNAME || "webrtcuser";
const TURN_SERVER_PASSWORD = process.env.TURN_SERVER_PASSWORD || "strongpassword123";

const ICE_SERVERS = [
  { urls: ["stun:stun.l.google.com:19302", "stun:stun1.l.google.com:19302", "stun:stun2.l.google.com:19302"] }
];

if (process.env.TURN_SERVER_URL) {
  ICE_SERVERS.push({
    urls: [process.env.TURN_SERVER_URL, process.env.TURN_SERVER_URL_TCP || process.env.TURN_SERVER_URL, process.env.TURN_SERVER_URL_TLS || process.env.TURN_SERVER_URL].filter(Boolean),
    username: process.env.TURN_SERVER_USERNAME,
    credential: process.env.TURN_SERVER_PASSWORD
  });
}

// Firebase initialization
let callApp = null;
try {
  const serviceAccountPath = path.join(__dirname, '..', 'firebase-service-account.json');
  if (fs.existsSync(serviceAccountPath)) {
    const firebaseServiceAccount = require(serviceAccountPath);
    const projectId = firebaseServiceAccount.project_id || 'unknown';
    callApp = admin.initializeApp({ credential: admin.credential.cert(firebaseServiceAccount) }, 'callServiceApp');
    console.log(`✓ Call Service: Firebase Admin SDK initialized for project: ${projectId}`);
  }
} catch (error) {
  console.warn('✗ Call Service: Failed to initialize Firebase:', error.message);
}

async function sendFcmV1Push(fcmToken, data, notification) {
  if (!callApp) return { success: false, error: 'Firebase not initialized' };
  try {
    const stringData = {};
    if (data) Object.entries(data).forEach(([k, v]) => stringData[k] = String(v));
    const message = { token: fcmToken, data: stringData, android: { priority: 'high' } };
    if (notification) message.notification = { title: notification.title, body: notification.body };
    const result = await callApp.messaging().send(message);
    return { success: true, messageId: result };
  } catch (err) {
    const tokenStart = fcmToken ? fcmToken.substring(0, 10) : 'none';
    const tokenEnd = fcmToken ? fcmToken.substring(fcmToken.length - 10) : 'none';
    console.error(`[FCM] Send error: ${err.message} | Token: ${tokenStart}...${tokenEnd}`);
    
    if (err.message.includes('not found')) {
        console.warn('👉 POSSIBLE CAUSE: The project ID in your service account JSON might not match the project that generated the mobile app token.');
    }
    return { success: false, error: err.message };
  }
}

function init(io, shared) {
    const { 
        logActivity, broadcastAstroUpdate, 
        activeSessions, userSockets, endSessionRecord 
    } = shared;

    // --- Presence ---
    router.post('/astrologer/online', async (req, res) => {
        const { userId, available, fcmToken } = req.body;
        if (!userId) return res.json({ ok: false, error: 'Missing userId' });
        try {
            await User.updateOne({ userId }, { isAvailable: available, isOnline: available, fcmToken, lastSeen: new Date() });
            await broadcastAstroUpdate();
            res.json({ ok: true });
        } catch (e) { res.json({ ok: false }); }
    });

    router.post('/astrologer/service-toggle', async (req, res) => {
        const { userId, service, enabled } = req.body;
        if (!userId || !service) return res.json({ ok: false, error: 'Missing params' });
        try {
            const update = { lastSeen: new Date() };
            if (service === 'chat') update.isChatOnline = enabled;
            else if (service === 'audio') update.isAudioOnline = enabled;
            else if (service === 'video') update.isVideoOnline = enabled;
            
            const user = await User.findOne({ userId });
            if (user) {
                const anyOn = (service === 'chat' ? enabled : user.isChatOnline) || 
                             (service === 'audio' ? enabled : user.isAudioOnline) || 
                             (service === 'video' ? enabled : user.isVideoOnline);
                update.isAvailable = anyOn;
                update.isOnline = anyOn;
            }
            await User.updateOne({ userId }, update);
            await broadcastAstroUpdate();
            res.json({ ok: true });
        } catch (e) { res.json({ ok: false }); }
    });

    // --- Signaling ---
    router.post('/call/initiate', async (req, res) => {
        const { callerId, receiverId } = req.body;
        try {
            const astro = await User.findOne({ userId: receiverId });
            if (!astro || !astro.isAvailable) return res.json({ ok: false, error: 'Offline', code: 'OFFLINE' });
            const callId = "CALL_" + Date.now();
            await CallRequest.create({ callId, callerId, receiverId, status: 'ringing' });
            if (astro.fcmToken) sendFcmV1Push(astro.fcmToken, { type: 'incoming_call', callId, callerId }, { title: 'Incoming Call', body: 'Tap to answer' });
            res.json({ ok: true, callId });
        } catch (e) { res.json({ ok: false }); }
    });

    router.post('/call/accept', async (req, res) => {
        try {
            const { callId } = req.body;
            await CallRequest.updateOne({ callId }, { status: 'accepted' });
            res.json({ ok: true });
        } catch (e) { res.json({ ok: false }); }
    });

    return router;
}

module.exports = { init, ICE_SERVERS, sendFcmV1Push };
