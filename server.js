// server.js
require('dotenv').config();
const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const mongoose = require('mongoose');
const cors = require('cors');
const compression = require('compression');
const path = require('path');
const crypto = require('crypto');

// Modular Imports
const state = require('./modules/state');
const callService = require('./modules/call-service');
const authModule = require('./modules/auth');
const adminModule = require('./modules/admin');
const astrologyModule = require('./modules/astrology');
const paymentModule = require('./modules/payment');
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
    const prefix = userName.substring(0, 3).toUpperCase().replace(/[^A-Z]/g, 'A');
    const code = `${prefix}${crypto.randomBytes(2).toString('hex').toUpperCase()}`;
    const exists = await User.findOne({ referralCode: code });
    return exists ? shared.generateReferralCode(userName) : code;
  },
  broadcastAstroUpdate: async () => {
    try {
      const astros = await User.find({ role: 'astrologer' }).select('userId name isOnline isAvailable isBusy price image skills').lean();
      if (global.io) global.io.emit('astrologer-update', astros);
    } catch (e) { console.error('Astro broadcast error:', e); }
  }
};

const app = express();
const server = http.createServer(app);
const io = new Server(server, { cors: { origin: "*" }, pingTimeout: 60000 });
global.io = io; // For background broadcasts

// Middleware
app.use(compression());
app.use(cors({ origin: "*" }));
app.use(express.json());
app.use(express.urlencoded({ extended: true }));
app.use(express.static(path.join(__dirname, 'public')));

// Initialize Core Modules
const billing = billingModule(io, state, shared);
shared.processBillingCharge = billing.processBillingCharge;
shared.endSessionRecord = billing.endSessionRecord;

// Mount Service Routers
app.use('/api', authModule(io, shared));
app.use('/api', astrologyModule(io, shared));
app.use('/api', paymentModule(io, shared));
app.use('/api', callService.init(io, shared));
app.use('/api/admin', adminModule(io, shared));

// Signaling & Real-time
communicationModule(io, shared);

// Root & Static Routes
app.get('/', (req, res) => res.sendFile(path.join(__dirname, 'public', 'index.html')));

// Database & Start
const MONGO_URI = process.env.MONGODB_URI || 'mongodb://localhost:27017/astrofive';
mongoose.connect(MONGO_URI, { useNewUrlParser: true, useUnifiedTopology: true })
  .then(() => {
    console.log('✅ MongoDB Connected');
    const PORT = process.env.PORT || 5000;
    server.listen(PORT, '0.0.0.0', () => {
      console.log(`🚀 Server listening on port ${PORT}`);
      logActivity('system', 'Server initialized and listening');
    });
  })
  .catch(err => console.error('✗ MongoDB Error:', err));