// server.js (MODULAR ORCHESTRATOR)
require('dotenv').config();
const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const mongoose = require('mongoose');
const cors = require('cors');
const compression = require('compression');
const path = require('path');
const crypto = require('crypto');

// Core Modules
const state = require('./modules/state');
const callService = require('./modules/call-service');
const authModule = require('./modules/auth');
const adminModule = require('./modules/admin');
const astrologyModule = require('./modules/astrology');
const paymentModule = require('./modules/payment');
const contentModule = require('./modules/content');
const billingModule = require('./modules/billing');
const communicationModule = require('./modules/communication');
const { SystemLog, User } = require('./models');

// Activity Logger
function logActivity(type, message, details = null) {
  const timestamp = new Date().toISOString();
  console.log(`[${timestamp}] [ACTIVITY] [${type.toUpperCase()}] ${message}`);
  try {
    SystemLog.create({ type: 'info', module: type, message, details, timestamp: new Date() }).catch(() => {});
  } catch (err) {}
}

// Shared State & Utils
const shared = {
  ...state,
  ICE_SERVERS: callService.ICE_SERVERS,
  logActivity,
  sendFcmV1Push: callService.sendFcmV1Push,
  generateReferralCode: async (userName) => {
    const prefix = (userName || 'USER').substring(0, 3).toUpperCase().replace(/[^A-Z]/g, 'A');
    const code = `${prefix}${crypto.randomBytes(2).toString('hex').toUpperCase()}`;
    const exists = await User.findOne({ referralCode: code });
    return exists ? shared.generateReferralCode(userName) : code;
  },
  broadcastAstroUpdate: async () => {
    try {
      const astros = await User.find({ role: 'astrologer' }).select('userId name isOnline isAvailable isBusy price image skills experience rating isVerified').lean();
      if (global.io) global.io.emit('astrologer-update', astros);
    } catch (e) { console.error('Astro broadcast error:', e); }
  }
};

const app = express();
const server = http.createServer(app);
const io = new Server(server, { cors: { origin: "*" }, pingTimeout: 60000 });
global.io = io;

// Middleware
app.use(compression());
app.use(cors({ origin: "*" }));
app.use(express.json());
app.use(express.urlencoded({ extended: true }));
app.use(express.static(path.join(__dirname, 'public')));
app.use('/uploads', express.static(path.join(__dirname, 'uploads')));

// Initialize Billing
const billing = billingModule(io, state, shared);
shared.processBillingCharge = billing.processBillingCharge;
shared.endSessionRecord = billing.endSessionRecord;

// Mount Service Routers
app.use('/api', authModule(io, shared));
app.use('/api', astrologyModule(io, shared));
app.use('/api', paymentModule(io, shared));
app.use('/api', callService.init(io, shared));
app.use('/api/admin', adminModule(io, shared));
app.use('/api', contentModule(io, shared));
app.use('/', contentModule(io, shared)); // For root policy pages

// App Wallet Deep Link Handler
app.get('/wallet', (req, res) => {
  const status = req.query.status || 'completed';
  const deepLink = status === 'success' ? 'astroluna://payment-success' : 'astroluna://payment-failed';
  res.send(`<html><head><title>Redirecting...</title></head><body style="text-align:center;padding:50px;">
    <h3>Payment ${status.toUpperCase()}</h3><p>Redirecting you back to AstroLuna App...</p>
    <script>setTimeout(() => { window.location.href = "${deepLink}"; }, 500);</script>
    <a href="${deepLink}" style="padding:15px 30px;background:#E74C3C;color:white;text-decoration:none;border-radius:10px;">Return to App</a>
  </body></html>`);
});

// Signaling
communicationModule(io, shared);

// Root
app.get('/', (req, res) => res.sendFile(path.join(__dirname, 'public', 'index.html')));

// Global Error Handler
app.use((err, req, res, next) => {
  console.error('[CRITICAL] Global Error:', err.message);
  res.status(500).json({ ok: false, error: 'Internal Server Error' });
});

// Database & Listen
const MONGO_URI = process.env.MONGODB_URI || 'mongodb://localhost:27017/astrofive';
mongoose.connect(MONGO_URI)
  .then(() => {
    console.log('✅ Connected to MongoDB');
    server.listen(process.env.PORT || 5000, '0.0.0.0', () => {
      console.log(`🚀 Server listening on port ${process.env.PORT || 5000}`);
      logActivity('system', 'Modular server initialized and listening');
    });
  })
  .catch(err => console.error('✗ MongoDB Connection Error:', err));