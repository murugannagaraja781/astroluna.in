// server.js
require('dotenv').config(); // Load environment variables from .env file
// Force update timestamp: 2026-01-10 (Sync Fix)d
// Force update timestamp: 2026-01-10
const https = require('https');
const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const path = require('path');
const fs = require('fs');
const crypto = require('crypto');
const mongoose = require('mongoose');
const multer = require('multer');
const admin = require('firebase-admin'); // Firebase Admin for Mobile App
const { DateTime } = require('luxon');
const { fetchDailyHoroscope } = require("./utils/rasiEng/horoscopeData");

// Activity Logger Helper
function logActivity(type, message, details = null) {
  const timestamp = new Date().toISOString();
  let logStr = `\n[${timestamp}] [ACTIVITY] [${type.toUpperCase()}] ${message}`;
  if (details) {
    if (typeof details === 'object') {
      logStr += ` | Data: ${JSON.stringify(details)}`;
    } else {
      logStr += ` | Data: ${details} `;
    }
  }
  console.log(logStr);

  // Optional: Also write to a persistent activity log file
  try {
    fs.appendFileSync('activity.log', logStr + '\n');
  } catch (err) {
    // console.error('Failed to write to activity.log');
  }
}

// PhonePe Config
const PHONEPE_MERCHANT_ID = (process.env.PHONEPE_MERCHANT_ID || "").trim();
const PHONEPE_SALT_KEY = (process.env.PHONEPE_SALT_KEY || "").trim();
const PHONEPE_SALT_INDEX = (process.env.PHONEPE_SALT_INDEX || "").trim();
const PHONEPE_HOST_URL = (process.env.PHONEPE_HOST_URL || "https://api.phonepe.com/apis/hermes").trim();

// PhonePe OAuth Config
const PHONEPE_CLIENT_ID = (process.env.PHONEPE_CLIENT_ID || "").trim();
const PHONEPE_CLIENT_VERSION = (process.env.PHONEPE_CLIENT_VERSION || "1").trim();
const PHONEPE_CLIENT_SECRET = (process.env.PHONEPE_CLIENT_SECRET || "").trim();
// WebRTC TURN Server Config
const TURN_SERVER_URL = process.env.TURN_SERVER_URL || "turn:turn.astroluna.in:3478?transport=udp";
const TURN_SERVER_URL_TCP = process.env.TURN_SERVER_URL_TCP || "turn:turn.astroluna.in:3478?transport=tcp";
const TURN_SERVER_URL_TLS = process.env.TURN_SERVER_URL_TLS || "turns:turn.astroluna.in:5349";
const TURN_SERVER_USERNAME = process.env.TURN_SERVER_USERNAME || "webrtcuser";
const TURN_SERVER_PASSWORD = process.env.TURN_SERVER_PASSWORD || "strongpassword123";

const ICE_SERVERS = [
  { urls: "stun:stun.l.google.com:19302" },
  { 
    urls: TURN_SERVER_URL, 
    username: TURN_SERVER_USERNAME, 
    credential: TURN_SERVER_PASSWORD 
  },
  { 
    urls: TURN_SERVER_URL_TCP, 
    username: TURN_SERVER_USERNAME, 
    credential: TURN_SERVER_PASSWORD 
  },
  { 
    urls: TURN_SERVER_URL_TLS, 
    username: TURN_SERVER_USERNAME, 
    credential: TURN_SERVER_PASSWORD 
  }
];

let phonepeTokenStore = {
  accessToken: null,
  expiresAt: 0 // epoch seconds
};


async function getPhonePeOAuthToken() {
  try {
    // Global PhonePe Identity Manager
    let oauthUrl = "https://api.phonepe.com/apis/identity-manager/v1/oauth/token";

    // Fallback logic if we want to honor sandbox flag
    if (PHONEPE_HOST_URL.includes("sandbox") && !PHONEPE_HOST_URL.includes("hermes")) {
      oauthUrl = "https://api-preprod.phonepe.com/apis/pg-sandbox/v1/oauth/token";
    }


    const params = new URLSearchParams();
    params.append('client_id', PHONEPE_CLIENT_ID);
    params.append('client_version', PHONEPE_CLIENT_VERSION);
    params.append('client_secret', PHONEPE_CLIENT_SECRET);
    params.append('grant_type', 'client_credentials');

    console.log(`[PhonePe OAuth] Requesting token from: ${oauthUrl}`);

    const response = await fetch(oauthUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded'
      },
      body: params
    });

    console.log(`[PhonePe OAuth] Status: ${response.status}`);


    const text = await response.text();
    let data;
    try {
      data = JSON.parse(text);
    } catch (e) {
      console.error("[PhonePe OAuth] Non-JSON Response:", text.substring(0, 500));
      return null;
    }

    if (response.ok && data.access_token) {
      phonepeTokenStore = {
        accessToken: data.access_token,
        expiresAt: data.expires_at || (Math.floor(Date.now() / 1000) + 3600) // Default 1 hour if not provided
      };
      console.log(`[PhonePe OAuth] New Token Generated. Expires at: ${new Date(phonepeTokenStore.expiresAt * 1000).toISOString()}`);
      return data.access_token;
    } else {
      console.error("[PhonePe OAuth] Token Generation Failed:", data);
      return null;
    }
  } catch (err) {
    console.error("[PhonePe OAuth] Error:", err.message);
    return null;
  }
}

async function getValidPhonePeToken() {
  const now = Math.floor(Date.now() / 1000);
  // Refresh if missing or expiring within 5 minutes
  if (!phonepeTokenStore.accessToken || phonepeTokenStore.expiresAt < (now + 300)) {
    return await getPhonePeOAuthToken();
  }
  return phonepeTokenStore.accessToken;
}

// ===== PhonePe Standard Checkout v2 =====
async function callPhonePePayV2(merchantOrderId, amount, redirectUrl, userMobile) {
  const endpoint = "https://api.phonepe.com/apis/pg/checkout/v2/pay";

  // Get OAuth token
  const oauthToken = await getValidPhonePeToken();
  if (!oauthToken) {
    console.error("[PhonePe v2] Failed to get OAuth token");
    return { success: false, data: { message: "OAuth token generation failed" }, status: 401 };
  }

  // Standard Checkout v2 payload
  const payload = {
    merchantOrderId: merchantOrderId,
    amount: amount, // amount in paisa
    expireAfter: 1200, // 20 minutes
    metaInfo: {
      udf1: userMobile || "9999999999"
    },
    paymentFlow: {
      type: "PG_CHECKOUT",
      merchantUrls: {
        redirectUrl: redirectUrl
      }
    }
  };

  const headers = {
    'Content-Type': 'application/json',
    'Authorization': `O-Bearer ${oauthToken}`,
    'accept': 'application/json'
  };

  console.log(`[PhonePe v2] Requesting: ${endpoint}`);
  console.log(`[PhonePe v2] OrderId: ${merchantOrderId}, Amount: ${amount} paisa`);

  let response, data;
  try {
    response = await fetch(endpoint, {
      method: 'POST',
      headers: headers,
      body: JSON.stringify(payload)
    });
    const text = await response.text();
    try {
      data = JSON.parse(text);
    } catch (e) {
      console.error("[PhonePe v2] Non-JSON Response:", text.substring(0, 500));
      return { success: false, data: { message: "External API returned invalid response" }, status: response.status };
    }
  } catch (err) {
    console.error("[PhonePe v2] Fetch Error:", err.message);
    return { success: false, data: { message: "Failed to connect to PhonePe" }, status: 500 };
  }

  // Debug Log
  try {
    const logMsg = `\n--- ${new Date().toISOString()} ---\n[v2 INIT] URL: ${endpoint}\nOrderId: ${merchantOrderId}\nAmount: ${amount}\nStatus: ${response.status}\nRes: ${JSON.stringify(data)}\n`;
    fs.appendFileSync('phonepe_debug.log', logMsg);
  } catch (err) { }

  const isSuccess = response.ok && data.orderId && data.redirectUrl;
  console.log(`[PhonePe v2] Response Status: ${response.status}, Success: ${isSuccess}`);

  return { success: isSuccess, data, status: response.status };
}


// Polyfill for fetch (Node.js 18+ has it built-in)
if (!global.fetch) {
  global.fetch = require('node-fetch');
}

// ===== Referral Helpers =====
async function generateReferralCode(userName) {
  const prefix = userName.substring(0, 3).toUpperCase().replace(/[^A-Z]/g, 'A');
  const random = crypto.randomBytes(2).toString('hex').toUpperCase();
  const code = `${prefix}${random}`;

  // Ensure uniqueness
  const exists = await User.findOne({ referralCode: code });
  if (exists) return generateReferralCode(userName); // Recurse
  return code;
}

// Use a shared secret for L1 referral bonus
const REFERRAL_BONUS_AMOUNT = 51; // Rs. 51 join bonus for referrer
const COMMISSION_L1 = 0.02; // 2%
const COMMISSION_L2 = 0.02; // 2%
const COMMISSION_L3 = 0.01; // 1%
const CASHBACK_CLIENT = 0.02; // 2% for referred client

// FCM Project and Auth
const FCM_PROJECT_ID = 'astroluna-76da1';

// ==========================================
// MOBILE APP FIREBASE INITIALIZATION
// ==========================================
let mobileTokenStore = new Map();
let callApp = null;

try {
  const serviceAccountPath = path.join(__dirname, 'firebase-service-account.json');

  if (!fs.existsSync(serviceAccountPath)) {
    throw new Error(`Service account file not found at: ${serviceAccountPath}`);
  }

  const firebaseServiceAccount = require(serviceAccountPath);
  callApp = admin.initializeApp({
    credential: admin.credential.cert(firebaseServiceAccount)
  }, 'callApp'); // Secondary App Name
  console.log('✓ Call App: Firebase Admin SDK initialized');
  console.log('✓ FCM Project ID:', FCM_PROJECT_ID);
} catch (error) {
  console.warn('✗ Call App: Failed to initialize Firebase Admin SDK (Mobile App)');
  console.warn('  Error:', error.message);
  global.callAppInitError = error.message;
}


// Send FCM v1 Push Notification (Using Firebase Admin SDK) with Legacy Fallback
async function sendFcmV1Push(fcmToken, data, notification) {
  if (!callApp) {
    console.warn('[FCM] Firebase Admin not initialized - trying legacy fallback');
    return await sendFcmLegacy(fcmToken, data, notification);
  }

  try {
    const stringData = {};
    if (data) {
      for (const [key, value] of Object.entries(data)) {
        stringData[key] = String(value || '');
      }
    }
    if (notification) {
      stringData.title = notification.title || '';
      stringData.body = notification.body || '';
    }

    const message = {
      token: fcmToken,
      data: stringData,
      android: {
        priority: 'high',
        ttl: 0
      }
    };

    const result = await callApp.messaging().send(message);
    console.log('[FCM v1] Push sent successfully:', result);
    return { success: true, messageId: result };
  } catch (err) {
    console.error('[FCM v1] Send error:', err.message, '- attempting legacy fallback');
    return await sendFcmLegacy(fcmToken, data, notification);
  }
}

// Legacy FCM logic (FCM v1 Fallback)
async function sendFcmLegacy(token, data, notification) {
  const SERVER_KEY = process.env.FCM_SERVER_KEY;
  if (!SERVER_KEY) {
    console.warn('[FCM Legacy] Missing SERVER_KEY in .env');
    return { success: false, error: 'Missing Server Key' };
  }
  try {
    const payload = {
      to: token,
      data: data,
      priority: 'high'
    };
    if (notification) {
      payload.notification = notification;
    }
    const response = await fetch('https://fcm.googleapis.com/fcm/send', {
      method: 'POST',
      headers: {
        'Authorization': `key=${SERVER_KEY}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(payload)
    });
    const result = await response.json();
    console.log('[FCM Legacy] Sent result:', result);
    return { success: true, result };
  } catch (err) {
    console.error('[FCM Legacy] Error:', err.message);
    return { success: false, error: err.message };
  }
}


const app = express();
const server = http.createServer(app);
const io = new Server(server, {
  pingTimeout: 60000,
  pingInterval: 25000,
  connectTimeout: 45000,
  allowEIO3: true,
  cors: {
    origin: "*",
    methods: ["GET", "POST"]
  }
});

// Helper for safe callbacks
function safeAck(cb, data) {
  if (typeof cb === 'function') {
    try {
      cb(data);
    } catch (e) {
      console.error('[Socket] Callback Error:', e.message);
    }
  }
}

const cors = require("cors");
const compression = require('compression');

app.use(compression());
app.use(cors({ origin: "*" }));

app.use(express.json());
app.use(express.urlencoded({ extended: true }));
app.use(express.static('public'));  // Serve static files

// Policy Page Routes
app.get('/privacy-policy', (req, res) => res.sendFile(path.join(__dirname, 'public', 'privacy-policy.html')));
app.get('/terms-condition', (req, res) => res.sendFile(path.join(__dirname, 'public', 'terms-condition.html')));
app.get('/refund-cancellation-policy', (req, res) => res.sendFile(path.join(__dirname, 'public', 'refund-cancellation-policy.html')));
app.get('/return-policy', (req, res) => res.sendFile(path.join(__dirname, 'public', 'return-policy.html')));
app.get('/shipping-policy', (req, res) => res.sendFile(path.join(__dirname, 'public', 'shipping-policy.html')));

// Fallback Wallet Route for App Users who get redirected to /wallet
app.get('/wallet', (req, res) => {
  const status = req.query.status || 'unknown';
  const reason = req.query.reason || '';

  // Construct Deep Link
  const scheme = status === 'success' ? 'astroluna://payment-success' : 'astroluna://payment-failed';
  const deepLink = `${scheme}?status=${status}&reason=${reason}`;
  const intentUrl = `intent://payment-${status === 'success' ? 'success' : 'failed'}?status=${status}#Intent;scheme=astroluna;package=com.astroluna.app;end`;

  res.send(`
    <html>
      <head>
        <title>Payment Status</title>
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <style>
          body { font-family: sans-serif; padding: 20px; text-align: center; }
          .btn { background: #059669; color: white; padding: 15px 30px; border-radius: 8px; text-decoration: none; display: inline-block; margin-top: 20px; font-weight: bold;}
        </style>
      </head>
      <body>
        <h3>Payment ${status === 'success' ? 'Successful' : 'Completed'}</h3>
        <p>Redirecting you back to the app...</p>
        <a href="${deepLink}" class="btn">Return to Home</a>
        <script>
          // Auto Redirect
          setTimeout(() => { window.location.href = "${intentUrl}"; }, 500);
          setTimeout(() => { window.location.href = "${deepLink}"; }, 1500);
        </script>
      </body>
    </html>
  `);
});

// Policy Page Routes
app.get('/terms-condition', (req, res) => res.sendFile(path.join(__dirname, 'public/terms-condition.html')));
app.get('/refund-cancellation-policy', (req, res) => res.sendFile(path.join(__dirname, 'public/refund-cancellation-policy.html')));
app.get('/return-policy', (req, res) => res.sendFile(path.join(__dirname, 'public/return-policy.html')));
app.get('/shipping-policy', (req, res) => res.sendFile(path.join(__dirname, 'public/shipping-policy.html')));

// Admin Pages
app.get('/admin/astrologer-requests', (req, res) => res.sendFile(path.join(__dirname, 'public/admin/astrologer-requests.html')));
app.get('/admin/deletion-requests', (req, res) => res.sendFile(path.join(__dirname, 'public/admin/deletion-requests.html')));

// Routes
const rasiEngRouter = require("./routes/rasiEng");
const rasipalanRouter = require("./routes/rasipalan");
const freeHoroscopeRouter = require("./routes/freeHoroscope");

app.use("/api/rasi-eng", rasiEngRouter);
app.use("/api/rasipalan", rasipalanRouter);
app.use("/api/horoscope/rasi-palan", rasipalanRouter); // Android App specific path
app.use("/api/horoscope", freeHoroscopeRouter); // Free horoscope chart generation

// --- Astrologer Registration API ---
app.post('/api/astrologer/register', async (req, res) => {
  try {
    const data = req.body;
    if (!data.realName || !data.cellNumber1) {
      return res.status(400).json({ ok: false, error: 'Real name and primary mobile number are required' });
    }

    // Check for existing application
    const existing = await AstrologerApplication.findOne({ cellNumber1: data.cellNumber1, status: 'pending' });
    if (existing) {
      return res.status(400).json({ ok: false, error: 'Application already pending for this number' });
    }

    const applicationId = crypto.randomUUID();
    await AstrologerApplication.create({
      applicationId,
      ...data,
      appliedAt: new Date()
    });

    console.log(`[Registration] New astrologer application from ${data.realName} (${data.cellNumber1})`);
    res.json({ ok: true, message: 'Application submitted successfully' });
  } catch (err) {
    console.error('Registration error:', err);
    res.status(500).json({ ok: false, error: 'Internal server error' });
  }
});

// Admin: Get all astrologer applications
// --- Admin Notifications & Activity ---
app.get('/api/admin/notifications', async (req, res) => {
  try {
    const notifications = await Notification.find().sort({ createdAt: -1 }).limit(50);
    res.json({ ok: true, notifications });
  } catch (e) {
    res.status(500).json({ ok: false, error: e.message });
  }
});

app.post('/api/admin/notifications/read', async (req, res) => {
  try {
    await Notification.updateMany({ read: false }, { read: true });
    res.json({ ok: true });
  } catch (e) {
    res.status(500).json({ ok: false, error: e.message });
  }
});

app.get('/api/admin/astrologers/attended', async (req, res) => {
  try {
    const startOfDay = new Date();
    startOfDay.setHours(0, 0, 0, 0);
    const attendedAstroIds = await Session.distinct('astrologerId', {
      startTime: { $gte: startOfDay },
      $or: [{ duration: { $gt: 0 } }, { status: 'connected' }]
    });
    res.json({ ok: true, attendedAstroIds });
  } catch (e) {
    res.status(500).json({ ok: false, error: e.message });
  }
});

app.get('/api/admin/astrologer-applications', async (req, res) => {
  try {
    const { status = 'pending' } = req.query;
    const applications = await AstrologerApplication.find({ status }).sort({ appliedAt: -1 });
    res.json({ ok: true, applications });
  } catch (err) {
    res.status(500).json({ ok: false, error: err.message });
  }
});

// Admin: Approve/Reject Astrologer Application
app.post('/api/admin/astrologer/process-application', async (req, res) => {
  try {
    const { applicationId, status, notes } = req.body; // status: 'approved' or 'rejected'
    const adminId = 'superadmin'; // Should come from session/auth

    const application = await AstrologerApplication.findOne({ applicationId });
    if (!application) return res.status(404).json({ ok: false, error: 'Application not found' });

    if (status === 'approved') {
      // 1. Create or Update user to Astrologer role
      let user = await User.findOne({ phone: application.cellNumber1 });
      if (user) {
        user.role = 'astrologer';
        user.name = application.realName;
        user.skills = [application.profession || 'Astrology'];
        user.experience = parseInt(application.astrologyExperience) || 0;
        user.isDocumentVerified = true;
        user.documentStatus = 'verified';
        await user.save();
        console.log(`[Admin] Approved application: User ${user.userId} promoted to Astrologer`);
      } else {
        const userId = crypto.randomUUID();
        user = await User.create({
          userId,
          phone: application.cellNumber1,
          name: application.realName,
          role: 'astrologer',
          isDocumentVerified: true,
          documentStatus: 'verified',
          skills: [application.profession || 'Astrology'],
          experience: parseInt(application.astrologyExperience) || 0,
          walletBalance: 0
        });
        console.log(`[Admin] Approved application: New Astrologer created: ${user.phone}`);
      }
    }

    application.status = status;
    application.processedAt = new Date();
    application.notes = notes;
    application.processedBy = adminId;
    await application.save();

    res.json({ ok: true, message: `Application ${status} successfully` });
  } catch (err) {
    console.error('Process application error:', err);
    res.status(500).json({ ok: false, error: err.message });
  }
});

// FCM Test Endpoint - Verify Firebase is working
app.get('/api/test-fcm', async (req, res) => {
  try {
    if (!callApp) {
      return res.json({
        ok: false,
        status: 'NOT_INITIALIZED',
        error: global.callAppInitError || 'Firebase Admin SDK not initialized'
      });
    }

    // Try a simple operation to verify credentials work
    return res.json({
      ok: true,
      status: 'WORKING',
      projectId: FCM_PROJECT_ID,
      message: 'Firebase Admin SDK is properly configured and ready to send FCM messages'
    });
  } catch (err) {
    return res.json({
      ok: false,
      status: 'ERROR',
      error: err.message
    });
  }
});

// ===== MSG91 Helper =====
function sendMsg91(phoneNumber, otp) {
  const cleanPhone = phoneNumber.replace(/\D/g, '');
  const mobile = `91${cleanPhone}`;
  const authKey = process.env.MSG91_AUTH_KEY;
  const templateId = process.env.MSG91_TEMPLATE_ID;

  console.log(`[MSG91 Debug] AuthKey: ${authKey ? 'Set' : 'Missing'}, TemplateID: ${templateId}`);

  // We pass 'otp' param so MSG91 sends OUR generated code
  const path = `/api/v5/otp?otp_expiry=5&template_id=${templateId}&mobile=${mobile}&authkey=${authKey}&realTimeResponse=1&otp=${otp}`;

  const options = {
    method: 'POST',
    hostname: 'control.msg91.com',
    path: path,
    headers: {
      'content-type': 'application/json'
    }
  };

  const req = https.request(options, (res) => {
    let data = '';
    res.on('data', (chunk) => data += chunk);
    res.on('end', () => console.log('MSG91 Result:', data));
  });

  req.on('error', (e) => console.error('MSG91 Error:', e));
  req.write('{}');
  req.end();
}

// ===== File upload setup =====
const uploadDir = path.join(__dirname, 'uploads');
const upload = multer({ dest: uploadDir });

app.use('/uploads', express.static(uploadDir));


app.post('/upload', upload.single('file'), (req, res) => {
  // ... (keeping upload logic if valid) ...
  return res.json({ ok: true, url: req.file ? '/uploads/' + req.file.filename : '' });
});
const MONGO_URI = process.env.MONGODB_URI || 'mongodb://localhost:27017/astrofive';

// Helper function to check if MongoDB is connected
const isMongoConnected = () => {
  return mongoose.connection.readyState === 1;
};

// Helper function for safe database operations
const safeDbOperation = async (operation, fallbackValue = null) => {
  if (!isMongoConnected()) {
    console.warn('⚠️  MongoDB not connected, skipping database operation');
    return fallbackValue;
  }
  try {
    return await operation();
  } catch (err) {
    console.error('Database operation error:', err.message);
    return fallbackValue;
  }
};

// MongoDB Connection with retry logic
const connectDB = async (retries = 5) => {
  try {
    await mongoose.connect(MONGO_URI, {
      serverSelectionTimeoutMS: 10000,
      socketTimeoutMS: 45000,
      maxPoolSize: 10,
      minPoolSize: 2
    });
    console.log('✅ MongoDB Connected to:', MONGO_URI.split('@').pop().split('?')[0]);
    if (process.env.NODE_ENV !== 'test') {
      seedDatabase();
    }
  } catch (err) {
    console.error('❌ MongoDB Connection Error:', err.message);

    if (err.message.includes('IP that isn\'t whitelisted') || err.message.includes('IP whitelist')) {
      console.error('👉 ACTION NEEDED: Login to MongoDB Atlas and whitelist your server IP');
      console.error('   Go to: Network Access → Add IP Address → Allow Access from Anywhere (0.0.0.0/0)');
    }

    if (retries > 0) {
      console.log(`🔄 Retrying MongoDB connection... (${retries} attempts left)`);
      setTimeout(() => connectDB(retries - 1), 5000);
    } else {
      console.error('❌ MongoDB connection failed after all retries');
      console.error('⚠️  Server will continue without database (some features may not work)');
    }
  }
};

// Handle MongoDB connection events
mongoose.connection.on('connected', () => {
  console.log('📡 Mongoose connected to MongoDB');
});

mongoose.connection.on('error', (err) => {
  console.error('❌ Mongoose connection error:', err.message);
});

mongoose.connection.on('disconnected', () => {
  console.log('📴 Mongoose disconnected from MongoDB');
});

// Graceful shutdown
process.on('SIGINT', async () => {
  try {
    await mongoose.connection.close();
    console.log('MongoDB connection closed through app termination');
    process.exit(0);
  } catch (err) {
    console.error('Error closing MongoDB connection:', err);
    process.exit(1);
  }
});

// Start connection
connectDB();

// Schemas
const UserSchema = new mongoose.Schema({
  userId: { type: String, unique: true },
  phone: { type: String, unique: true },
  name: String,
  role: { type: String, enum: ['client', 'astrologer', 'superadmin'], default: 'client' },
  isOnline: { type: Boolean, default: false },
  isChatOnline: { type: Boolean, default: false },
  isAudioOnline: { type: Boolean, default: false },
  isVideoOnline: { type: Boolean, default: false },
  isBanned: { type: Boolean, default: false },
  skills: [String],
  price: { type: Number, default: 20 },
  walletBalance: { type: Number, default: 108 },
  totalEarnings: { type: Number, default: 0 }, // Phase 16: Lifetime Earnings
  experience: { type: Number, default: 0 },
  isVerified: { type: Boolean, default: false }, // Blue Tick
  isDocumentVerified: { type: Boolean, default: false }, // Legacy Boolean
  documentStatus: { type: String, enum: ['none', 'processing', 'verified'], default: 'none' }, // New Status
  image: { type: String, default: '' },
  birthDetails: {
    dob: String,
    tob: String,
    pob: String,
    lat: Number,
    lon: Number
  },
  // Phase Extra: Persistent Intake Form Details
  intakeDetails: {
    gender: String,
    marital: String,
    occupation: String,
    topic: String,
    partner: {
      name: String,
      dob: String,
      tob: String,
      pob: String
    }
  },
  // Phase 2: Reliable Calling Fields
  isAvailable: { type: Boolean, default: false }, // Explicit Online Toggle
  isBusy: { type: Boolean, default: false }, // Currently in session
  availabilityExpiresAt: Date, // Safety timeout
  fcmToken: String, // Push Notification Token
  lastSeen: { type: Date, default: Date.now },

  // Referral System Fields
  referredBy: { type: String, default: null }, // userId of the referrer
  referralCode: { type: String, unique: true, sparse: true }, // unique code for sharing
  hasRecharged: { type: Boolean, default: false }, // Only rechargers activate referral commission
  referralEarnings: { type: Number, default: 0 }, // Total amount earned from referrals
  referralWithdrawn: { type: Number, default: 0 }, // Total amount withdrawn from referral earnings

  // Astrologer Registration Request
  astrologerRequestStatus: { type: String, enum: ['none', 'pending', 'approved', 'rejected'], default: 'none' },
  astrologerRequestedAt: Date,
  astrologerExperience: String, // Short description from the applicant
  astrologerAbout: String, // Bio
  astrologerSkills: [String], // Array of skills
  bankDetails: {
    accountHolder: String,
    accountNumber: String,
    bankName: String,
    ifscCode: String,
    upiId: String
  }
});

const CallRequestSchema = new mongoose.Schema({
  callId: { type: String, unique: true },
  callerId: String,
  receiverId: String,
  status: { type: String, enum: ['initiated', 'ringing', 'accepted', 'rejected', 'missed'], default: 'initiated' },
  createdAt: { type: Date, default: Date.now }
});
const CallRequest = mongoose.model('CallRequest', CallRequestSchema);
const User = mongoose.model('User', UserSchema);

const SessionSchema = new mongoose.Schema({
  sessionId: { type: String, unique: true },

  // Phase 0: Core Billing Fields
  clientId: String,
  astrologerId: String,
  clientConnectedAt: Number, // Timestamp
  astrologerConnectedAt: Number, // Timestamp
  actualBillingStart: Number, // Timestamp
  sessionEndAt: Number, // Timestamp
  status: { type: String, enum: ['active', 'ended'], default: 'active' },

  // Legacy/Compatibility Fields
  fromUserId: String,
  toUserId: String,
  type: String,
  duration: Number,
  totalEarned: Number, // Phase 16: Track session earnings (Astro share)
  totalDeducted: Number // Track total client charge
});
const Session = mongoose.model('Session', SessionSchema);

const PairMonthSchema = new mongoose.Schema({
  pairId: { type: String, required: true, index: true }, // client_id + "_" + astrologer_id
  clientId: String,
  astrologerId: String,
  yearMonth: { type: String, required: true }, // "YYYY-MM"
  currentSlab: { type: Number, default: 0 },
  slabLockedAt: { type: Number, default: 0 }, // seconds
  resetAt: Date
});
// Compound index for unique pair in a month
PairMonthSchema.index({ pairId: 1, yearMonth: 1 }, { unique: true });
const PairMonth = mongoose.model('PairMonth', PairMonthSchema);

const BillingLedgerSchema = new mongoose.Schema({
  billingId: { type: String, unique: true },
  sessionId: { type: String, required: true, index: true },
  minuteIndex: { type: Number, required: true },
  chargedToClient: Number,
  creditedToAstrologer: Number,
  adminAmount: Number,
  reason: {
    type: String,
    enum: ['first_60', 'first_60_partial', 'slab', 'rounded', 'payout_withdrawal', 'referral', 'bonus',
      'slab_1', 'slab_2', 'slab_3', 'slab_4', 'slab_5', 'slab_6', 'slab_7', 'slab_8', 'slab_9', 'slab_10',
      'slab_11', 'slab_12', 'slab_13', 'slab_14', 'slab_15', 'slab_16', 'slab_17', 'slab_18', 'slab_19', 'slab_20']
  },
  createdAt: { type: Date, default: Date.now }
});
const BillingLedger = mongoose.model('BillingLedger', BillingLedgerSchema);

// Phase 15: Withdrawal Schema
const WithdrawalSchema = new mongoose.Schema({
  astroId: String,
  amount: Number,
  type: { type: String, enum: ['payout', 'referral'], default: 'payout' },
  status: { type: String, enum: ['pending', 'approved', 'rejected'], default: 'pending' },
  requestedAt: { type: Date, default: Date.now },
  processedAt: Date
});
const Withdrawal = mongoose.model('Withdrawal', WithdrawalSchema);

const PaymentSchema = new mongoose.Schema({
  transactionId: { type: String, unique: true },
  merchantTransactionId: String, // For PhonePe callback matching
  userId: String,
  amount: Number, // in Rupees
  status: { type: String, enum: ['pending', 'success', 'failed'], default: 'pending' },
  createdAt: { type: Date, default: Date.now },
  providerRefId: String,
  isApp: { type: Boolean, default: false }
});
const Payment = mongoose.model('Payment', PaymentSchema);


const ChatMessageSchema = new mongoose.Schema({
  messageId: { type: String, unique: true },
  sessionId: String,
  fromUserId: String,
  toUserId: String,
  text: String,
  type: { type: String, default: 'text' }, // text, system
  timestamp: { type: Number, default: Date.now },
  createdAt: { type: Date, default: Date.now }
});
const ChatMessage = mongoose.model('ChatMessage', ChatMessageSchema);

const AcademyVideoSchema = new mongoose.Schema({
  title: String,
  youtubeUrl: String,
  thumbnail: String,
  category: String,
  createdAt: { type: Date, default: Date.now }
});
const AcademyVideo = mongoose.model('AcademyVideo', AcademyVideoSchema);

const BannerSchema = new mongoose.Schema({
  imageUrl: { type: String, required: true },
  title: String,
  subtitle: String,
  ctaText: { type: String, default: 'Learn More' },
  order: { type: Number, default: 0 },
  isActive: { type: Boolean, default: true },
  createdAt: { type: Date, default: Date.now }
});
const Banner = mongoose.model('Banner', BannerSchema);

// Account Deletion Request Schema
const AccountDeletionRequestSchema = new mongoose.Schema({
  requestId: { type: String, unique: true },
  userIdentifier: { type: String, required: true }, // Email or Phone
  userId: String, // If found in database
  reason: String,
  status: { type: String, default: 'pending' }, // pending, approved, rejected, completed
  requestedAt: { type: Date, default: Date.now },
  processedAt: Date,
  processedBy: String, // Admin userId who processed it
  notes: String // Admin notes
});
const AccountDeletionRequest = mongoose.model('AccountDeletionRequest', AccountDeletionRequestSchema);
// Astrologer Application Schema
const AstrologerApplicationSchema = new mongoose.Schema({
  applicationId: { type: String, unique: true },
  realName: { type: String, required: true },
  displayName: String,
  gender: String,
  dob: String,
  tob: String,
  pob: String,
  cellNumber1: { type: String, required: true },
  cellNumber2: String,
  whatsAppNumber: String,
  email: String,
  address: String,
  aadharNumber: String,
  panNumber: String,
  astrologyExperience: String,
  profession: String,
  bankDetails: String,
  upiName: String,
  upiNumber: String,
  status: { type: String, enum: ['pending', 'approved', 'rejected'], default: 'pending' },
  appliedAt: { type: Date, default: Date.now },
  processedAt: Date,
  processedBy: String, // Super Admin userId
  notes: String
});
const AstrologerApplication = mongoose.model('AstrologerApplication', AstrologerApplicationSchema);

const NotificationSchema = new mongoose.Schema({
  userId: String,
  type: { type: String, default: 'system' }, // 'missed_call', 'status', 'system'
  title: String,
  message: String,
  details: Object,
  read: { type: Boolean, default: false },
  createdAt: { type: Date, default: Date.now }
});
const Notification = mongoose.model('Notification', NotificationSchema);


// ===== Seed Data =====
async function seedDatabase() {
  const count = await User.countDocuments();
  if (count > 0) return; // Already seeded

  console.log('--- Seeding Database ---');

  const create = async (name, phone, role) => {
    const userId = crypto.randomUUID();
    await User.create({
      userId, name, phone, role,
      skills: role === 'astrologer' ? ['Vedic', 'Prashana'] : [],
      price: 20,
      walletBalance: 108
    });
  };

  await create('Astro Maveeran', '9000000001', 'astrologer');
  await create('Thiru', '9000000002', 'astrologer');
  await create('Lakshmi', '9000000003', 'astrologer');
  await create('Client John', '8000000001', 'client');
  await create('Client Sarah', '8000000002', 'client');
  await create('Client Mike', '8000000003', 'client');

  console.log('--- Database Seeded ---');
}
// seedDatabase(); // Moved to DB connection success

// In-Memory cache for socket mapping (Ephemeral)
const userSockets = new Map(); // userId -> socketId
const socketToUser = new Map(); // socketId -> userId
const userActiveSession = new Map(); // userId -> sessionId
const activeSessions = new Map(); // sessionId -> { type, users... }
const pendingMessages = new Map();
const otpStore = new Map();

// Astrologer Status Persistence (5-min grace period)
const offlineTimeouts = new Map(); // userId -> timeoutId
const savedAstroStatus = new Map(); // userId -> { chat, audio, video, timestamp }
const OFFLINE_GRACE_PERIOD = 5 * 60 * 1000; // 5 minutes

// Session Disconnect Persistence (1-min grace period for calls)
const sessionDisconnectTimeouts = new Map(); // userId -> timeoutId
const SESSION_GRACE_PERIOD = 60 * 1000; // 60 seconds

// --- Static Files & Root Route ---
app.use(express.static(path.join(__dirname, 'public')));
app.get('/', (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

// Store OTPs in memory { phone: { otp, expires } }
// const otpStore = new Map(); // This was already declared above, moving it here for context with the new code.

// ===== Daily Horoscope Logic =====
let dailyHoroscope = { date: '', content: '' };

function generateTamilHoroscope() {
  const now = new Date();
  const dateStr = now.toDateString();

  if (dailyHoroscope.date === dateStr) return dailyHoroscope.content;

  // Tamil Templates (Grammatically Correct Parts)
  // Spoken Tamil Daily Predictions (One Sentence Rule)
  const predictions = [
    "இன்னிக்கு வேலைல கொஞ்சம் கவனமா இருங்க, சின்ன தப்பு கூட பெருசா ஆகலாம்.",
    "பண வரவு நல்லா இருக்கும், ஆனா செலவும் அதுக்கு ஏத்த மாதிரி வரும்.",
    "குடும்பத்துல சின்ன சின்ன சண்டை வரலாம், நீங்க கொஞ்சம் விட்டுக்கொடுங்க.",
    "உடம்புல சின்ன சோர்வு இருக்கும், சரியான நேரத்துக்கு சாப்பிடுங்க.",
    "புதுசா எதுவும் முயற்சி பண்ண வேண்டாம், இருக்கறத சரியா பாத்துக்கோங்க.",
    "நண்பர்கள் மூலமா நல்ல செய்தி வரும், சந்தோஷமா இருப்பீங்க.",
    "இன்னிக்கு உங்களுக்கு யோகமான நாள், நினைச்சது நடக்கும்.",
    "வெளியிடங்களுக்கு போகும்போது வண்டியை மெதுவா ஓட்டுங்க.",
    "வேலை தேடுறவங்களுக்கு இன்னிக்கு நல்ல பதில் கிடைக்கும்.",
    "யார் கிட்டயும் கடன் வாங்க வேண்டாம், கொடுக்கவும் வேண்டாம்.",
    "கோபத்தை குறைச்சுகிட்டா இன்னிக்கு எல்லாமே நல்லபடியா நடக்கும்.",
    "பிள்ளைகள் விஷயத்துல கொஞ்சம் அக்கறை காட்டுங்க.",
    "தொழில்ல எதிர்பார்த்த லாபம் கிடைக்கும், புது ஆர்டர் வரும்.",
    "வாய் வார்த்தைல கவனம் தேவை, தேவையில்லாம பேச வேண்டாம்.",
    "இன்னிக்கு நாள் முழுக்க சுறுசுறுப்பா இருப்பீங்க."
  ];

  // Pick one based on date (Deterministic per day)
  const dayIndex = now.getDate() % predictions.length;
  dailyHoroscope = {
    date: dateStr,
    content: predictions[dayIndex]
  };

  return dailyHoroscope.content;
}

// Init on start
generateTamilHoroscope();

// --- Endpoints ---

// === Astrologer Registration (New & Upgrade) ===
app.post('/api/astrologer/register', async (req, res) => {
  try {
    const { name, phone, experience, about, bankDetails, skills } = req.body;
    if (!phone) return res.status(400).json({ ok: false, error: 'Phone number required' });

    let user = await User.findOne({ phone });
    let isUpgrade = false;

    if (user) {
      if (user.role === 'astrologer' || user.role === 'superadmin') {
        return res.status(400).json({ ok: false, error: 'This number is already registered as an Astrologer or Admin' });
      }
      if (user.astrologerRequestStatus === 'pending') {
        return res.status(400).json({ ok: false, error: 'Your registration request is already pending approval' });
      }

      // Existing Client - Start Upgrade Process
      isUpgrade = true;
      user.name = name || user.name;
      user.astrologerExperience = experience || '';
      user.astrologerAbout = about || '';
      user.bankDetails = bankDetails || {};
      user.astrologerSkills = skills || [];
      user.astrologerRequestStatus = 'pending';
      user.astrologerRequestedAt = new Date();
      await user.save();
    } else {
      // New User - Create as Client with Pending Astrologer Status
      user = await User.create({
        userId: crypto.randomUUID(),
        phone,
        name: name || 'Astro Applicant',
        role: 'client', // Starts as client till approved
        walletBalance: 0,
        astrologerRequestStatus: 'pending',
        astrologerRequestedAt: new Date(),
        astrologerExperience: experience || '',
        astrologerAbout: about || '',
        bankDetails: bankDetails || {},
        astrologerSkills: skills || []
      });
    }

    console.log(`[Astrologer ${isUpgrade ? 'Upgrade' : 'New'}] ${user.name} (${user.phone}) submitted request`);
    logActivity('astrologer', isUpgrade ? 'Astrologer Upgrade Request' : 'New Astrologer Request', { userId: user.userId, name: user.name, phone: user.phone });

    // Notify Super Admins
    const notificationText = isUpgrade
      ? `⭐ New Astrologer Request (Upgrade): ${user.name} (${user.phone})`
      : `⭐ New Astrologer Request: ${user.name} (${user.phone})`;

    io.to('superadmin').emit('admin-notification', {
      type: 'astrologer_request',
      text: notificationText,
      data: { userId: user.userId, name: user.name }
    });

    res.json({ ok: true, message: 'Request submitted successfully. Waiting for admin approval.' });

  } catch (err) {
    console.error('[Astrologer Register] Error:', err.message);
    res.status(500).json({ ok: false, error: 'Server Error: ' + err.message });
  }
});

// Admin: Get Pending Astrologer Requests
app.get('/api/admin/astrologer-requests', async (req, res) => {
  try {
    const requests = await AstrologerApplication.find({ status: 'pending' })
      .sort({ appliedAt: -1 })
      .lean();

    res.json({ ok: true, requests });
  } catch (err) {
    console.error('[Admin] Astrologer requests error:', err.message);
    res.status(500).json({ ok: false, error: 'Server Error' });
  }
});

// --- Get User Profile (Wallet Balance) ---
app.get('/api/user/:userId', async (req, res) => {
  const { userId } = req.params;
  console.log(`[Refer-Debug] User profile requested: ${userId}`);
  try {
    const user = await User.findOne({ userId });
    if (!user) {
      logActivity('auth', 'Profile Not Found', { userId });
      return res.status(404).json({ ok: false, error: 'User not found' });
    }

    // Ensure referral code exists
    if (!user.referralCode) {
      user.referralCode = await generateReferralCode(user.name || 'User');
      await user.save();
    }

    const responseData = {
      ok: true,
      userId: user.userId || '',
      name: user.name || 'User',
      phone: user.phone || '',
      role: user.role || 'client',
      walletBalance: Number(user.walletBalance || 0),
      referralCode: user.referralCode || '',
      isOnline: Boolean(user.isOnline),
      isAvailable: Boolean(user.isAvailable),
      isChatOnline: Boolean(user.isChatOnline),
      isAudioOnline: Boolean(user.isAudioOnline),
      isVideoOnline: Boolean(user.isVideoOnline),
      isBusy: Boolean(user.isBusy),
      totalEarnings: Number(user.totalEarnings || 0),
      image: user.image || ''
    };

    res.json(responseData);
  } catch (err) {
    console.error(`[Refer-Debug] Profile Error: ${err.message}`);
    res.status(500).json({ ok: false, error: 'Internal Error' });
  }
});

// --- Get Referral Stats Dashboard Data ---
app.get('/api/referral/stats/:userId', async (req, res) => {
  const { userId } = req.params;
  console.log(`[Refer-Debug] Referral stats requested: ${userId}`);
  try {
    if (!userId || userId === 'undefined') {
      return res.status(400).json({ ok: false, error: 'Invalid User ID' });
    }

    // Fetch referral levels
    const l1 = await User.find({ referredBy: userId }).select('userId name createdAt').lean();
    const l1Ids = l1.map(u => u.userId);
    const l2 = await User.find({ referredBy: { $in: l1Ids } }).select('userId name createdAt').lean();
    const l2Ids = l2.map(u => u.userId);
    const l3 = await User.find({ referredBy: { $in: l2Ids } }).select('userId name createdAt').lean();

    const user = await User.findOne({ userId }).select('referralEarnings referralWithdrawn referralCode');
    const earnings = user ? (user.referralEarnings || 0) : 0;
    const withdrawn = user ? (user.referralWithdrawn || 0) : 0;

    // Clean data for Android (Remove _id, format dates)
    const cleanList = (list) => list.map(u => ({
      userId: u.userId,
      name: u.name || 'User',
      date: u.createdAt ? new Date(u.createdAt).toDateString() : ''
    }));

    const responseData = {
      ok: true,
      referralCode: user?.referralCode || '',
      stats: {
        level1Count: l1.length,
        level2Count: l2.length,
        level3Count: l3.length,
        totalReferrals: l1.length + l2.length + l3.length,
        referralEarnings: Math.floor(earnings),
        withdrawableAmount: Math.floor(earnings - withdrawn),
        earnings: Math.floor(earnings) // Double key for compatibility
      },
      referrals: {
        l1: cleanList(l1),
        l2: cleanList(l2),
        l3: cleanList(l3)
      }
    };

    console.log(`[Refer-Debug] Sending Response for ${userId} (L1: ${l1.length} users)`);
    res.json(responseData);

  } catch (err) {
    console.error(`[Refer-Debug] Stats Error for ${userId}: ${err.message}`);
    res.status(500).json({ ok: false, error: err.message });
  }
});

// --- Referral Withdrawal Request API ---
app.post('/api/withdraw-referral', async (req, res) => {
  logActivity('referral', 'Withdrawal request started', req.body);
  try {
    const { userId, amount } = req.body;
    if (!userId || !amount || amount < 1000) {
      logActivity('referral', 'Withdrawal validation failed', { userId, amount });
      return res.json({ ok: false, error: 'Minimum withdrawal is ₹1000' });
    }

    const user = await User.findOne({ userId });
    if (!user) {
      logActivity('referral', 'User not found', { userId });
      return res.json({ ok: false, error: 'User not found' });
    }

    const available = (user.referralEarnings || 0) - (user.referralWithdrawn || 0);
    if (amount > available) {
      return res.json({ ok: false, error: 'Insufficient referral balance' });
    }

    if (user.walletBalance < amount) {
      return res.json({ ok: false, error: 'Insufficient wallet balance' });
    }

    // Create withdrawal record
    const withdrawalId = crypto.randomUUID();
    await Withdrawal.create({
      withdrawalId,
      astroId: userId, // Reusing field for userId
      amount,
      type: 'referral',
      status: 'pending'
    });

    // Deduct and update
    user.walletBalance -= amount;
    user.referralWithdrawn = (user.referralWithdrawn || 0) + amount;
    await user.save();

    // Create a ledger entry for withdrawal
    await BillingLedger.create({
      billingId: crypto.randomUUID(),
      sessionId: 'referral_withdrawal_' + userId,
      minuteIndex: 0,
      chargedToClient: amount,
      creditedToAstrologer: 0,
      adminAmount: amount,
      reason: 'payout_withdrawal'
    });

    // Notify user via socket
    const socketId = userSockets.get(userId);
    if (socketId) {
      io.to(socketId).emit('wallet-update', { balance: user.walletBalance });
      io.to(socketId).emit('notification', {
        title: 'Withdrawal Requested',
        text: `₹${amount} referral withdrawal is pending approval.`
      });
    }

    logActivity('referral', 'Withdrawal successful', { userId, amount });
    res.json({ ok: true, message: 'Withdrawal request submitted successfully' });

  } catch (err) {
    logActivity('referral', 'Withdrawal Error', { error: err.message });
    res.status(500).json({ ok: false, error: err.message });
  }
});

// Astrologer List API (Used by Mobile App)
app.get('/api/astrology/astrologers', async (req, res) => {
  try {
    const astrologers = await User.find({ role: 'astrologer' })
      .select('userId name phone skills price isOnline isChatOnline isAudioOnline isVideoOnline experience isVerified image walletBalance totalEarnings')
      .lean();

    // Ensure lists are sorted by Online first (though App also sorts)
    // and map to ensure compatibility
    const formatted = astrologers.map(a => ({
      userId: a.userId,
      name: a.name,
      skills: a.skills || [],
      price: a.price || 15,
      isOnline: a.isOnline || false,
      isChatOnline: a.isChatOnline || false,
      isAudioOnline: a.isAudioOnline || false,
      isVideoOnline: a.isVideoOnline || false,
      experience: a.experience || 0,
      isVerified: a.isVerified || false,
      isBusy: a.isBusy || false,
      image: a.image || '',
      walletBalance: a.walletBalance // Optional
    }));

    res.json({ ok: true, astrologers: formatted });
  } catch (err) {
    console.error('Error fetching astrologers:', err);
    res.status(500).json({ ok: false, error: err.message });
  }
});

// --- Get Astrologer Session History ---
app.get('/api/astrology/history/:userId', async (req, res) => {
  try {
    const { userId } = req.params;
    // Find sessions where this user was the astrologer
    // We check both astrologerId and toUserId for compatibility
    const sessions = await Session.find({
      $or: [
        { astrologerId: userId },
        { toUserId: userId, type: { $in: ['audio', 'video', 'chat'] } }
      ],
      status: 'ended'
    })
      .sort({ actualBillingStart: -1, startTime: -1 })
      .limit(50)
      .lean();

    const populatedSessions = await Promise.all(sessions.map(async (s) => {
      const cId = s.clientId || s.fromUserId;
      const client = await User.findOne({ userId: cId }).select('name').lean();
      return {
        ...s,
        clientName: client ? client.name : 'Unknown Client'
      };
    }));

    res.json({ ok: true, sessions: populatedSessions });
  } catch (err) {
    console.error('History API error:', err);
    res.status(500).json({ ok: false, error: err.message });
  }
});

// --- Register Device (FCM Token) ---
app.post('/register', async (req, res) => {
  try {
    const { userId, fcmToken } = req.body;
    if (!userId || !fcmToken) {
      return res.status(400).json({ success: false, error: 'Missing fields' });
    }

    const user = await User.findOne({ userId });
    if (user) {
      user.fcmToken = fcmToken;
      await user.save();
      console.log(`[FCM] Device registered for ${user.name} (${userId})`);
      res.json({ success: true, message: 'Device registered' });
    } else {
      res.status(404).json({ success: false, error: 'User not found' });
    }
  } catch (error) {
    console.error('Registration Error:', error);
    res.status(500).json({ success: false, error: error.message });
  }
});

// Academy Admin APIs
app.post('/api/admin/academy/videos', async (req, res) => {
  try {
    const video = new AcademyVideo(req.body);
    await video.save();
    res.json({ ok: true, video });
  } catch (err) {
    res.status(500).json({ ok: false, error: err.message });
  }
});

app.put('/api/admin/academy/videos/:id', async (req, res) => {
  try {
    const video = await AcademyVideo.findByIdAndUpdate(req.params.id, req.body, { new: true });
    res.json({ ok: true, video });
  } catch (err) {
    res.status(500).json({ ok: false, error: err.message });
  }
});

app.delete('/api/admin/academy/videos/:id', async (req, res) => {
  try {
    await AcademyVideo.findByIdAndDelete(req.params.id);
    res.json({ ok: true });
  } catch (err) {
    res.status(500).json({ ok: false, error: err.message });
  }
});

// Daily Horoscope API
app.get('/api/daily-horoscope', async (req, res) => {
  try {
    const today = DateTime.now().setZone('Asia/Kolkata').toFormat('yyyy-MM-dd');
    let data = await fetchDailyHoroscope(today);

    if (!data || !Array.isArray(data) || data.length === 0) {
      console.warn('No daily horoscope data available for today, using dummy data.');
      // Generate basic dummy data for 12 rasis
      const dummyData = [
        { sign_en: "Aries", sign_ta: "மேஷம்", prediction_ta: "இன்று நீங்கள் எதிலும் நிதானத்துடன் செயல்பட வேண்டும்.", prediction_en: "Today you should act with patience in everything." },
        { sign_en: "Taurus", sign_ta: "ரிஷபம்", prediction_ta: "தொழில் வியாபாரத்தில் நல்ல லாபம் கிடைக்கும்.", prediction_en: "Good profit in business and trade." },
        { sign_en: "Gemini", sign_ta: "மிதுனம்", prediction_ta: "எதிர்பார்த்த உதவிகள் தக்க சமயத்தில் கிடைக்கும்.", prediction_en: "Expected help will arrive on time." },
        { sign_en: "Cancer", sign_ta: "கடகம்", prediction_ta: "உடல் ஆரோக்கியத்தில் கவனம் தேவை.", prediction_en: "Need to pay attention to health." },
        { sign_en: "Leo", sign_ta: "சிம்மம்", prediction_ta: "நண்பர்கள் மூலம் ஆதாயம் உண்டாகும்.", prediction_en: "Benefits through friends." },
        { sign_en: "Virgo", sign_ta: "கன்னி", prediction_ta: "வேலை சுமை அதிகரிக்கலாம்.", prediction_en: "Workload may increase." },
        { sign_en: "Libra", sign_ta: "துலாம்", prediction_ta: "பண வரவு தாராளமாக இருக்கும்.", prediction_en: "Cash flow will be generous." },
        { sign_en: "Scorpio", sign_ta: "விருச்சிகம்", prediction_ta: "வாழ்க்கை துணையின் ஆதரவு கிடைக்கும்.", prediction_en: "Support from spouse." },
        { sign_en: "Sagittarius", sign_ta: "தனுசு", prediction_ta: "பிள்ளைகள் வழியில் நல்ல செய்தி வரும்.", prediction_en: "Good news through children." },
        { sign_en: "Capricorn", sign_ta: "மகரம்", prediction_ta: "வீண் செலவுகள் ஏற்படும்.", prediction_en: "Unnecessary expenses." },
        { sign_en: "Aquarius", sign_ta: "கும்பம்", prediction_ta: "திறமைக்கு ஏற்ற அங்கீகாரம் கிடைக்கும்.", prediction_en: "Recognition for talent." },
        { sign_en: "Pisces", sign_ta: "மீனம்", prediction_ta: "உடல் சோர்வு நீங்கி புத்துணர்ச்சி பெறுவீர்கள்.", prediction_en: "Fatigue will disappear and you will be refreshed." }
      ].map(d => ({
        ...d, career_ta: "சிறப்பு", career_en: "Good", finance_ta: "நன்று", finance_en: "Good",
        health_ta: "சிறப்பு", health_en: "Good", lucky_number: "6", lucky_color_ta: "வெள்ளை", lucky_color_en: "White"
      }));
      data = dummyData;
    }

    // Logic: If user passed a sign in the app, use it. Otherwise, use index 0 (Mesham).
    const signName = req.query.sign;
    let targetItem = data[0];

    if (signName) {
      const searchStr = signName.toLowerCase();
      const found = data.find(d =>
        (d.sign_en && d.sign_en.toLowerCase() === searchStr) ||
        (d.sign_ta === signName)
      );
      if (found) targetItem = found;
    }

    const rawText = targetItem.prediction_ta || targetItem.forecast_ta || targetItem.prediction_en || "Today is looking promising!";
    const content = truncateTo2Lines(rawText);

    res.json({ ok: true, content: content || 'Today is a good day!' });
  } catch (err) {
    console.error('Error in /api/daily-horoscope:', err);
    try {
      const content = generateTamilHoroscope();
      res.json({ ok: true, content: truncateTo2Lines(content) });
    } catch (innerErr) {
      res.status(500).json({ ok: false, content: "Check back later for your daily insight." });
    }
  }
});

function truncateTo2Lines(text) {
  if (!text) return "";
  const lines = text.split('\n').filter(l => l.trim().length > 0);
  if (lines.length >= 2) return lines.slice(0, 2).join('\n');
  const sentences = text.match(/[^\.!\?]+[\.!\?]+/g) || [text];
  if (sentences.length >= 2) return sentences.slice(0, 2).join(' ');
  return text.length > 150 ? text.substring(0, 147) + "..." : text;
}

// Academy Videos API
app.get('/api/academy/videos', async (req, res) => {
  try {
    let videos = await AcademyVideo.find().sort({ createdAt: -1 });
    if (videos.length === 0) {
      // Return some dummy videos if none exist
      videos = [
        { title: "Introduction to Astrology", youtubeUrl: "https://www.youtube.com/watch?v=kYI9W5yisCc", category: "Basics" },
        { title: "Planetary Positions", youtubeUrl: "https://www.youtube.com/watch?v=FjI1XwHhK_4", category: "Intermediate" },
        { title: "Daily Prediction Guide", youtubeUrl: "https://www.youtube.com/watch?v=BvRE0mD6uA0", category: "General" }
      ];
    }
    res.json({ ok: true, videos });
  } catch (err) {
    res.status(500).json({ ok: false, error: err.message });
  }
});

// --- Banner APIs (Admin & App) ---

// Get Active Banners (Public)
app.get('/api/home/banners', async (req, res) => {
  try {
    const banners = await Banner.find({ isActive: true }).sort({ order: 1 });
    // Fallback if no banners in DB
    if (banners.length === 0) {
      return res.json({
        ok: true,
        data: [
          { id: '1', imageUrl: "https://via.placeholder.com/600x300/1B5E20/FFFFFF?text=Astro+Premium", title: "Premium Consultation", subtitle: "50% Off Today", ctaText: "Book Now" },
          { id: '2', imageUrl: "https://via.placeholder.com/600x300/43A047/FFFFFF?text=Love+Match", title: "Find Your Soulmate", subtitle: "Vedic Compatibility", ctaText: "Check Match" },
          { id: '3', imageUrl: "https://via.placeholder.com/600x300/66BB6A/FFFFFF?text=Career+Growth", title: "Career Guidance", subtitle: "Success Ahead", ctaText: "View Path" }
        ]
      });
    }
    res.json({ ok: true, data: banners });
  } catch (err) {
    res.status(500).json({ ok: false, error: err.message });
  }
});

// Admin: Get All Banners
app.get('/api/admin/banners', async (req, res) => {
  try {
    const banners = await Banner.find().sort({ order: 1 });
    res.json({ ok: true, banners });
  } catch (err) {
    res.status(500).json({ ok: false, error: err.message });
  }
});

// Admin: Create Banner
app.post('/api/admin/banners', async (req, res) => {
  try {
    const banner = new Banner(req.body);
    await banner.save();
    res.json({ ok: true, banner });
  } catch (err) {
    res.status(500).json({ ok: false, error: err.message });
  }
});

// Admin: Update Banner
app.put('/api/admin/banners/:id', async (req, res) => {
  try {
    const banner = await Banner.findByIdAndUpdate(req.params.id, req.body, { new: true });
    res.json({ ok: true, banner });
  } catch (err) {
    res.status(500).json({ ok: false, error: err.message });
  }
});

// Admin: Delete Banner
app.delete('/api/admin/banners/:id', async (req, res) => {
  try {
    await Banner.findByIdAndDelete(req.params.id);
    res.json({ ok: true });
  } catch (err) {
    res.status(500).json({ ok: false, error: err.message });
  }
});

// 12 Rasi Horoscope API
app.get('/api/horoscope/rasi', (req, res) => {
  const raliList = [
    { id: 1, name: "Mesham", name_tamil: "மேஷம்", icon: "aries", prediction: "இன்று நீங்கள் எதிலும் நிதானத்துடன் செயல்பட வேண்டும். குடும்பத்தில் மகிழ்ச்சி நிலவும்." },
    { id: 2, name: "Rishabam", name_tamil: "ரிஷபம்", icon: "taurus", prediction: "தொழில் வியாபாரத்தில் நல்ல லாபம் கிடைக்கும். உறவினர்கள் வருகை இருக்கும்." },
    { id: 3, name: "Mithunam", name_tamil: "மிதுனம்", icon: "gemini", prediction: "எதிர்பார்த்த உதவிகள் தக்க சமயத்தில் கிடைக்கும். சுப காரிய முயற்சிகள் கைகூடும்." },
    { id: 4, name: "Kadagam", name_tamil: "கடகம்", icon: "cancer", prediction: "உடல் ஆரோக்கியத்தில் கவனம் தேவை. பயணங்களில் எச்சரிக்கை அவசியம்." },
    { id: 5, name: "Simmam", name_tamil: "சிம்மம்", icon: "leo", prediction: "நண்பர்கள் மூலம் ஆதாயம் உண்டாகும். நினைத்த காரியம் நிறைவேறும்." },
    { id: 6, name: "Kanni", name_tamil: "கன்னி", icon: "virgo", prediction: "வேலை சுமை அதிகரிக்கலாம். சக ஊழியர்களிடம் அனுசரித்து செல்வது நல்லது." },
    { id: 7, name: "Thulaam", name_tamil: "துலாம்", icon: "libra", prediction: "பண வரவு தாராளமாக இருக்கும். புதிய பொருட்கள் வாங்குவீர்கள்." },
    { id: 8, name: "Viruchigam", name_tamil: "விருச்சிகம்", icon: "scorpio", prediction: "வாழ்க்கை துணையின் ஆதரவு கிடைக்கும். ஆன்மீக நாட்டம் அதிகரிக்கும்." },
    { id: 9, name: "Dhanusu", name_tamil: "தனுசு", icon: "sagittarius", prediction: "பிள்ளைகள் வழியில் நல்ல செய்தி வரும். சமூகத்தில் மதிப்பு உயரும்." },
    { id: 10, name: "Magaram", name_tamil: "மகரம்", icon: "capricorn", prediction: "வீண் செலவுகள் ஏற்படும். ஆடம்பர செலவுகளை குறைப்பது நல்லது." },
    { id: 11, name: "Kumbam", name_tamil: "கும்பம்", icon: "aquarius", prediction: "திறமைக்கு ஏற்ற அங்கீகாரம் கிடைக்கும். மேலதிகாரிகளின் பாராட்டு கிடைக்கும்." },
    { id: 12, name: "Meenam", name_tamil: "மீனம்", icon: "pisces", prediction: "உடல் சோர்வு நீங்கி புத்துணர்ச்சி பெறுவீர்கள். கணவன் மனைவி அன்யோன்யம் கூடும்." }
  ];
  res.json({ ok: true, data: raliList });
});

// ==========================================
// USER INTAKE APIs (Required by Android App)
// ==========================================

// Get user intake details
app.get('/api/user/:userId/intake', async (req, res) => {
  try {
    const { userId } = req.params;
    const user = await User.findOne({ userId });

    if (!user) {
      return res.status(404).json({ success: false, error: 'User not found' });
    }

    res.json({
      success: true,
      data: user.intakeDetails || null
    });
  } catch (err) {
    console.error('Get intake error:', err);
    res.status(500).json({ success: false, error: err.message });
  }
});

// Save user intake details
app.post('/api/user/intake', async (req, res) => {
  try {
    const { userId, ...intakeData } = req.body;

    if (!userId) {
      return res.status(400).json({ success: false, error: 'userId required' });
    }

    const user = await User.findOneAndUpdate(
      { userId },
      { $set: { intakeDetails: intakeData } },
      { new: true }
    );

    if (!user) {
      return res.status(404).json({ success: false, error: 'User not found' });
    }

    res.json({ success: true, data: user.intakeDetails });
  } catch (err) {
    console.error('Save intake error:', err);
    res.status(500).json({ success: false, error: err.message });
  }
});

// ==========================================
// CHAT HISTORY API (Required by Android App)
// ==========================================
app.get('/api/chat/history/:sessionId', async (req, res) => {
  try {
    const { sessionId } = req.params;
    const messages = await ChatMessage.find({ sessionId }).sort({ timestamp: 1 });

    res.json({
      success: true,
      messages: messages.map(m => ({
        messageId: m._id.toString(),
        text: m.text,
        fromUserId: m.fromUserId,
        toUserId: m.toUserId,
        timestamp: m.timestamp
      }))
    });
  } catch (err) {
    console.error('Chat history error:', err);
    res.status(500).json({ success: false, error: err.message });
  }
});

// ==========================================
// LEGACY CHART APIs (Redirect to rasi-eng)
// ==========================================

// Birth chart - proxy to rasi-eng/charts/full
app.post('/api/charts/birth-chart', async (req, res) => {
  try {
    const { DateTime } = require('luxon');
    const { swissEph } = require('./utils/rasiEng/swisseph');
    const { getPlanetsWithDetails, getHouseCusps } = require('./utils/rasiEng/calculations');

    const { date, time, lat, lng, timezone = 5.5, ayanamsa = 'Lahiri' } = req.body;

    const offsetHours = Math.floor(Math.abs(timezone));
    const offsetMinutes = Math.round((Math.abs(timezone) - offsetHours) * 60);
    const sign = timezone >= 0 ? '+' : '-';
    const zone = `UTC${sign}${String(offsetHours).padStart(2, '0')}:${String(offsetMinutes).padStart(2, '0')}`;

    const dt = DateTime.fromFormat(`${date} ${time}`, "yyyy-MM-dd HH:mm", { zone });
    if (!dt.isValid) {
      return res.status(400).json({ error: 'Invalid date or time format' });
    }

    const utc = dt.toUTC();
    const jd = swissEph.julday(utc.year, utc.month, utc.day, utc.hour + utc.minute / 60);

    const houses = getHouseCusps(jd, lat, lng, 'Placidus', ayanamsa);
    const planets = getPlanetsWithDetails(jd, houses.cusps, ayanamsa);

    res.json({ success: true, data: { planets, houses } });
  } catch (err) {
    console.error('Birth chart error:', err);
    res.status(500).json({ success: false, error: err.message });
  }
});

// Match porutham
app.post('/api/match/porutham', async (req, res) => {
  try {
    const { DateTime } = require('luxon');
    const { swissEph } = require('./utils/rasiEng/swisseph');
    const { calculatePorutham } = require('./utils/rasiEng/matchCalculations');

    const {
      groomDate, groomTime, groomLat, groomLng, groomTimezone = 5.5,
      brideDate, brideTime, brideLat, brideLng, brideTimezone = 5.5,
      // Alternative fields for compatibility
      gDate, gTime, gLat, gLng, gTz,
      bDate, bTime, bLat, bLng, bTz,
      // Direct moon longitude input (if already calculated)
      groomMoonLon, brideMoonLon
    } = req.body;

    let gMoonLon, bMoonLon;

    // If moon longitudes are provided directly, use them
    if (groomMoonLon !== undefined && brideMoonLon !== undefined) {
      gMoonLon = groomMoonLon;
      bMoonLon = brideMoonLon;
    } else {
      // Calculate moon positions from birth data
      const gD = groomDate || gDate;
      const gT = groomTime || gTime || '12:00';
      const gLa = groomLat || gLat || 13.08;
      const gLo = groomLng || gLng || 80.27;
      const gZ = groomTimezone || gTz || 5.5;

      const bD = brideDate || bDate;
      const bT = brideTime || bTime || '12:00';
      const bLa = brideLat || bLat || 13.08;
      const bLo = brideLng || bLng || 80.27;
      const bZ = brideTimezone || bTz || 5.5;

      if (!gD || !bD) {
        return res.status(400).json({ success: false, error: 'Both groom and bride birth dates required' });
      }

      // Helper to parse datetime
      const parseDateTime = (date, time, tz) => {
        const offsetHours = Math.floor(Math.abs(tz));
        const offsetMinutes = Math.round((Math.abs(tz) - offsetHours) * 60);
        const sign = tz >= 0 ? '+' : '-';
        const zone = `UTC${sign}${String(offsetHours).padStart(2, '0')}:${String(offsetMinutes).padStart(2, '0')}`;
        return DateTime.fromFormat(`${date} ${time}`, "yyyy-MM-dd HH:mm", { zone });
      };

      const gDt = parseDateTime(gD, gT, gZ);
      const bDt = parseDateTime(bD, bT, bZ);

      if (!gDt.isValid || !bDt.isValid) {
        return res.status(400).json({ success: false, error: 'Invalid date/time format' });
      }

      // Calculate Julian Days
      const gUtc = gDt.toUTC();
      const bUtc = bDt.toUTC();
      const gJd = swissEph.julday(gUtc.year, gUtc.month, gUtc.day, gUtc.hour + gUtc.minute / 60);
      const bJd = swissEph.julday(bUtc.year, bUtc.month, bUtc.day, bUtc.hour + bUtc.minute / 60);

      // Get Moon positions
      const gPlanets = swissEph.getAllPlanets(gJd, 'Lahiri');
      const bPlanets = swissEph.getAllPlanets(bJd, 'Lahiri');

      const gMoon = gPlanets.find(p => p.name === 'Moon');
      const bMoon = bPlanets.find(p => p.name === 'Moon');

      if (!gMoon || !bMoon) {
        return res.status(500).json({ success: false, error: 'Could not calculate Moon positions' });
      }

      gMoonLon = gMoon.longitude;
      bMoonLon = bMoon.longitude;
    }

    const result = calculatePorutham(gMoonLon, bMoonLon);
    res.json({ success: true, data: result });
  } catch (err) {
    console.error('Match porutham error:', err);
    res.status(500).json({ success: false, error: err.message });
  }
});

// OTP Send (Mock)
app.post('/api/send-otp', (req, res) => {
  const { phone } = req.body;
  if (!phone) return res.json({ ok: false, error: 'Phone required' });

  // Generate 4-digit OTP
  const otp = Math.floor(1000 + Math.random() * 9000).toString();

  // Super Admin Bypass (Don't send SMS)
  if (phone === '9876543210') {
    console.log('Super Admin Login Attempt');
    return res.json({ ok: true });
  }

  // Test Astrologer Bypass (OTP: 0101)
  if (phone === '8000000001') {
    console.log('Test Astrologer Login Attempt - OTP: 0101');
    otpStore.set(phone, { otp: '0101', expires: Date.now() + 300000 });
    return res.json({ ok: true });
  }

  // Test Client Bypass (OTP: 0101)
  if (phone === '9000000001') {
    console.log('Test Client Login Attempt - OTP: 0101');
    otpStore.set(phone, { otp: '0101', expires: Date.now() + 300000 });
    return res.json({ ok: true });
  }

  // Send via MSG91 for everyone else
  sendMsg91(phone, otp);

  otpStore.set(phone, { otp, expires: Date.now() + 300000 }); // 5 min
  console.log(`OTP for ${phone}: ${otp}`); // Log for debug
  res.json({ ok: true });
});

// OTP Verify (DB Lookup)
app.post('/api/verify-otp', async (req, res) => {
  const { phone, otp } = req.body;
  logActivity('auth', 'OTP verification attempt', { phone });

  // --- Super Admin Backdoor ---
  if (phone === '9876543210' && otp === '1369') {
    let user = await User.findOne({ phone });
    if (!user) {
      user = await User.create({
        userId: crypto.randomUUID(),
        phone,
        name: 'Super Admin',
        role: 'superadmin',
        walletBalance: 100000
      });
    } else if (user.role !== 'superadmin') {
      user.role = 'superadmin';
      await user.save();
    }

    if (!user.referralCode) {
      user.referralCode = await generateReferralCode(user.name || 'Admin');
      await user.save();
    }
    return res.json({
      ok: true,
      userId: user.userId,
      name: user.name,
      role: user.role,
      phone: user.phone,
      walletBalance: user.walletBalance,
      totalEarnings: user.totalEarnings || 0,
      referralCode: user.referralCode,
      image: user.image
    });
  }

  // --- Test Astrologer Account ---
  if (phone === '8000000001' && otp === '0101') {
    let user = await User.findOne({ phone });
    if (!user) {
      user = await User.create({
        userId: crypto.randomUUID(),
        phone,
        name: 'Test Astrologer',
        role: 'astrologer',
        walletBalance: 5000,
        totalEarnings: 0,
        isOnline: false, // User Request: Default should be offline
        isAvailable: false,
        isChatOnline: false,
        isAudioOnline: false,
        isVideoOnline: false,
        ratePerMinute: 10
      });
    } else if (user.role !== 'astrologer') {
      user.role = 'astrologer';
      // User Request: Never force online magically.
      user.ratePerMinute = user.ratePerMinute || 10;
      await user.save();
    }

    // User Request: Always reset toggles to offline on login
    user.isOnline = false;
    user.isAvailable = false;
    user.isChatOnline = false;
    user.isAudioOnline = false;
    user.isVideoOnline = false;
    user.isBusy = false;
    await user.save();

    if (!user.referralCode) {
      user.referralCode = await generateReferralCode(user.name || 'Astro');
      await user.save();
    }
    return res.json({
      ok: true,
      userId: user.userId,
      name: user.name,
      role: user.role,
      phone: user.phone,
      walletBalance: user.walletBalance,
      totalEarnings: user.totalEarnings || 0,
      referralCode: user.referralCode,
      image: user.image,
      ratePerMinute: user.ratePerMinute
    });
  }

  // --- Test Client Account ---
  if (phone === '9000000001' && otp === '0101') {
    let user = await User.findOne({ phone });
    if (!user) {
      user = await User.create({
        userId: crypto.randomUUID(),
        phone,
        name: 'Test Client',
        role: 'client',
        walletBalance: 1000
      });
    } else if (user.role !== 'client') {
      user.role = 'client';
      await user.save();
    }

    if (!user.referralCode) {
      user.referralCode = await generateReferralCode(user.name || 'Client');
      await user.save();
    }
    return res.json({
      ok: true,
      userId: user.userId,
      name: user.name,
      role: user.role,
      phone: user.phone,
      walletBalance: user.walletBalance,
      totalEarnings: user.totalEarnings || 0,
      referralCode: user.referralCode,
      image: user.image
    });
  }

  // --- Normal User Verification ---
  const entry = otpStore.get(phone);
  if (!entry) return res.json({ ok: false, error: 'No OTP requested' });
  if (Date.now() > entry.expires) return res.json({ ok: false, error: 'Expired' });
  if (entry.otp !== otp) return res.json({ ok: false, error: 'Invalid OTP' });
  otpStore.delete(phone);

  try {
    let user = await User.findOne({ phone });
    logActivity('auth', 'OTP verification successful', { phone, isNewUser: !user });

    // Check Ban
    if (user && user.isBanned) {
      return res.json({ ok: false, error: 'Account Banned by Admin' });
    }

    if (!user) {
      // Create new client
      const userId = crypto.randomUUID();
      const randomSuffix = crypto.randomBytes(2).toString('hex');
      const name = `User_${randomSuffix}`;

      const referralCode = await generateReferralCode(name);

      // Check for incoming referral code
      let referredByUserId = null;
      if (req.body.referralCode) {
        const referrer = await User.findOne({ referralCode: req.body.referralCode.toUpperCase() });
        if (referrer) {
          referredByUserId = referrer.userId;

          // Credit referral bonus to referrer
          referrer.walletBalance += REFERRAL_BONUS_AMOUNT;
          referrer.referralEarnings = (referrer.referralEarnings || 0) + REFERRAL_BONUS_AMOUNT;
          await referrer.save();

          console.log(`[Referral] User ${name} referred by ${referrer.name}. Bonus Rs.${REFERRAL_BONUS_AMOUNT} given.`);

          // Add a ledger entry for the bonus
          await BillingLedger.create({
            billingId: crypto.randomUUID(),
            sessionId: 'referral_bonus_' + userId,
            minuteIndex: 0,
            chargedToClient: 0,
            creditedToAstrologer: 0,
            adminAmount: -REFERRAL_BONUS_AMOUNT,
            reason: 'referral'
          });

          // Notify Referrer in real-time
          const referrerSocket = userSockets.get(referrer.userId);
          if (referrerSocket) {
            io.to(referrerSocket).emit('wallet-update', { balance: referrer.walletBalance });
            io.to(referrerSocket).emit('notification', {
              title: 'Referral Bonus!',
              text: `₹${REFERRAL_BONUS_AMOUNT} credited for referring ${name}!`
            });
          }
        }
      }

      user = await User.create({
        userId,
        phone,
        name,
        role: 'client',
        referralCode,
        referredBy: referredByUserId,
        walletBalance: referredByUserId ? 108 + 21 : 108 // Extra 21 for being referred
      });
    } else {
      // Existing user: ensure they have a referral code
      if (!user.referralCode) {
        user.referralCode = await generateReferralCode(user.name || 'User');
        await user.save();
      }

      // User Request: Astrologer login should default all toggles to OFFLINE
      if (user.role === 'astrologer') {
        user.isOnline = false;
        user.isAvailable = false;
        user.isChatOnline = false;
        user.isAudioOnline = false;
        user.isVideoOnline = false;
        user.isBusy = false;
        await user.save();
        console.log(`[Login] Astrologer ${user.name} logged in - all toggles set to OFFLINE`);
        broadcastAstroUpdate();
      }
    }

    // Ensure role is respected (if changed by admin)
    res.json({
      ok: true,
      userId: user.userId,
      name: user.name,
      role: user.role,
      phone: user.phone,
      walletBalance: user.walletBalance,
      totalEarnings: user.totalEarnings || 0,
      referralCode: user.referralCode,
      image: user.image
    });
  } catch (e) {
    res.status(500).json({ ok: false, error: 'DB Error' });
  }
});

// ===== ACCOUNT DELETION REQUEST API =====
app.post('/api/delete-account-request', async (req, res) => {
  try {
    const { user_identifier, reason } = req.body;

    if (!user_identifier) {
      return res.json({ ok: false, error: 'Email or phone number is required' });
    }

    // Check if user exists in database
    let user = null;
    let userId = null;

    // Try to find by phone
    if (/^\d+$/.test(user_identifier)) {
      user = await User.findOne({ phone: user_identifier });
    } else {
      // Try to find by email (if email field exists in your schema)
      user = await User.findOne({ email: user_identifier });
    }

    if (user) {
      userId = user.userId;
    }

    // Check if there's already a pending request
    const existingRequest = await AccountDeletionRequest.findOne({
      userIdentifier: user_identifier,
      status: 'pending'
    });

    if (existingRequest) {
      return res.json({
        ok: false,
        error: 'A deletion request for this account is already pending'
      });
    }

    // Create deletion request
    const requestId = crypto.randomUUID();
    const deletionRequest = await AccountDeletionRequest.create({
      requestId,
      userIdentifier: user_identifier,
      userId: userId,
      reason: reason || 'No reason provided',
      status: 'pending',
      requestedAt: new Date()
    });

    console.log(`[Account Deletion] Request created: ${requestId} for ${user_identifier}`);

    res.json({
      ok: true,
      message: 'Account deletion request submitted successfully',
      requestId: requestId
    });

  } catch (error) {
    console.error('[Account Deletion] Error:', error);
    res.status(500).json({ ok: false, error: 'Failed to submit deletion request' });
  }
});

// ===== ADMIN: GET ACCOUNT DELETION REQUESTS =====
app.get('/api/admin/deletion-requests', async (req, res) => {
  try {
    const { status } = req.query;

    const query = status ? { status } : {};
    const requests = await AccountDeletionRequest.find(query)
      .sort({ requestedAt: -1 })
      .limit(100);

    res.json({ ok: true, requests });
  } catch (error) {
    console.error('[Admin] Error fetching deletion requests:', error);
    res.status(500).json({ ok: false, error: 'Failed to fetch requests' });
  }
});

// ===== ADMIN: PROCESS ACCOUNT DELETION REQUEST =====
app.post('/api/admin/process-deletion', async (req, res) => {
  try {
    const { requestId, action, adminUserId, notes } = req.body;
    // action: 'approve' or 'reject'

    if (!requestId || !action || !adminUserId) {
      return res.json({ ok: false, error: 'Missing required fields' });
    }

    const request = await AccountDeletionRequest.findOne({ requestId });
    if (!request) {
      return res.json({ ok: false, error: 'Request not found' });
    }

    if (request.status !== 'pending') {
      return res.json({ ok: false, error: 'Request already processed' });
    }

    if (action === 'approve') {
      // Delete user account and related data
      if (request.userId) {
        // Delete user
        await User.deleteOne({ userId: request.userId });

        // Delete related data
        await Session.deleteMany({
          $or: [
            { fromUserId: request.userId },
            { toUserId: request.userId }
          ]
        });
        await ChatMessage.deleteMany({
          $or: [
            { fromUserId: request.userId },
            { toUserId: request.userId }
          ]
        });
        await Payment.deleteMany({ userId: request.userId });
        await BillingLedger.deleteMany({
          $or: [
            { clientId: request.userId },
            { astrologerId: request.userId }
          ]
        });
        await PairMonth.deleteMany({
          $or: [
            { clientId: request.userId },
            { astrologerId: request.userId }
          ]
        });
        await Withdrawal.deleteMany({ astroId: request.userId });

        console.log(`[Account Deletion] User ${request.userId} and related data deleted`);
      }

      request.status = 'completed';
    } else if (action === 'reject') {
      request.status = 'rejected';
    }

    request.processedAt = new Date();
    request.processedBy = adminUserId;
    request.notes = notes || '';
    await request.save();

    res.json({
      ok: true,
      message: `Request ${action === 'approve' ? 'approved and account deleted' : 'rejected'}`
    });

  } catch (error) {
    console.error('[Admin] Error processing deletion:', error);
    res.status(500).json({ ok: false, error: 'Failed to process request' });
  }
});

// ===== NATIVE CALL ACCEPT API =====
// Called from Android when notification Accept/Reject is clicked
// This allows accepting calls WITHOUT WebView being loaded
app.post('/api/native/accept-call', async (req, res) => {
  try {
    const { sessionId, userId, accept, callType } = req.body;

    console.log(`[Native API] Accept Call - Session: ${sessionId}, User: ${userId}, Accept: ${accept}`);

    if (!sessionId || !userId) {
      return res.json({ ok: false, error: 'Missing sessionId or userId' });
    }

    // Find the session
    let session = activeSessions.get(sessionId);
    let fromUserId = null;
    let sessionType = callType || 'audio';

    if (session) {
      // Session found in memory
      fromUserId = session.users.find(u => u !== userId);
      sessionType = session.type || callType || 'audio';
    } else {
      // Try DB
      const dbSession = await Session.findOne({ sessionId });
      if (dbSession) {
        fromUserId = dbSession.fromUserId;
        sessionType = dbSession.type || callType || 'audio';
      }
    }

    if (!fromUserId) {
      console.log(`[Native API] Session not found: ${sessionId}`);
      return res.json({ ok: false, error: 'Session not found or expired' });
    }

    const callerSocketId = userSockets.get(fromUserId);

    if (accept) {
      // Accept the call - notify caller via socket
      if (callerSocketId) {
        io.to(callerSocketId).emit('session-answered', {
          sessionId,
          fromUserId: userId,
          type: sessionType,
          accept: true
        });
        console.log(`[Native API] ✅ Call ACCEPTED - Notified caller: ${fromUserId}`);
      } else {
        console.log(`[Native API] Caller not connected: ${fromUserId}`);
      }

      return res.json({
        ok: true,
        fromUserId,
        callType: sessionType,
        message: 'Call accepted successfully'
      });

    } else {
      // Reject the call
      if (callerSocketId) {
        io.to(callerSocketId).emit('session-answered', {
          sessionId,
          fromUserId: userId,
          accept: false
        });
        console.log(`[Native API] ❌ Call REJECTED - Notified caller: ${fromUserId}`);
      }

      // End the session
      endSessionRecord(sessionId);

      return res.json({ ok: true, message: 'Call rejected' });
    }

  } catch (err) {
    console.error('[Native API] Error:', err);
    res.status(500).json({ ok: false, error: 'Server error' });
  }
});

function startSessionRecord(sessionId, type, u1, u2) {
  activeSessions.set(sessionId, {
    type,
    users: [u1, u2],
    startedAt: Date.now(),
  });
  userActiveSession.set(u1, sessionId);
  userActiveSession.set(u2, sessionId);

  // Mark astrologer as busy
  User.updateMany({ userId: { $in: [u1, u2] }, role: 'astrologer' }, { isBusy: true })
    .then(() => broadcastAstroUpdate())
    .catch(e => console.error('Error marking busy:', e));
}


function getOtherUserIdFromSession(sessionId, userId) {
  const s = activeSessions.get(sessionId);
  if (!s) return null;
  const [u1, u2] = s.users;
  return u1 === userId ? u2 : u2 === userId ? u1 : null;
}

// Helper: End Session & Calculate Wallet
async function endSessionRecord(sessionId) {
  const s = activeSessions.get(sessionId);
  if (!s) return;

  const endTime = Date.now();
  // Use tracked billable seconds, but fallback to wall-clock duration if 0 (for display/history)
  const wallClockSeconds = Math.floor((endTime - (s.startTime || endTime)) / 1000);
  const billableSeconds = s.elapsedBillableSeconds || 0;

  // For DB recording and user summary, if billable is 0 but they were actually in a session, use wall clock
  const displayDuration = billableSeconds > 0 ? billableSeconds : wallClockSeconds;

  // Update Session in DB
  await Session.updateOne({ sessionId }, {
    endTime,
    duration: displayDuration * 1000,
    totalEarned: s.totalEarned || 0,
    totalDeducted: s.totalDeducted || 0,
    status: 'ended'
  });

  // Update PairMonth Cumulative Seconds (Phase 4)
  if (s.pairMonthId) {
    await PairMonth.updateOne(
      { _id: s.pairMonthId },
      { $inc: { slabLockedAt: billableSeconds } }
    );
  }

  // Phase 3: Early Exit Handling (< 60s)
  if (billableSeconds > 0 && billableSeconds < 60) {
    console.log(`Session ${sessionId}: Early exit at ${billableSeconds}s. Charging full 1st minute to Admin.`);
    await processBillingCharge(sessionId, 60, 1, 'first_60_full');
  }
  // Phase 5: Round-Up Billing (Partial Minute at End)
  else if (billableSeconds >= 60) {
    const lastBilled = s.lastBilledMinute || 1;
    const totalMinutes = Math.ceil(billableSeconds / 60);

    if (totalMinutes > lastBilled) {
      console.log(`Session ${sessionId}: Finalizing billing for minutes ${lastBilled + 1} to ${totalMinutes}`);

      for (let i = lastBilled + 1; i <= totalMinutes; i++) {
        // According to new rules: Intermediate full minutes use Slab.
        // The last fraction minute uses 100% Admin rule.
        if (i === totalMinutes && (billableSeconds % 60) !== 0) {
          await processBillingCharge(sessionId, 60, i, 'fraction');
        } else {
          await processBillingCharge(sessionId, 60, i, 'slab');
        }
      }
    }
  }

  // Cleanup active session finally
  activeSessions.delete(sessionId);
  if (s.users) {
    s.users.forEach((u) => {
      if (userActiveSession.get(u) === sessionId) {
        userActiveSession.delete(u);
      }
      // NEW: Clear any pending session disconnect timeouts for these users
      if (sessionDisconnectTimeouts.has(u)) {
        clearTimeout(sessionDisconnectTimeouts.get(u));
        sessionDisconnectTimeouts.delete(u);
      }
    });
  }

  // Notify with Summary
  const payload = {
    reason: 'ended',
    summary: {
      deducted: s.totalDeducted || 0,
      earned: s.totalEarned || 0,
      duration: displayDuration
    }
  };

  if (s.clientId) io.to(s.clientId).emit('session-ended', payload);
  if (s.astrologerId) io.to(s.astrologerId).emit('session-ended', payload);

  // User Request: Clear FCM notification on call end
  // Send CALL_ENDED message to both parties so they clear their notification trays
  s.users.forEach(async (uid) => {
    try {
      const u = await User.findOne({ userId: uid });
      if (u && u.fcmToken) {
        sendFcmV1Push(u.fcmToken, { type: 'CALL_ENDED', sessionId }, null);
      }
    } catch (e) {
      console.error(`Error sending CALL_ENDED FCM to ${uid}`, e);
    }
  });

  // Mark astrologer as NOT busy (Wait for DB update before broadcast)
  User.updateMany({ userId: { $in: s.users }, role: 'astrologer' }, { isBusy: false })
    .then(() => broadcastAstroUpdate())
    .catch(e => console.error('Error clearing busy:', e));
}

// --- Phase 3: Billing Helper ---
const SLAB_RATES = {
  1: 0.30, // 30% to Astro
  2: 0.35, // 35%
  3: 0.40, // 40%
  4: 0.50  // 50%
};

async function processBillingCharge(sessionId, durationSeconds, minuteIndex, type) {
  try {
    const session = await Session.findOne({ sessionId });
    if (!session) return;

    // Fetch Astrologer Price
    const astro = await User.findOne({ userId: session.astrologerId });
    if (!astro) return;

    const client = await User.findOne({ userId: session.clientId });
    if (!client) return;

    // Phase: Pricing Logic
    // Priority: Astro DB Price > Hardcoded fallback
    let pricePerMin = 10;
    if (astro.price && astro.price > 0) {
      pricePerMin = parseInt(astro.price);
    } else {
      // Fallback defaults
      if (session.type === 'audio') pricePerMin = 15;
      if (session.type === 'video') pricePerMin = 20;
    }

    console.log(`[Billing] Session ${sessionId} | Type: ${session.type} | Price: ${pricePerMin}/min | Minute: ${minuteIndex}`);

    let amountToCharge = 0;
    let adminShare = 0;
    let astroShare = 0;
    let reason = '';

    // Logic: First 60 Seconds (Admin Only)
    if (type === 'first_60_full') {
      amountToCharge = pricePerMin;
      adminShare = amountToCharge;
      astroShare = 0;
      reason = 'first_60';
    } else if (type === 'early_exit') {
      amountToCharge = (pricePerMin / 60) * durationSeconds;
      adminShare = amountToCharge; // 100% to Admin
      astroShare = 0;
      reason = 'first_60_partial';
    } else if (type === 'slab') {
      // Standard Minute Billing
      const activeSess = activeSessions.get(sessionId);
      const currentSlab = activeSess?.currentSlab || 3;
      const rate = SLAB_RATES[currentSlab] || 0.30;

      amountToCharge = pricePerMin;
      astroShare = amountToCharge * rate;
      adminShare = amountToCharge - astroShare;
      reason = `slab_${currentSlab}`;

      console.log(`[Billing] Slab: ${currentSlab} | Rate: ${rate} | AstroShare: ${astroShare}`);
    } else if (type === 'fraction') {
      // Last partial minute (100% to Admin)
      amountToCharge = pricePerMin;
      adminShare = amountToCharge;
      astroShare = 0;
      reason = 'fraction_admin';
    } else {
      return;
    }

    // Deduct from Client
    if (client.walletBalance >= amountToCharge) {
      client.walletBalance -= amountToCharge;
      await client.save();

      // Credit Astrologer (if > 0)
      if (astroShare > 0) {
        astro.walletBalance += astroShare;
        astro.totalEarnings = (astro.totalEarnings || 0) + astroShare; // Phase 16
        await astro.save();
      }

      // --- Multi-Level Referral Logic ---
      if (client.referredBy && client.hasRecharged) {
        // 1. Client Cashback (2%)
        const clientCashback = amountToCharge * CASHBACK_CLIENT;
        client.walletBalance += clientCashback;
        await client.save();
        adminShare -= clientCashback;

        // 2. Level 1 Referrer (2%)
        const l1 = await User.findOne({ userId: client.referredBy });
        if (l1) {
          const l1Com = amountToCharge * COMMISSION_L1;
          l1.walletBalance += l1Com;
          l1.referralEarnings = (l1.referralEarnings || 0) + l1Com;
          await l1.save();
          adminShare -= l1Com;

          // Notify L1
          const l1Socket = userSockets.get(l1.userId);
          if (l1Socket) io.to(l1Socket).emit('wallet-update', { balance: l1.walletBalance });

          // 3. Level 2 Referrer (2%)
          if (l1.referredBy) {
            const l2 = await User.findOne({ userId: l1.referredBy });
            if (l2) {
              const l2Com = amountToCharge * COMMISSION_L2;
              l2.walletBalance += l2Com;
              l2.referralEarnings = (l2.referralEarnings || 0) + l2Com;
              await l2.save();
              adminShare -= l2Com;

              // Notify L2
              const l2Socket = userSockets.get(l2.userId);
              if (l2Socket) io.to(l2Socket).emit('wallet-update', { balance: l2.walletBalance });

              // 4. Level 3 Referrer (1%)
              if (l2.referredBy) {
                const l3 = await User.findOne({ userId: l2.referredBy });
                if (l3) {
                  const l3Com = amountToCharge * COMMISSION_L3;
                  l3.walletBalance += l3Com;
                  l3.referralEarnings = (l3.referralEarnings || 0) + l3Com;
                  await l3.save();
                  adminShare -= l3Com;

                  // Notify L3
                  const l3Socket = userSockets.get(l3.userId);
                  if (l3Socket) io.to(l3Socket).emit('wallet-update', { balance: l3.walletBalance });
                }
              }
            }
          }
        }
      }

      // Admin Share is just recorded in Ledger, or we could credit a SuperAdmin wallet.
      // Task says: "Deduct from client, credit 0 to astro, rest to Admin"

      // Create Ledger Entry
      await BillingLedger.create({
        billingId: crypto.randomUUID(),
        sessionId,
        minuteIndex,
        chargedToClient: amountToCharge,
        creditedToAstrologer: astroShare,
        adminAmount: adminShare,
        reason
      });

      // Track Session Totals
      const activeSess = activeSessions.get(sessionId);
      if (activeSess) {
        activeSess.totalDeducted = (activeSess.totalDeducted || 0) + amountToCharge;
        activeSess.totalEarned = (activeSess.totalEarned || 0) + astroShare;
      }

      console.log(`Billing: ${reason} | Charge: ${amountToCharge} | Admin: ${adminShare} | Astro: ${astroShare}`);

      // Notify Wallets
      const s1 = userSockets.get(client.userId);
      if (s1) io.to(s1).emit('wallet-update', { balance: client.walletBalance });

      const s2 = userSockets.get(astro.userId);
      if (s2) io.to(s2).emit('wallet-update', {
        balance: astro.walletBalance,
        totalEarnings: astro.totalEarnings || 0
      });

    } else {
      console.log(`Billing Failed: Insufficient funds for ${client.name}`);
      // Handle forced termination
      forceEndSession(sessionId, 'insufficient_funds');
    }

  } catch (e) {
    console.error('Billing Error:', e);
  }
}

function forceEndSession(sessionId, reason) {
  const session = activeSessions.get(sessionId);
  if (!session) return;

  console.log(`Force Ending Session ${sessionId} due to: ${reason}`);

  // Notify Users (With Summary)
  const clientSocketId = userSockets.get(session.clientId);
  const astroSocketId = userSockets.get(session.astrologerId);

  const payload = {
    reason,
    summary: {
      deducted: session.totalDeducted || 0,
      earned: session.totalEarned || 0,
      duration: session.elapsedBillableSeconds || 0
    }
  };

  if (clientSocketId) io.to(clientSocketId).emit('session-ended', payload);
  if (astroSocketId) io.to(astroSocketId).emit('session-ended', payload);

  // Cleanup Server State
  endSessionRecord(sessionId);
}

// ===== City Autocomplete API =====
app.post('/api/city-autocomplete', async (req, res) => {
  try {
    const { query } = req.body;

    if (!query || query.trim().length < 2) {
      return res.json({ ok: true, results: [] });
    }

    // Call Nominatim API to search for cities in India
    const nominatimUrl = `https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(query)},India&format=json&limit=50&countrycodes=in`;

    const response = await fetch(nominatimUrl, {
      headers: { 'User-Agent': 'AstroApp/1.0' }
    });

    if (!response.ok) {
      return res.json({ ok: true, results: [] });
    }

    const data = await response.json();

    if (!data || data.length === 0) {
      return res.json({ ok: true, results: [] });
    }

    // Process and prioritize results
    let results = data.map(item => ({
      name: item.name,
      state: item.address?.state || '',
      country: item.address?.country || 'India',
      latitude: parseFloat(item.lat),
      longitude: parseFloat(item.lon),
      displayName: item.display_name
    }));

    // Prioritize Tamil Nadu cities
    const tamilNaduCities = results.filter(r => r.state === 'Tamil Nadu');
    const otherCities = results.filter(r => r.state !== 'Tamil Nadu');

    results = [...tamilNaduCities, ...otherCities];

    // Remove duplicates
    const seen = new Set();
    results = results.filter(r => {
      const key = `${r.name}-${r.state}`;
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    });

    // Limit to top 10 results
    results = results.slice(0, 10);

    res.json({ ok: true, results });
  } catch (error) {
    console.error('City autocomplete error:', error);
    res.json({ ok: false, error: 'Failed to fetch cities', results: [] });
  }
});

// ===== Get City Timezone =====
app.post('/api/city-timezone', async (req, res) => {
  try {
    const { latitude, longitude } = req.body;

    if (!latitude || !longitude) {
      return res.json({ ok: false, error: 'Latitude and longitude required' });
    }

    // Call GeoNames Timezone API
    const geonamesUrl = `http://api.geonames.org/timezoneJSON?lat=${latitude}&lng=${longitude}&username=demo`;

    const response = await fetch(geonamesUrl);

    if (!response.ok) {
      return res.json({ ok: false, error: 'Failed to fetch timezone' });
    }

    const data = await response.json();

    if (data.status && data.status.value !== 0) {
      return res.json({ ok: false, error: 'Invalid coordinates' });
    }

    res.json({
      ok: true,
      timezone: data.timezoneId,
      gmtOffset: data.gmtOffset,
      dstOffset: data.dstOffset
    });
  } catch (error) {
    console.error('Timezone fetch error:', error);
    res.json({ ok: false, error: 'Failed to fetch timezone' });
  }
});

// --- Presence Helpers ---
async function broadcastAstroUpdate() {
  try {
    // Include ALL fields needed by client apps for real-time status
    const astros = await User.find(
      { role: 'astrologer' },
      'userId name isOnline isChatOnline isAudioOnline isVideoOnline isAvailable isBusy price image skills experience rating isVerified phone'
    ).lean();

    if (io) io.emit('astrologer-update', astros);
  } catch (e) {
    console.error('Error broadcasting astro updates:', e);
  }
}

// --- Phase 2: Session Timer Engine ---
let tickInterval = null;

function getSlabBySeconds(seconds) {
  if (seconds <= 300) return 1;
  if (seconds <= 600) return 2;
  if (seconds <= 900) return 3;
  if (seconds <= 1200) return 4;
  return 4; // Max slab 4+
}

function tickSessions() {
  const now = Date.now();

  // Only log active count every 60 seconds to save CPU/Logs
  if (Math.floor(now / 1000) % 60 === 0 && activeSessions.size > 0) {
    console.log(`[Ticker] Active Sessions: ${activeSessions.size}`);
  }

  for (const [sessionId, session] of activeSessions) {
    // 1. Check if Billing Started
    if (!session.actualBillingStart || now < session.actualBillingStart) continue;

    const clientSocketId = userSockets.get(session.clientId);
    const astroSocketId = userSockets.get(session.astrologerId);

    const isClientConnected = !!clientSocketId;
    const isAstroConnected = !!astroSocketId;

    if (isClientConnected && isAstroConnected) {
      session.elapsedBillableSeconds = (session.elapsedBillableSeconds || 0) + 1;

      // Phase 3: First Minute Check (at 60s exactly)
      if (session.elapsedBillableSeconds === 60) {
        processBillingCharge(sessionId, 60, 1, 'first_60_full');
      }

      // Phase 4: Check Slab Upgrade
      if (session.pairMonthId) {
        const totalSeconds = (session.initialPairSeconds || 0) + session.elapsedBillableSeconds;
        const calculatedSlab = getSlabBySeconds(totalSeconds);
        const effectiveSlab = Math.max(calculatedSlab, session.currentSlab || 0);

        if (effectiveSlab > session.currentSlab) {
          session.currentSlab = effectiveSlab;
          PairMonth.updateOne({ _id: session.pairMonthId }, { currentSlab: effectiveSlab }).exec();
        }
      }

      // Check Minute Boundary (Post-First-Minute)
      if (session.elapsedBillableSeconds > 60) {
        const eligibleSeconds = session.elapsedBillableSeconds - 60;
        const totalShouldBeBilled = 1 + Math.floor(eligibleSeconds / 60);

        if (totalShouldBeBilled > (session.lastBilledMinute || 1)) {
          processBillingCharge(sessionId, 60, totalShouldBeBilled, 'slab');
          session.lastBilledMinute = totalShouldBeBilled;
        }
      }
    }
  }
}

// Start ticker once
if (tickInterval) clearInterval(tickInterval);
tickInterval = setInterval(tickSessions, 1000);

// OTP Cleanup Interval (Every 10 minutes)
setInterval(() => {
  const now = Date.now();
  for (const [phone, data] of otpStore) {
    if (now > data.expires) {
      otpStore.delete(phone);
    }
  }
}, 10 * 60 * 1000);

// ===== Socket.IO =====
io.on('connection', (socket) => {
  logActivity('socket', `New connection: ${socket.id}`);

  // --- Register user ---
  // --- Register user ---
  socket.on('register', (data, cb) => {
    try {
      const { name, phone, existingUserId } = data || {};
      const userId = data.userId || socketToUser.get(socket.id);

      const query = phone ? { phone } : (userId ? { userId } : null);

      if (!query) {
        safeAck(cb, { ok: false, error: 'No identifier provided' });
        return;
      }

      User.findOne(query).then(user => {
        if (!user) {
          safeAck(cb, { ok: false, error: 'User not found' });
          return;
        }

        const userId = user.userId;
        userSockets.set(userId, socket.id);
        socketToUser.set(socket.id, userId);
        socket.join(userId); // JOIN ROOM FOR RELIABLE SIGNALING

        safeAck(cb, {
          ok: true,
          userId: user.userId,
          role: user.role,
          name: user.name,
          walletBalance: user.walletBalance,
          totalEarnings: user.totalEarnings || 0,
          referralCode: user.referralCode,
          hasRecharged: user.hasRecharged || false
        });
        logActivity('socket', 'User registered', { name: user.name, role: user.role, userId: user.userId });

        // Cancel pending SESSION timeout (For ALL users - Client or Astrologer)
        if (sessionDisconnectTimeouts.has(userId)) {
          clearTimeout(sessionDisconnectTimeouts.get(userId));
          sessionDisconnectTimeouts.delete(userId);
          console.log(`[Session] Cancelled disconnect timeout for ${user.name} (reconnected in time!)`);

          // Re-join the user to the socket room?
          // If we depend on socket.id for targeting, we update userSockets above so it should be fine.
          // However, if we used rooms for sessions, we'd need to re-join.
          // Current logic uses userSockets.get(userId) to target, so updating the map is sufficient.
        }

        // If astro, broadcast status
        if (user.role === 'astrologer') {
          // Cancel pending offline timeout (if any - though we will remove the timeout logic)
          if (offlineTimeouts.has(userId)) {
            clearTimeout(offlineTimeouts.get(userId));
            offlineTimeouts.delete(userId);
          }

          // User Request: Do NOT auto-restore online status on socket register.
          // Astrologer must manually toggle online after login.
          broadcastAstroUpdate();
        }
        // If superadmin, join room
        if (user.role === 'superadmin') {
          socket.join('superadmin');
        }

        // NEW: All users join a room with their userId for reliable messaging
        socket.join(userId);
        console.log(`[Socket] ${user.name} joined room: ${userId}`);
      });
    } catch (err) {
      console.error('register error', err);
      safeAck(cb, { ok: false, error: 'Internal error' });
    }
  });

  // --- Rejoin Session (for reconnecting after background/edit) ---
  socket.on('rejoin-session', (data) => {
    try {
      const { sessionId } = data || {};
      const userId = socketToUser.get(socket.id);

      if (sessionId && userId) {
        socket.join(sessionId);
        console.log(`[Socket] User ${userId} rejoined session: ${sessionId}`);

        // Notify the other party that user has reconnected
        socket.to(sessionId).emit('peer-reconnected', { userId });
        logActivity('session', 'User rejoined session', { userId, sessionId });
      }
    } catch (err) {
      console.error('rejoin-session error', err);
    }
  });



  // --- Get Astrologers List ---
  socket.on('get-astrologers', async (cb) => {
    try {
      const astros = await User.find({ role: 'astrologer' });
      // Emit to this socket directly for compatibility
      socket.emit('astrologer-update', astros);
      safeAck(cb, { astrologers: astros });
    } catch (e) {
      safeAck(cb, { astrologers: [] });
    }
  });

  // --- Toggle Status (Astrologer Only) ---
  socket.on('toggle-status', async (data) => {
    const userId = data.userId || socketToUser.get(socket.id);
    if (!userId) return;

    try {
      const update = {};
      if (data.type === 'chat') update.isChatOnline = !!data.online;
      if (data.type === 'audio') update.isAudioOnline = !!data.online;
      if (data.type === 'video') update.isVideoOnline = !!data.online;

      let user = await User.findOne({ userId });
      if (user) {
        // If type is 'online' (generic), don't touch individual services
        // Just ensure astrologer stays with their current service statuses
        if (data.type === 'online') {
          // Don't modify any service status - just update lastSeen
          user.lastSeen = new Date();
          user.isOnline = user.isChatOnline || user.isAudioOnline || user.isVideoOnline;
          user.isAvailable = user.isOnline;
          await user.save();
          broadcastAstroUpdate();
          console.log(`[Presence] ${user.name} post-session refresh (services unchanged)`);
        } else {
          Object.assign(user, update);
          user.isOnline = user.isChatOnline || user.isAudioOnline || user.isVideoOnline;
          user.isAvailable = user.isOnline;
          user.lastSeen = new Date();
          await user.save();
          broadcastAstroUpdate();
          console.log(`[Presence] ${user.name} toggled ${data.type}: ${data.online}`);
        }
      }
    } catch (e) { console.error(e); }
  });

  // --- Update Service Status (Individual Toggles from Android) ---
  socket.on('update-service-status', async (data) => {
    const userId = data.userId || socketToUser.get(socket.id);
    if (!userId) return;

    try {
      const update = {};
      const isEnabled = !!data.isEnabled;

      if (data.service === 'chat') update.isChatOnline = isEnabled;
      if (data.service === 'call') update.isAudioOnline = isEnabled; // 'call' maps to 'audio'
      if (data.service === 'video') update.isVideoOnline = isEnabled;

      let user = await User.findOne({ userId });
      if (user) {
        Object.assign(user, update);
        // Manual Toggle Rule: isAvailable is the master status
        user.isOnline = user.isAvailable;
        user.lastSeen = new Date();
        await user.save();

        broadcastAstroUpdate();
        console.log(`[Service Status] ${user.name} updated ${data.service}: ${isEnabled}`);
      }
    } catch (e) { console.error('update-service-status error:', e); }
  });

  // --- Mobile App Specific Status Update ---
  socket.on('update-status', async (data) => {
    const userId = data.userId || socketToUser.get(socket.id);
    if (!userId) return;

    try {
      const isOnline = !!data.isOnline;
      // Mobile toggle sets ALL statuses
      let user = await User.findOne({ userId });
      if (user) {
        user.isChatOnline = isOnline;
        user.isAudioOnline = isOnline;
        user.isVideoOnline = isOnline;
        user.isOnline = isOnline;
        user.isAvailable = isOnline;
        user.lastSeen = new Date();
        await user.save();
        broadcastAstroUpdate();
        console.log(`[Presence Mobile] ${user.name} updated status: ${isOnline}`);
      }
    } catch (e) { console.error(e); }
  });

  // --- App Lifecycle: Background ---
  socket.on('app-background', async () => {
    const userId = socketToUser.get(socket.id);
    if (!userId) return;

    try {
      const user = await User.findOne({ userId });
      if (user && user.role === 'astrologer') {
        user.lastSeen = new Date();
        // DON'T mark offline - just update lastSeen
        await user.save();
        console.log(`[Presence] ${user.name} went to background (lastSeen updated)`);
      }
    } catch (e) { console.error('[Presence] app-background error:', e); }
  });

  // --- App Lifecycle: Foreground ---
  socket.on('app-foreground', async () => {
    const userId = socketToUser.get(socket.id);
    if (!userId) return;

    try {
      const user = await User.findOne({ userId });
      if (user && user.role === 'astrologer') {
        user.lastSeen = new Date();

        // Restore status from saved state if available
        const saved = savedAstroStatus.get(userId);
        if (saved) {
          user.isChatOnline = saved.chat;
          user.isAudioOnline = saved.audio;
          user.isVideoOnline = saved.video;
          user.isOnline = saved.chat || saved.audio || saved.video;
          user.isAvailable = user.isOnline;
          savedAstroStatus.delete(userId);
          console.log(`[Presence] ${user.name} returned to foreground - status restored`);
        } else {
          console.log(`[Presence] ${user.name} returned to foreground`);
        }

        await user.save();
        broadcastAstroUpdate();
      }
    } catch (e) { console.error('[Presence] app-foreground error:', e); }
  });

  // --- Update Profile ---
  socket.on('update-profile', async (data, cb) => {
    const userId = socketToUser.get(socket.id);
    if (!userId) return cb({ ok: false, error: 'Not logged in' });

    try {
      const user = await User.findOne({ userId });
      if (user) {
        if (data.price) user.price = parseInt(data.price);
        if (data.experience) user.experience = parseInt(data.experience);
        if (data.image) user.image = data.image; // URL
        if (data.birthDetails) {
          user.birthDetails = { ...user.birthDetails, ...data.birthDetails };
        }

        await user.save();

        if (user.role === 'astrologer') broadcastAstroUpdate();
        cb({ ok: true, user });
      } else {
        cb({ ok: false, error: 'User not found' });
      }
    } catch (e) {
      console.error('Update Profile Error', e);
      cb({ ok: false, error: 'Internal Error' });
    }
  });

  // --- Session request (chat / audio / video) ---
  socket.on('request-session', async (data, cb) => {
    logActivity('session', 'New session request received', data);
    try {
      const { toUserId, type, birthData } = data || {};
      const fromUserId = socketToUser.get(socket.id);

      if (!fromUserId) return cb({ ok: false, error: 'Not registered' });
      if (!toUserId || !type) return cb({ ok: false, error: 'Missing fields' });

      // Get target user from DB
      const toUser = await User.findOne({ userId: toUserId });
      const fromUser = await User.findOne({ userId: fromUserId });

      if (!toUser) {
        return cb({ ok: false, error: 'User not found' });
      }

      // Check if astrologer is available (MANUAL ONLY)
      const isAvailable = toUser.isAvailable === true;

      // ALLOW CALL even if offline -> Logic will fall back to FCM below
      // if (!isAvailable) {
      //   return cb({ ok: false, error: 'Astrologer is offline' });
      // }

      if (userActiveSession.has(toUserId)) {
        const existingSessionId = userActiveSession.get(toUserId);
        const existingSession = activeSessions.get(existingSessionId);

        if (!existingSession) {
          // Ghost session cleanup
          console.log(`[Session] Ghost session ${existingSessionId} detected for ${toUserId}. Auto-cleaning.`);
          userActiveSession.delete(toUserId);
        }
        else if (existingSession.users.includes(fromUserId)) {
          // Same caller retrying
          console.log(`[Session] Stale session ${existingSessionId} detected between ${fromUserId} and ${toUserId}. Auto-cleaning.`);
          await endSessionRecord(existingSessionId);
        } else {
          return cb({ ok: false, error: 'User busy' });
        }
      }

      const sessionId = crypto.randomUUID();

      // Resolve roles
      let clientId = null;
      let astrologerId = null;

      if (fromUser && fromUser.role === 'client') clientId = fromUserId;
      if (fromUser && fromUser.role === 'astrologer') astrologerId = fromUserId;
      if (toUser && toUser.role === 'client') clientId = toUserId;
      if (toUser && toUser.role === 'astrologer') astrologerId = toUserId;

      await Session.create({
        sessionId, fromUserId, toUserId, type, startTime: Date.now(),
        clientId, astrologerId
      });

      activeSessions.set(sessionId, {
        type,
        users: [fromUserId, toUserId],
        startedAt: Date.now(),
        clientId,
        astrologerId,
        elapsedBillableSeconds: 0,
        lastBilledMinute: 0,
        actualBillingStart: null,
        totalDeducted: 0,
        totalEarned: 0,
        status: 'ringing'
      });
      userActiveSession.set(fromUserId, sessionId);
      userActiveSession.set(toUserId, sessionId);

      // Try socket notification (might fail if in background - that's OK!)
      let socketSent = false;
      io.to(toUserId).emit('incoming-session', {
        sessionId,
        fromUserId,
        callerName: fromUser?.name || 'Client',  // FIX: Add caller name for display
        type,
        birthData: birthData || null,
        iceServers: ICE_SERVERS // Dynamic TURN servers
      });
      socketSent = true;
      console.log(`[Session] Socket notification sent to room: ${toUserId}`);

      // IMPROVED: Send FCM Push Notification as BACKUP (even if socket sent)
      // This ensures the call reaches the user if socket message is missed/dropped
      // The Android app handles duplicate by showing only one IncomingCallActivity
      if (toUser && toUser.fcmToken) {
        const fcmData = {
          type: 'INCOMING_CALL',
          sessionId: sessionId,
          callType: type,
          callerName: fromUser?.name || 'Client',
          callerId: fromUserId, // Fixed: callerUserId -> callerId
          timestamp: Date.now().toString(),
          birthData: JSON.stringify(birthData || {}),
          iceServers: JSON.stringify(ICE_SERVERS) // Dynamic TURN servers for FCM
        };

        const fcmNotification = {
          title: '📞 Incoming Call',
          body: `${fromUser?.name || 'Someone'} is calling you`
        };

        sendFcmV1Push(toUser.fcmToken, fcmData, fcmNotification)
          .then(result => {
            console.log(`[FCM v1] Session Push to ${toUserId}: Success=${result.success} (socketSent=${socketSent})`);
            if (!result.success && (result.error?.includes('Requested entity was not found') || result.error === 'UNREGISTERED')) {
              // Token is stale/invalid
              User.updateOne({ userId: toUserId }, { $unset: { fcmToken: 1 } })
                .then(() => console.log(`[FCM v1] Invalid token removed for ${toUserId}`))
                .catch(e => console.error('Token removal error', e));
            }
          })
          .catch(err => {
            console.error('[FCM v1] Session Push Error:', err.message);
          });
      }

      console.log(`Session request: ${sessionId} (${type})`);
      cb({ ok: true, sessionId, iceServers: ICE_SERVERS });

      // --- MISSED CALL TIMEOUT (25s) ---
      setTimeout(async () => {
        const s = activeSessions.get(sessionId);
        if (s && s.status === 'ringing') {
          console.log(`[Session] Ringing timeout for ${sessionId}. Marking as MISSED.`);

          // USER REQUEST: DO NOT set astrologer offline on missed call.
          /*
          if (astrologerId) {
            try {
              const astro = await User.findOne({ userId: astrologerId });
              if (astro) {
                astro.isChatOnline = false;
                astro.isAudioOnline = false;
                astro.isVideoOnline = false;
                astro.isOnline = false;
                astro.isAvailable = false;
                await astro.save();

                // Broadcast to all clients
                broadcastAstroUpdate();

                // Notify Super Admin
                io.to('superadmin').emit('admin-notification', {
                  title: 'Call Missed - Astro Stayed Online',
                  message: `Astrologer ${astro.name} missed an incoming call. Status was NOT changed.`,
                  type: 'warning',
                  astroId: astrologerId,
                  sessionId: sessionId
                });

                // Notify the astrologer via socket (if connected)
                io.to(astrologerId).emit('admin-notification', {
                  title: 'Call Missed',
                  message: 'You missed a call. Please check your dashboard.',
                  type: 'warning'
                });
              }
            } catch (e) {
              console.error('[Missed Call] Error handling missed call:', e);
            }
          }
          */

          // Broadcast MISSED update to Super Admin (Status remains unchanged as per User Request)
          if (astrologerId) {
            // Persistent Notification for Super Admin
            Notification.create({
              userId: astrologerId,
              type: 'missed_call',
              title: 'Missed Call Alert',
              message: `Astrologer missed a call from ${fromUser?.name || 'Client'}.`,
              details: { sessionId, fromUserId, toUserId, type }
            }).catch(e => console.error('Notification creation error', e));

            io.to('superadmin').emit('admin-notification', {
              title: 'Call Missed',
              message: `Astrologer missed a call, but remained online (User Request).`,
              type: 'info',
              astroId: astrologerId,
              sessionId: sessionId
            });
          }

          io.to(fromUserId).emit('session-ended', { sessionId, reason: 'no_answer' });
          io.to(toUserId).emit('session-ended', { sessionId, reason: 'missed' });

          userActiveSession.delete(fromUserId);
          userActiveSession.delete(toUserId);
          activeSessions.delete(sessionId);
          await Session.updateOne({ sessionId }, { status: 'missed', endTime: Date.now() }).catch(() => { });
        }
      }, 25000);
    } catch (err) {
      console.error('request-session error', err);
      cb({ ok: false, error: 'Internal error' });
    }
  });

  // --- Save Intake Details ---
  socket.on('save-intake-details', async (data, cb) => {
    const userId = socketToUser.get(socket.id);
    if (!userId) return;
    try {
      // Data contains the full birthData object from frontend
      // We extract what we need for persistent storage
      const u = await User.findOne({ userId });
      if (u) {
        // Update regular birth details
        u.birthDetails = {
          dob: `${data.year}-${String(data.month).padStart(2, '0')}-${String(data.day).padStart(2, '0')}`,
          tob: `${String(data.hour).padStart(2, '0')}:${String(data.minute).padStart(2, '0')}`,
          pob: data.city,
          lat: data.latitude,
          lon: data.longitude
        };
        u.name = data.name; // Update name if changed

        // Update Intake Details
        u.intakeDetails = {
          gender: data.gender,
          marital: data.marital,
          occupation: data.occupation,
          topic: data.topic,
          partner: data.partner
        };
        await u.save();
        if (typeof cb === 'function') cb({ ok: true });

        // --- REAL-TIME UPDATE TO PARTNER ---
        // If user is in a session, send the updated details to the other person (Astrologer) immediately.
        const sessionId = userActiveSession.get(userId);
        if (sessionId) {
          const partnerId = getOtherUserIdFromSession(sessionId, userId);
          if (partnerId) {
            const partnerSocket = userSockets.get(partnerId);
            if (partnerSocket) {
              io.to(partnerSocket).emit('client-birth-chart', {
                sessionId,
                fromUserId: userId,
                birthData: data
              });
            }
          }
        }
      }
    } catch (e) { console.error(e); }
  });

  // --- Answer session ---
  socket.on('answer-session', (data) => {
    try {
      const { sessionId, toUserId, type, accept } = data || {};
      const fromUserId = socketToUser.get(socket.id);
      if (!fromUserId || !sessionId || !toUserId) {
        console.warn(`[Session] answer-session missing data: from=${fromUserId}, session=${sessionId}, to=${toUserId}`);
        return;
      }

      if (!accept) {
        endSessionRecord(sessionId);
      } else {
        const session = activeSessions.get(sessionId);
        if (session) {
          if (session.status === 'active') {
            console.log('[Session] Ignoring duplicate web answer for active session', sessionId);
            return;
          }
          session.status = 'active';
          if (session.astrologerId) {
            User.updateOne({ userId: session.astrologerId }, { isBusy: true }).then(() => {
              broadcastAstroUpdate();
            }).catch(e => console.error('Error setting isBusy on answer-session', e));
          }
        }
      }

      // Emit to Room (userId) - works even after reconnect!
      io.to(toUserId).emit('session-answered', {
        sessionId,
        fromUserId,
        type,
        accept: !!accept,
        iceServers: ICE_SERVERS
      });

      console.log(
        `Session answer: sessionId=${sessionId}, type=${type}, from=${fromUserId}, to=${toUserId}, accept=${!!accept}`
      );
    } catch (err) {
      console.error('answer-session error', err);
    }
  });

  // --- Answer session from Android Native (doesn't have toUserId) ---
  socket.on('answer-session-native', async (data, cb) => {
    try {
      const { sessionId, accept, callType } = data || {};
      const astrologerId = socketToUser.get(socket.id);

      if (!astrologerId || !sessionId) {
        if (typeof cb === 'function') cb({ ok: false, error: 'Invalid data' });
        return;
      }

      // Look up the session to find the caller (client)
      const session = activeSessions.get(sessionId);
      if (!session) {
        // Try to find from DB
        const dbSession = await Session.findOne({ sessionId });
        if (!dbSession) {
          if (typeof cb === 'function') cb({ ok: false, error: 'Session not found' });
          return;
        }

        const fromUserId = dbSession.fromUserId;
        const targetSocketId = userSockets.get(fromUserId);

        if (accept) {
          // Notify caller that call was accepted
          if (astrologerId) {
            User.updateOne({ userId: astrologerId }, { isBusy: true }).then(() => {
              broadcastAstroUpdate();
            }).catch(e => console.error('Error setting isBusy on answer-session-native-db', e));
          }
          io.to(fromUserId).emit('session-answered', {
            sessionId,
            fromUserId: astrologerId,
            type: callType || dbSession.type,
            accept: true
          });

          console.log(`[Native] Call accepted (DB) - Session: ${sessionId}, From: ${fromUserId}, To: ${astrologerId}`);
          if (typeof cb === 'function') cb({ ok: true, fromUserId });
        } else {
          // Call rejected
          io.to(fromUserId).emit('session-answered', {
            sessionId,
            fromUserId: astrologerId,
            type: callType || dbSession.type,
            accept: false
          });
          endSessionRecord(sessionId);
          console.log(`[Native] Call rejected - Session: ${sessionId}`);
          if (typeof cb === 'function') cb({ ok: true });
        }
        return;
      }

      // Session found in memory
      const fromUserId = session.users.find(u => u !== astrologerId);
      const targetSocketId = userSockets.get(fromUserId);

      if (accept) {
        if (session.status === 'active') {
          console.log('[Native] Ignoring duplicate answer for active session', sessionId);
          if (typeof cb === 'function') cb({ ok: true, fromUserId });
          return;
        }
        session.status = 'active';
        if (astrologerId) {
          User.updateOne({ userId: astrologerId }, { isBusy: true }).then(() => {
            broadcastAstroUpdate();
          }).catch(e => console.error('Error setting isBusy on answer-session-native', e));
        }
        // USE userId ROOM instead of socket ID for reliability (fixes web issues)
        io.to(fromUserId).emit('session-answered', {
          sessionId,
          fromUserId: astrologerId,
          type: callType || session.type,
          accept: true
        });
        console.log(`[Native] Call accepted - Session: ${sessionId}, Caller: ${fromUserId}, Astro: ${astrologerId}`);
        if (typeof cb === 'function') cb({ ok: true, fromUserId });
      } else {
        // USE userId ROOM instead of socket ID for reliability
        io.to(fromUserId).emit('session-answered', {
          sessionId,
          fromUserId: astrologerId,
          accept: false
        });
        endSessionRecord(sessionId);
        console.log(`[Native] Call rejected - Session: ${sessionId}`);
        if (typeof cb === 'function') cb({ ok: true });
      }

    } catch (err) {
      console.error('answer-session-native error', err);
      if (typeof cb === 'function') cb({ ok: false, error: 'Server error' });
    }
  });

  // --- WebRTC signaling relay ---
  socket.on('signal', (data) => {
    try {
      const { sessionId, toUserId, signal } = data || {};
      const fromUserId = socketToUser.get(socket.id);
      if (!fromUserId || !sessionId || !toUserId || !signal) {
        console.warn(`[Signal] Missing data: from=${fromUserId}, session=${sessionId}, to=${toUserId}`);
        return;
      }
      logActivity('signal', 'WebRTC Signaling', { from: fromUserId, to: toUserId, sessionId });

      // Emit to Room (userId) - works even after reconnect!
      io.to(toUserId).emit('signal', {
        sessionId,
        fromUserId,
        signal,
      });
    } catch (err) {
      console.error('signal error', err);
    }
  });

  // --- End Session (Sync for both sides) ---
  socket.on('end-session', async (data) => {
    logActivity('session', 'Session end requested', data);
    try {
      const { sessionId } = data || {};
      const fromUserId = socketToUser.get(socket.id);

      if (!fromUserId || !sessionId) return;

      const session = activeSessions.get(sessionId);
      // No need to emit here, endSessionRecord handles it for both parties

      endSessionRecord(sessionId);
      console.log(`[Session] Ended by ${fromUserId}: ${sessionId}`);

    } catch (e) { console.error('end-session error', e); }
  });

  // --- Chat message (text / audio / file) ---
  socket.on('chat-message', async (data) => {
    logActivity('chat', 'New message', { from: data.from, to: data.to, length: data.text?.length });
    try {
      const { toUserId, sessionId, content, timestamp, messageId } = data || {};
      const fromUserId = socketToUser.get(socket.id);
      if (!fromUserId || !toUserId || !content || !messageId) return;

      socket.emit('message-status', {
        messageId,
        status: 'sent',
      });

      // Save to DB (Async)
      ChatMessage.create({
        messageId,
        sessionId,
        fromUserId,
        toUserId,
        text: content.text,
        timestamp: timestamp || Date.now()
      }).catch(e => console.error('ChatSave Error', e));

      // Emit to Room (userId) - works even after reconnect
      io.to(toUserId).emit('chat-message', {
        fromUserId,
        content,
        sessionId: sessionId || null,
        timestamp: timestamp || Date.now(),
        messageId,
      });

      // ALWAYS send FCM push for background delivery
      // App may be in background but socket still connected
      // FCM ensures message is delivered even if app is killed
      sendChatMessagePush(toUserId, fromUserId, content.text || 'New message', sessionId, messageId);
    } catch (err) {
      console.error('chat-message error', err);
    }
  });

  // --- Helper: Send Chat Message Push (for background messages) ---
  async function sendChatMessagePush(toUserId, fromUserId, messageText, sessionId, messageId) {
    try {
      const toUser = await User.findOne({ userId: toUserId });
      const fromUser = await User.findOne({ userId: fromUserId });

      if (toUser && toUser.fcmToken) {
        const payload = {
          type: 'CHAT_MESSAGE',
          sessionId: sessionId || '',
          callerName: fromUser?.name || 'Astrologer',
          callerId: fromUserId,
          text: (messageText || 'New message').substring(0, 200),
          messageId: messageId || Date.now().toString(),
          timestamp: Date.now().toString()
        };

        // Data-only message for background handling
        await sendFcmV1Push(toUser.fcmToken, payload, null);
        console.log(`Chat push sent to ${toUserId} from ${fromUserId}`);
      }
    } catch (e) {
      console.error('Chat Message Push Error:', e);
    }
  }


  // --- Helper: Send Chat Push ---
  async function sendChatPush(toUserId, fromUserId, messageText, sessionId) {
    try {
      const toUser = await User.findOne({ userId: toUserId });
      const fromUser = await User.findOne({ userId: fromUserId });

      if (toUser && toUser.fcmToken) {
        const payload = {
          type: 'INCOMING_CALL',
          callType: 'chat',
          sessionId: sessionId || `chat_${Date.now()}`,
          callerName: fromUser?.name || 'Client',
          callerId: fromUserId,
          body: messageText.substring(0, 100),
          timestamp: Date.now().toString()
        };

        const notification = {
          title: `Message from ${fromUser?.name}`,
          body: messageText.substring(0, 100)
        };

        await sendFcmV1Push(toUser.fcmToken, payload, notification);
      }
    } catch (e) { console.error('Chat Push Error:', e); }
  }

  // --- Get History ---
  socket.on('get-history', async (data, cb) => {
    if (typeof data === 'function') { cb = data; data = {}; }
    try {
      const userId = socketToUser.get(socket.id);
      if (!userId) return cb && cb({ ok: false });

      if (data && data.sessionId) {
        const messages = await ChatMessage.find({ sessionId: data.sessionId }).sort({ timestamp: 1 });
        return cb && cb({ ok: true, messages });
      }

      // Find sessions where user participated
      const sessions = await Session.find({ $or: [{ fromUserId: userId }, { toUserId: userId }] })
        .sort({ startTime: -1 })
        .limit(50);

      // Populate names (Mock style since we don't have populate setup easily, we'll fetch manually or send IDs)
      // Actually client can resolve names from its own list or we just send IDs + Time + Type

      cb({ ok: true, sessions });
    } catch (e) { console.error(e); cb({ ok: false }); }
  });

  // --- message-status (from Android) - handles both delivered and read ---
  socket.on('message-status', (data) => {
    try {
      const { toUserId, messageId, status } = data || {};
      const fromUserId = socketToUser.get(socket.id);
      if (!fromUserId || !toUserId || !messageId || !status) return;

      console.log(`[MessageStatus] ${status} from ${fromUserId} to ${toUserId} msgId=${messageId}`);

      // Emit to sender (toUserId is the original sender)
      io.to(toUserId).emit('message-status', {
        messageId,
        status, // 'delivered' or 'read'
      });
    } catch (err) { console.error('message-status error', err); }
  });

  // --- Receiver: delivered ack (legacy) ---
  socket.on('message-delivered', (data) => {
    try {
      const { toUserId, messageId } = data || {};
      const fromUserId = socketToUser.get(socket.id);
      if (!fromUserId || !toUserId || !messageId) return;

      // Emit to userId room (not socketId) - works after reconnect
      io.to(toUserId).emit('message-status', {
        messageId,
        status: 'delivered',
      });
    } catch (err) { console.error(err); }
  });

  // --- Receiver: read ack ---
  socket.on('message-read', (data) => {
    try {
      const { toUserId, messageId } = data || {};
      const fromUserId = socketToUser.get(socket.id);
      if (!fromUserId || !toUserId || !messageId) return;

      // Emit to userId room (not socketId) - works after reconnect
      io.to(toUserId).emit('message-status', {
        messageId,
        status: 'read',
      });
    } catch (err) { console.error(err); }
  });

  // --- Typing indicator ---
  socket.on('typing', (data) => {
    try {
      const { toUserId, isTyping } = data || {};
      const fromUserId = socketToUser.get(socket.id);
      if (!fromUserId || !toUserId) return;

      const targetSocketId = userSockets.get(toUserId);
      if (!targetSocketId) return;

      io.to(targetSocketId).emit('typing', {
        fromUserId,
        isTyping: !!isTyping,
      });
    } catch (err) { console.error('typing error', err); }
  });

  // --- Astrologer Registration Support (Web) ---
  socket.on('submit-astro-registration', async (data, cb) => {
    try {
      if (!data.realName || !data.cellNumber1) {
        return cb({ ok: false, error: 'Mandatory fields missing' });
      }

      // Check for existing pending application
      const existing = await AstrologerApplication.findOne({ cellNumber1: data.cellNumber1, status: 'pending' });
      if (existing) {
        return cb({ ok: false, error: 'Registration already pending for this number' });
      }

      const applicationId = crypto.randomUUID();
      await AstrologerApplication.create({
        applicationId,
        ...data,
        appliedAt: new Date()
      });

      console.log(`[Socket] New astrologer application from ${data.realName}`);
      cb({ ok: true });
    } catch (err) {
      console.error('Socket registration error:', err.message);
      cb({ ok: false, error: 'Internal Error' });
    }
  });

  // --- Phase 1: Connection & Billing Start ---
  socket.on('session-connect', async (data, cb) => {
    try {
      const { sessionId } = data || {};
      const userId = socketToUser.get(socket.id);

      if (!userId || !sessionId) return;

      console.log(`Session Connect: User ${userId} joined Session ${sessionId}`);

      await handleUserConnection(sessionId, userId);
      if (cb) cb({ ok: true, iceServers: ICE_SERVERS });

    } catch (err) {
      console.error('session-connect error:', err);
    }
  });

  async function handleUserConnection(sessionId, userId) {
    const session = await Session.findOne({ sessionId });
    if (!session) return;

    // Determine which timestamp to update
    const now = Date.now();
    let updated = false;

    if (userId === session.clientId) {
      if (!session.clientConnectedAt) {
        session.clientConnectedAt = now;
        updated = true;
        console.log(`Session ${sessionId}: Client connected at ${now}`);
      }
    } else if (userId === session.astrologerId) {
      if (!session.astrologerConnectedAt) {
        session.astrologerConnectedAt = now;
        updated = true;
        console.log(`Session ${sessionId}: Astrologer connected at ${now}`);
      }
    }

    if (updated) {
      await session.save();
    }

    // Check if billing can start
    if (session.clientConnectedAt && session.astrologerConnectedAt && !session.actualBillingStart) {
      const maxTime = Math.max(session.clientConnectedAt, session.astrologerConnectedAt);
      const billingStart = maxTime + 2000; // 2 seconds buffer

      session.actualBillingStart = billingStart;
      await session.save();

      // Update in-memory map for the ticker
      const activeSession = activeSessions.get(sessionId);
      if (activeSession) {
        activeSession.actualBillingStart = billingStart;

        // --- FIX: Initialize Billing Fields in Memory ---
        if (typeof activeSession.elapsedBillableSeconds === 'undefined') {
          activeSession.elapsedBillableSeconds = 0;
          activeSession.lastBilledMinute = 1; // Prepare for first minute check
          activeSession.clientId = session.clientId;
          activeSession.astrologerId = session.astrologerId;
          activeSession.currentSlab = 3; // Default Slab if not set
          activeSession.totalDeducted = 0;
          activeSession.totalEarned = 0;
          console.log(`Session ${sessionId}: Billing Fields Initialized (Memory)`);
        }

        // --- Phase 4: Init Pair Slab ---
        try {
          const currentMonth = new Date().toISOString().slice(0, 7); // YYYY-MM
          const pairId = `${session.clientId}_${session.astrologerId}`;

          let pairRec = await PairMonth.findOne({ pairId, yearMonth: currentMonth });
          if (!pairRec) {
            console.log(`Creating PairMonth for ${pairId} (Starting Slab 3)`);
            pairRec = await PairMonth.create({
              pairId,
              clientId: session.clientId,
              astrologerId: session.astrologerId,
              yearMonth: currentMonth,
              currentSlab: 3, // Default Slab 3
              slabLockedAt: 0
            });
          }

          activeSession.pairMonthId = pairRec._id;
          activeSession.currentSlab = pairRec.currentSlab;
          activeSession.initialPairSeconds = pairRec.slabLockedAt || 0;
          console.log(`Session ${sessionId} initialized with Slab ${activeSession.currentSlab}, InitialSecs: ${activeSession.initialPairSeconds}`);
        } catch (e) {
          console.error('PairMonth Init Error', e);
        }
      }

      console.log(`Session ${sessionId}: Billing starts at ${billingStart} (Buffer applied)`);

      // Get client wallet and rate for available minutes calculation
      const client = await User.findOne({ userId: session.clientId });
      const astro = await User.findOne({ userId: session.astrologerId });
      const clientBalance = client?.walletBalance || 0;
      const ratePerMinute = astro?.price || 10;
      const availableMinutes = Math.floor(clientBalance / ratePerMinute);

      // Notify both parties
      io.to(userSockets.get(session.clientId)).emit('billing-started', {
        startTime: billingStart,
        clientBalance,
        availableMinutes
      });
      io.to(userSockets.get(session.astrologerId)).emit('billing-started', {
        startTime: billingStart,
        clientBalance,
        ratePerMinute,
        availableMinutes
      });
    }
  }



  // --- Client Birth Chart Data ---
  socket.on('client-birth-chart', (data, cb) => {
    try {
      const { toUserId, birthData } = data || {};
      const fromUserId = socketToUser.get(socket.id);
      if (!fromUserId || !toUserId) return cb({ ok: false, error: 'Invalid data' });

      // Send birth chart data to astrologer
      io.to(toUserId).emit('client-birth-chart', {
        fromUserId,
        birthData
      });

      cb({ ok: true });
      console.log(`Birth chart sent from ${fromUserId} to ${toUserId}`);
    } catch (err) {
      console.error('client-birth-chart error', err);
      cb({ ok: false, error: err.message });
    }
  });

  // --- Session end (manual) ---
  socket.on('session-ended', (data) => {
    try {
      const { sessionId, toUserId, type, durationMs } = data || {};
      const fromUserId = socketToUser.get(socket.id);
      if (!fromUserId || !sessionId || !toUserId) return;

      endSessionRecord(sessionId);

      const targetSocketId = userSockets.get(toUserId);
      if (targetSocketId) {
        io.to(targetSocketId).emit('session-ended', {
          sessionId,
          fromUserId,
          type,
          durationMs,
        });
      }

      console.log(
        `Session ended (manual): sessionId=${sessionId}, type=${type}, from=${fromUserId}, to=${toUserId}, duration=${durationMs} ms`
      );
    } catch (err) {
      console.error('session-ended error', err);
    }
  });

  // --- ADMIN API ---
  const checkAdmin = async (sid) => {
    const uid = socketToUser.get(sid);
    if (!uid) return false;
    const u = await User.findOne({ userId: uid });
    return u && u.role === 'superadmin';
  };

  // --- Admin: Get All Users ---
  socket.on('get-all-users', async (cb) => {
    if (!await checkAdmin(socket.id)) return cb({ ok: false });
    try {
      const usersRaw = await User.find({}).sort({ role: 1, name: 1 }).lean();

      // Enhance users with referral counts (L1, L2, L3)
      // This is slightly heavy but requested for tracking
      const allUsers = await Promise.all(usersRaw.map(async (u) => {
        const l1 = await User.find({ referredBy: u.userId }).select('userId').lean();
        const l1Ids = l1.map(x => x.userId);

        const l2 = await User.find({ referredBy: { $in: l1Ids } }).select('userId').lean();
        const l2Ids = l2.map(x => x.userId);

        const l3 = await User.find({ referredBy: { $in: l2Ids } }).select('userId').lean();

        return {
          ...u,
          refStats: {
            l1: l1.length,
            l2: l2.length,
            l3: l3.length,
            total: l1.length + l2.length + l3.length
          }
        };
      }));

      cb({ ok: true, users: allUsers });
    } catch (e) {
      console.error("[Admin] Error fetching all users:", e);
      safeAck(cb, { ok: false });
    }
  });

  // --- Admin: Edit User (Name Only) ---
  socket.on('admin-edit-user', async (data, cb) => {
    if (!await checkAdmin(socket.id)) return safeAck(cb, { ok: false, error: 'Unauthorized' });
    try {
      const { targetUserId, updates } = data || {};
      if (!targetUserId || !updates || !updates.name) return safeAck(cb, { ok: false, error: 'Invalid Data' });

      const u = await User.findOne({ userId: targetUserId });
      if (!u) return safeAck(cb, { ok: false, error: 'User not found' });

      u.name = updates.name;
      await u.save();

      console.log(`Admin edited user ${u.userId}: Name -> ${u.name}`);

      if (u.role === 'astrologer') broadcastAstroUpdate();

      safeAck(cb, { ok: true });
    } catch (e) {
      console.error(e);
      safeAck(cb, { ok: false, error: 'Internal Error' });
    }
  });

  // --- Admin: Update User Details (Unified) ---
  socket.on('admin-update-user-details', async (data, cb) => {
    if (!await checkAdmin(socket.id)) return safeAck(cb, { ok: false, error: 'Unauthorized' });
    try {
      const { userId, updates } = data;
      const user = await User.findOne({ userId });
      if (!user) return safeAck(cb, { ok: false, error: 'User not found' });

      // Update allowed fields
      if (updates.name) user.name = updates.name;
      if (updates.price) user.price = parseInt(updates.price);
      if (updates.image) user.image = updates.image;
      if (typeof updates.isVerified === 'boolean') user.isVerified = updates.isVerified;
      if (updates.documentStatus) {
        user.documentStatus = updates.documentStatus;
        // Sync legacy boolean for backward compatibility if needed, but UI uses status now
        user.isDocumentVerified = (updates.documentStatus === 'verified');
      }

      await user.save();
      console.log(`Admin updated user ${user.name}:`, updates);

      if (user.role === 'astrologer') broadcastAstroUpdate();

      safeAck(cb, { ok: true, user });
    } catch (e) {
      console.error(e);
      safeAck(cb, { ok: false, error: 'Update Failed' });
    }
  });

  socket.on('admin-update-role', async (data, cb) => {
    if (!await checkAdmin(socket.id)) return safeAck(cb, { ok: false });
    try {
      const updates = { role: data.role };
      if (data.role === 'astrologer') {
        updates.walletBalance = 0;
      }
      await User.updateOne({ userId: data.userId }, updates);

      // Notify user of role/wallet change if online
      const sId = userSockets.get(data.userId);
      if (sId) {
        if (data.role === 'astrologer') io.to(sId).emit('wallet-update', { balance: 0 });
        io.to(sId).emit('app-notification', { text: `Your role has been updated to ${data.role}!` });
      }

      safeAck(cb, { ok: true });
    } catch (e) { safeAck(cb, { ok: false }); }
  });

  // === Astrologer Request: Approve ===
  socket.on('admin-approve-astrologer', async (data, cb) => {
    if (!await checkAdmin(socket.id)) return safeAck(cb, { ok: false, error: 'Unauthorized' });
    try {
      const user = await User.findOne({ userId: data.userId });
      if (!user) return safeAck(cb, { ok: false, error: 'User not found' });

      user.role = 'astrologer';
      user.walletBalance = 0;
      user.astrologerRequestStatus = 'approved';
      await user.save();

      console.log(`[Astrologer Approved] ${user.name} (${user.phone}) approved by admin`);
      logActivity('astrologer', 'Request Approved', { userId: user.userId, name: user.name });

      // Notify user via socket if online
      const sId = userSockets.get(user.userId);
      if (sId) {
        io.to(sId).emit('wallet-update', { balance: 0 });
        io.to(sId).emit('app-notification', {
          text: '🎉 Congratulations! Your astrologer request has been APPROVED! Please re-login to access your astrologer dashboard.'
        });
        io.to(sId).emit('role-changed', { role: 'astrologer' });
      }

      // FCM Push Notification
      if (user.fcmToken) {
        await sendFcmV1Push(user.fcmToken, {
          type: 'astrologer_approved',
          userId: user.userId
        }, {
          title: '🎉 Astrologer Request Approved!',
          body: 'Congratulations! You are now an Astrologer on AstroLuna. Please re-login to start your journey.'
        });
        console.log(`[FCM] Approval notification sent to ${user.name}`);
      }

      broadcastAstroUpdate();
      safeAck(cb, { ok: true });
    } catch (e) {
      console.error('[Approve Astrologer Error]', e);
      safeAck(cb, { ok: false, error: e.message });
    }
  });

  // === Astrologer Request: Reject ===
  socket.on('admin-reject-astrologer', async (data, cb) => {
    if (!await checkAdmin(socket.id)) return safeAck(cb, { ok: false, error: 'Unauthorized' });
    try {
      const user = await User.findOne({ userId: data.userId });
      if (!user) return safeAck(cb, { ok: false, error: 'User not found' });

      user.astrologerRequestStatus = 'rejected';
      await user.save();

      console.log(`[Astrologer Rejected] ${user.name} (${user.phone}) rejected by admin`);
      logActivity('astrologer', 'Request Rejected', { userId: user.userId, name: user.name });

      // Notify user via socket if online
      const sId = userSockets.get(user.userId);
      if (sId) {
        io.to(sId).emit('app-notification', {
          text: '❌ Your astrologer registration request was not approved at this time. Please contact support for more details.'
        });
      }

      // FCM Push Notification
      if (user.fcmToken) {
        await sendFcmV1Push(user.fcmToken, {
          type: 'astrologer_rejected',
          userId: user.userId
        }, {
          title: 'Astrologer Request Update',
          body: 'Your astrologer registration request was not approved at this time. Please contact support for more info.'
        });
        console.log(`[FCM] Rejection notification sent to ${user.name}`);
      }

      safeAck(cb, { ok: true });
    } catch (e) {
      console.error('[Reject Astrologer Error]', e);
      safeAck(cb, { ok: false, error: e.message });
    }
  });

  socket.on('admin-add-wallet', async (data, cb) => {
    if (!await checkAdmin(socket.id)) return safeAck(cb, { ok: false });
    try {
      const u = await User.findOne({ userId: data.userId });
      u.walletBalance += parseInt(data.amount);
      await u.save();

      // Notify user
      const s = userSockets.get(data.userId);
      if (s) io.to(s).emit('wallet-update', { balance: u.walletBalance });

      safeAck(cb, { ok: true });
    } catch (e) { safeAck(cb, { ok: false }); }
  });

  socket.on('admin-toggle-ban', async (data, cb) => {
    if (!await checkAdmin(socket.id)) return safeAck(cb, { ok: false });
    try {
      await User.updateOne({ userId: data.userId }, { isBanned: data.isBanned });
      safeAck(cb, { ok: true });
      // If banned, disconnect socket?
      if (data.isBanned) {
        const s = userSockets.get(data.userId);
        if (s) io.to(s).emit('force-logout'); // Need to handle client side
      }
    } catch (e) { safeAck(cb, { ok: false }); }
  });

  socket.on('admin-get-pending-requests', async (cb) => {
    if (!await checkAdmin(socket.id)) return safeAck(cb, { ok: false });
    try {
      const requests = await User.find({ astrologerRequestStatus: 'pending' }).sort({ astrologerRequestedAt: -1 });
      safeAck(cb, { ok: true, requests });
    } catch (e) { safeAck(cb, { ok: false }); }
  });

  socket.on('get-slab-rates', async (cb) => {
    if (!await checkAdmin(socket.id)) return safeAck(cb, { ok: false });
    safeAck(cb, { ok: true, rates: SLAB_RATES });
  });

  socket.on('update-slab-rates', async (rates, cb) => {
    if (!await checkAdmin(socket.id)) return safeAck(cb, { ok: false });
    for (const key in rates) {
      SLAB_RATES[key] = parseFloat(rates[key]);
    }
    safeAck(cb, { ok: true });
  });

  socket.on('admin-force-offline', async (data, cb) => {
    if (!await checkAdmin(socket.id)) return safeAck(cb, { ok: false });
    try {
      const astro = await User.findOne({ userId: data.userId });
      if (astro) {
        astro.isOnline = false;
        astro.isAvailable = false;
        astro.isChatOnline = false;
        astro.isAudioOnline = false;
        astro.isVideoOnline = false;
        await astro.save();
        broadcastAstroUpdate();
        safeAck(cb, { ok: true });
      } else safeAck(cb, { ok: false, error: 'User not found' });
    } catch (e) { safeAck(cb, { ok: false, error: e.message }); }
  });

  socket.on('admin-get-photo-approvals', async (cb) => {
    if (!await checkAdmin(socket.id)) return safeAck(cb, { ok: false });
    try {
      // Find users with a pending image URL. Assuming 'pendingImage' schema or unstructured approach.
      const pendingUsers = await User.find({ pendingImage: { $exists: true, $ne: '' } });
      safeAck(cb, { ok: true, requests: pendingUsers });
    } catch (e) { safeAck(cb, { ok: false }); }
  });

  socket.on('admin-resolve-photo-approval', async (data, cb) => {
    if (!await checkAdmin(socket.id)) return safeAck(cb, { ok: false });
    try {
      const user = await User.findOne({ userId: data.userId });
      if (user && data.action === 'approve') {
        user.image = user.pendingImage || user.image;
        user.pendingImage = undefined;
      } else if (user && data.action === 'reject') {
        user.pendingImage = undefined;
      }
      await user.save();
      safeAck(cb, { ok: true });
    } catch (e) { safeAck(cb, { ok: false, error: e.message }); }
  });

  socket.on('send-bulk-fcm', async (data, cb) => {
    if (!await checkAdmin(socket.id)) return safeAck(cb, { ok: false });
    try {
      let tokens = [];
      if (data.allUsers) {
        const users = await User.find({ fcmToken: { $exists: true, $ne: '' } });
        tokens = users.map(u => u.fcmToken);
      } else if (data.userIds && data.userIds.length > 0) {
        const users = await User.find({ userId: { $in: data.userIds }, fcmToken: { $exists: true, $ne: '' } });
        tokens = users.map(u => u.fcmToken);
      }
      let sentCount = 0;
      for (const token of tokens) {
        await sendFcmV1Push(token, { type: 'OFFER', imageUrl: data.imageUrl || '' }, { title: data.title, body: data.body }).then(res => {
          if (res.success) sentCount++;
        }).catch(err => console.error(err));
      }
      safeAck(cb, { ok: true, sentCount });
    } catch (e) { safeAck(cb, { ok: false }); }
  });

  socket.on('admin-delete-user', async (data, cb) => {
    if (!await checkAdmin(socket.id)) return safeAck(cb, { ok: false });
    try {
      const res = await User.deleteOne({ userId: data.userId });
      if (res.deletedCount > 0) {
        broadcastAstroUpdate();
        safeAck(cb, { ok: true });
      } else {
        safeAck(cb, { ok: false, error: 'User not found' });
      }
    } catch (e) { safeAck(cb, { ok: false, error: e.message }); }
  });

  // Phase 10: Ledger Stats
  socket.on('admin-get-ledger-stats', async (data, cb) => {
    if (!await checkAdmin(socket.id)) return safeAck(cb, { ok: false });
    try {
      // Get billing stats
      const billingStats = await BillingLedger.aggregate([
        {
          $group: {
            _id: null,
            totalRevenue: { $sum: '$chargedToClient' },
            totalAstroPayout: { $sum: '$creditedToAstrologer' },
            totalAdminRevenue: { $sum: '$adminAmount' },
            totalMinutes: { $sum: 1 }
          }
        }
      ]);

      // Get user counts
      const totalUsers = await User.countDocuments();
      const activeSessionCount = activeSessions.size;

      // Get full ledger for breakdown
      const fullLedger = await BillingLedger.find({}).sort({ createdAt: -1 }).limit(100);

      const billing = billingStats[0] || {};

      // Map to expected format
      const stats = {
        totalRevenue: billing.totalRevenue || 0,
        adminProfit: billing.totalAdminRevenue || 0,
        astroPayout: billing.totalAstroPayout || 0,
        totalDuration: (billing.totalMinutes || 0) * 60, // Convert minutes to seconds
        totalUsers: totalUsers,
        activeSessions: activeSessionCount
      };

      safeAck(cb, { ok: true, stats, fullLedger });
    } catch (e) {
      console.error(e);
      safeAck(cb, { ok: false });
    }
  });

  // --- Save FCM Token (for push notifications) ---
  socket.on('save-fcm-token', async ({ fcmToken }) => {
    const userId = socketToUser.get(socket.id);
    if (!userId || !fcmToken) return;

    try {
      await User.updateOne({ userId }, { fcmToken });
      console.log(`[FCM] Token saved for user: ${userId.substring(0, 8)}...`);
    } catch (e) {
      console.error('[FCM] Error saving token:', e);
    }
  });

  // --- Get Wallet (Manual Refresh) ---
  socket.on('get-wallet', async (data) => {
    const userId = socketToUser.get(socket.id);
    if (!userId) return;
    try {
      const u = await User.findOne({ userId });
      if (u) {
        socket.emit('wallet-update', {
          balance: u.walletBalance,
          totalEarnings: u.totalEarnings || 0
        });
      }
    } catch (e) { }
  });

  // --- Withdrawal Logic ---
  socket.on('request-withdrawal', async (data, cb) => {
    const userId = socketToUser.get(socket.id);
    if (!userId) return;
    try {
      const amount = parseInt(data.amount);
      if (!amount || amount < 100) return safeAck(cb, { ok: false, error: 'Minimum limit 100' });

      // Check Balance
      const u = await User.findOne({ userId });
      if (!u || u.walletBalance < amount) return safeAck(cb, { ok: false, error: 'Insufficient Balance' });

      // DEDUCT IMMEDIATELY
      u.walletBalance -= amount;
      await u.save();

      const w = await Withdrawal.create({
        astroId: userId,
        amount,
        status: 'pending',
        requestedAt: Date.now()
      });

      // Emit wallet update to self
      io.to(socket.id).emit('wallet-update', { balance: u.walletBalance });

      // Notify Super Admins
      io.to('superadmin').emit('admin-notification', {
        type: 'withdrawal_request',
        text: `💰 New Withdrawal Request: ${u.name} requested ₹${amount}`,
        data: { withdrawalId: w._id, astroName: u.name, amount }
      });

      safeAck(cb, { ok: true, balance: u.walletBalance });
    } catch (e) {
      console.error(e);
      safeAck(cb, { ok: false, error: 'Error' });
    }
  });

  socket.on('approve-withdrawal', async (data, cb) => {
    if (!await checkAdmin(socket.id)) return safeAck(cb, { ok: false });
    try {
      const { withdrawalId } = data;
      const w = await Withdrawal.findById(withdrawalId);
      if (!w || w.status !== 'pending') return safeAck(cb, { ok: false, error: 'Invalid Request' });

      const u = await User.findOne({ userId: w.astroId });
      if (!u) return safeAck(cb, { ok: false, error: 'User not found' });

      // Balance already deducted at request time

      // Update Request
      w.status = 'approved';
      w.processedAt = Date.now();
      await w.save();

      // Notify Astro
      const sId = userSockets.get(u.userId);
      if (sId) {
        io.to(sId).emit('app-notification', { text: `✅ Your withdrawal of ₹${w.amount} is approved!` });
      }

      safeAck(cb, { ok: true, balance: u.walletBalance });
    } catch (e) {
      console.error(e);
      safeAck(cb, { ok: false, error: 'Error' });
    }
  });

  socket.on('reject-withdrawal', async (data, cb) => {
    if (!await checkAdmin(socket.id)) return safeAck(cb, { ok: false });
    try {
      const { withdrawalId } = data;
      const w = await Withdrawal.findById(withdrawalId);
      if (!w || w.status !== 'pending') return safeAck(cb, { ok: false, error: 'Invalid Request' });

      const u = await User.findOne({ userId: w.astroId });
      if (u) {
        // REFUND
        u.walletBalance += w.amount;

        // If it was a referral withdrawal, reverse the referralWithdrawn count
        if (w.type === 'referral') {
          u.referralWithdrawn = Math.max(0, (u.referralWithdrawn || 0) - w.amount);
        }

        await u.save();

        const sId = userSockets.get(u.userId);
        if (sId) {
          io.to(sId).emit('wallet-update', { balance: u.walletBalance });
          io.to(sId).emit('app-notification', { text: `❌ Your withdrawal of ₹${w.amount} was rejected. Money refunded.` });
        }
      }

      w.status = 'rejected';
      w.processedAt = Date.now();
      await w.save();

      safeAck(cb, { ok: true });
    } catch (e) {
      console.error(e);
      safeAck(cb, { ok: false });
    }
  });

  socket.on('get-withdrawals', async (cb) => {
    try {
      const list = await Withdrawal.find().sort({ requestedAt: -1 }).limit(50);
      const enriched = [];
      for (const w of list) {
        const u = await User.findOne({ userId: w.astroId });
        enriched.push({ ...w.toObject(), astroName: u ? u.name : 'Unknown' });
      }
      safeAck(cb, { ok: true, list: enriched });
    } catch (e) {
      console.error(e);
      safeAck(cb, { ok: false, list: [] });
    }
  });

  socket.on('get-my-withdrawals', async (cb) => {
    const userId = socketToUser.get(socket.id);
    if (!userId) return;
    try {
      const list = await Withdrawal.find({ astroId: userId }).sort({ requestedAt: -1 }).limit(10);
      safeAck(cb, { ok: true, list });
    } catch (e) {
      safeAck(cb, { ok: false });
    }
  });

  socket.on('get-payout-status', async (data, cb) => {
    try {
      const userId = socketToUser.get(socket.id);
      if (!userId) return safeAck(cb, { ok: false });

      const pending = await Withdrawal.find({ astroId: userId, status: 'pending' });
      const totalPending = pending.reduce((sum, w) => sum + (w.amount || 0), 0);

      cb({ ok: true, pendingAmount: totalPending, count: pending.length });
    } catch (e) {
      console.error(e);
      cb({ ok: false, error: 'Error' });
    }
  });
  // --- End Withdrawal Logic ---

  // --- Disconnect ---
  socket.on('disconnect', async () => {
    const userId = socketToUser.get(socket.id);
    logActivity('socket', 'Socket disconnected', { socketId: socket.id, userId });
    if (userId) {
      console.log(`Socket disconnected: ${socket.id}, userId=${userId}`);
      socketToUser.delete(socket.id);

      if (userSockets.get(userId) === socket.id) {
        userSockets.delete(userId);
      }

      try {
        // If Astrologer, use grace period before marking offline
        const user = await User.findOne({ userId });
        if (user && user.role === 'astrologer') {
          // Save current status before potential offline
          return; // Manual Toggle Rule: Skip offline marking

        }
      } catch (e) { console.error('Disconnect DB error', e); }

      const sid = userActiveSession.get(userId);
      if (sid) {
        // --- FIX: Don't end session immediately. Give grace period. ---
        console.log(`[Session] User ${userId} disconnected. Starting grace period for Session ${sid}`);

        // Clear existing if any (debounce)
        if (sessionDisconnectTimeouts.has(userId)) {
          clearTimeout(sessionDisconnectTimeouts.get(userId));
        }

        const timeoutId = setTimeout(() => {
          // If this runs, it means user didn't reconnect in time
          console.log(`[Session] Grace period expired for ${userId}. Ending Session ${sid}`);

          sessionDisconnectTimeouts.delete(userId);

          // Double check if session still active (maybe other user ended it?)
          const s = activeSessions.get(sid);
          if (s) {
            // We can optionally update Session end time in DB here
            Session.updateOne({ sessionId: sid }, { endTime: Date.now(), duration: Date.now() - s.startedAt }).catch(() => { });

            const otherUserId = getOtherUserIdFromSession(sid, userId);

            // NOW we end it
            endSessionRecord(sid);

            if (otherUserId) {
              // Notify other user that partner dropped
              io.to(otherUserId).emit('session-ended', {
                sessionId: sid,
                reason: 'partner_disconnected'
              });
            }
          }
        }, SESSION_GRACE_PERIOD);

        sessionDisconnectTimeouts.set(userId, timeoutId);
      }
    } else {
      console.log('Socket disconnected (no user):', socket.id);
    }
  });
});

// ===== Reliable Calling System (DB + FCM) =====

// 1. Astrologer Online Toggle
app.post('/api/astrologer/online', async (req, res) => {
  const { userId, available, fcmToken } = req.body;
  if (!userId) return res.json({ ok: false, error: 'Missing userId' });

  try {
    const update = {
      isAvailable: available,
      isOnline: available, // Sync Master
      isChatOnline: available,
      isAudioOnline: available,
      isVideoOnline: available,
      lastSeen: new Date()
    };

    if (fcmToken) {
      update.fcmToken = fcmToken;
    }

    await User.updateOne({ userId }, update);

    // Broadcast update to real-time clients
    await broadcastAstroUpdate();
    res.json({ ok: true });
  } catch (e) {
    console.error("Online Toggle Error:", e);
    res.json({ ok: false });
  }
});

// 1b. Individual Service Toggle (Chat / Audio / Video)
app.post('/api/astrologer/service-toggle', async (req, res) => {
  const { userId, service, enabled } = req.body;
  if (!userId || !service) return res.json({ ok: false, error: 'Missing params' });

  try {
    const update = { lastSeen: new Date() };

    // Update specific service
    if (service === 'chat') {
      update.isChatOnline = enabled;
    } else if (service === 'audio') {
      update.isAudioOnline = enabled;
    } else if (service === 'video') {
      update.isVideoOnline = enabled;
    }

    // Also update isAvailable and isOnline if any service is enabled
    const user = await User.findOne({ userId });
    if (user) {
      const chatOn = service === 'chat' ? enabled : user.isChatOnline;
      const audioOn = service === 'audio' ? enabled : user.isAudioOnline;
      const videoOn = service === 'video' ? enabled : user.isVideoOnline;

      // isAvailable = true if ANY service is online
      update.isAvailable = chatOn || audioOn || videoOn;
      update.isOnline = chatOn || audioOn || videoOn;
    }

    await User.updateOne({ userId }, update);

    // Broadcast update
    await broadcastAstroUpdate();

    console.log(`[Service Toggle] ${userId}: ${service} = ${enabled}`);
    res.json({ ok: true });
  } catch (e) {
    console.error("Service Toggle Error:", e);
    res.json({ ok: false });
  }
});

// 2. Initiate Call (User -> Astrologer)
app.post('/api/call/initiate', async (req, res) => {
  const { callerId, receiverId } = req.body;
  if (!callerId || !receiverId) return res.json({ ok: false, error: 'Missing IDs' });

  try {
    // A. Check Availability (DB Source of Truth)
    const astro = await User.findOne({ userId: receiverId });


    if (!astro || !astro.isAvailable) {
      return res.json({ ok: false, error: 'Astrologer is Offline', code: 'OFFLINE' });
    }

    // B. Create Call Request
    const callId = "CALL_" + Date.now() + "_" + Math.floor(Math.random() * 1000);
    await CallRequest.create({
      callId,
      callerId,
      receiverId,
      status: 'ringing'
    });

    // C. Send FCM Push Notification (WAKE UP APP)
    // Send FCM v1 Push Notification
    if (astro.fcmToken) {
      const fcmData = {
        type: 'incoming_call',
        callId: callId,
        callerId: callerId,
        callerName: 'Client'
      };

      const fcmNotification = {
        title: 'Incoming Call',
        body: 'Tap to answer video call'
      };

      const fcmResult = await sendFcmV1Push(astro.fcmToken, fcmData, fcmNotification);
      console.log(`[FCM v1] Sent Push to ${receiverId} | Success: ${fcmResult.success}`);
    } else {
      console.log(`[FCM v1] No Token for ${receiverId}. Call might fail if app is killed.`);
    }

    res.json({ ok: true, callId, status: 'ringing' });

  } catch (e) {
    console.error("Init Call Error:", e);
    res.json({ ok: false, error: 'Server Error' });
  }
});

// 3. Accept Call (Astrologer -> Server)
app.post('/api/call/accept', async (req, res) => {
  const { callId, receiverId } = req.body;
  try {
    const call = await CallRequest.findOne({ callId });
    if (!call) return res.json({ ok: false, error: 'Invalid Call' });

    if (call.status !== 'ringing') {
      return res.json({ ok: false, error: 'Call already handled' });
    }

    call.status = 'accepted';
    await call.save();

    res.json({ ok: true, message: 'Call Connected' });

  } catch (e) {
    console.error("Accept Call Error:", e);
    res.json({ ok: false });
  }
});


// ===== Payment Gateway Logic (PhonePe) =====
// Configuration from environment variables
// Config moved to top of file

// ===== Payment Token Store (In-Memory) =====
// Token → { userId, amount, createdAt, used }
const paymentTokens = new Map();

// Token cleanup - delete expired tokens every 5 minutes
setInterval(() => {
  const now = Date.now();
  const expiryTime = 10 * 60 * 1000; // 10 minutes
  for (const [token, data] of paymentTokens) {
    if (now - data.createdAt > expiryTime) {
      paymentTokens.delete(token);
    }
  }
}, 5 * 60 * 1000);

// Generate Payment Token (Called from WebView with auth session)
app.post('/api/payment/token', async (req, res) => {
  try {
    const { userId, amount } = req.body;

    if (!userId || !amount) {
      return res.json({ ok: false, error: 'Missing userId or amount' });
    }

    if (amount < 1) {
      return res.json({ ok: false, error: 'Minimum amount is ₹1' });
    }

    // Verify user exists
    const user = await User.findOne({ userId });
    if (!user) {
      return res.json({ ok: false, error: 'User not found' });
    }

    // GST 18% calculation
    const baseAmountValue = parseFloat(amount);
    const gstValue = Math.round(baseAmountValue * 0.18 * 100) / 100;
    const totalAmountValue = Math.round((baseAmountValue + gstValue) * 100) / 100;

    // Generate secure token
    const token = crypto.randomBytes(32).toString('hex');

    // Store token mapping
    paymentTokens.set(token, {
      userId: userId,
      amount: totalAmountValue, // STORE TOTAL AMOUNT TO CHARGE
      baseAmount: baseAmountValue, // STORE BASE AMOUNT TO CREDIT
      gst: gstValue,
      createdAt: Date.now(),
      used: false,
      userName: user.name,
      userPhone: user.phone
    });

    console.log(`Payment Token Created: ${token.substring(0, 8)}... for ${user.name} | Base: ₹${baseAmountValue} + GST: ₹${gstValue} = Total: ₹${totalAmountValue}`);

    res.json({
      ok: true,
      token,
      baseAmount: baseAmountValue,
      totalAmount: totalAmountValue,
      gst: gstValue
    });

  } catch (e) {
    console.error('Payment Token Error:', e);
    res.json({ ok: false, error: 'Failed to create payment token' });
  }
});

// Verify Payment Token (Called from payment.html in browser)
app.get('/api/verify-payment-token', async (req, res) => {
  try {
    const { token } = req.query;

    if (!token) {
      return res.json({ valid: false, error: 'Token required' });
    }

    const tokenData = paymentTokens.get(token);

    if (!tokenData) {
      return res.json({ valid: false, error: 'Invalid or expired token' });
    }

    // Check expiry (10 minutes)
    const expiryTime = 10 * 60 * 1000;
    if (Date.now() - tokenData.createdAt > expiryTime) {
      paymentTokens.delete(token);
      return res.json({ valid: false, error: 'Token expired' });
    }

    // Check if already used
    if (tokenData.used) {
      return res.json({ valid: false, error: 'Token already used' });
    }

    // Valid token - return payment details
    res.json({
      valid: true,
      amount: tokenData.amount, // Total amount to pay
      baseAmount: tokenData.baseAmount,
      gst: tokenData.gst,
      userName: tokenData.userName,
      expiresIn: Math.floor((expiryTime - (Date.now() - tokenData.createdAt)) / 1000) // seconds
    });

  } catch (e) {
    console.error('Verify Token Error:', e);
    res.json({ valid: false, error: 'Verification failed' });
  }
});

// 1. Initiate Payment (Supports both token-based and legacy userId-based)
app.post('/api/payment/create', async (req, res) => {
  logActivity('payment', 'Payment initiation started', req.body);
  try {
    let { amount, userId, isApp, token } = req.body;

    // Token-based authentication (SECURE - for browser flow)
    if (token) {
      const tokenData = paymentTokens.get(token);

      if (!tokenData) {
        return res.json({ ok: false, error: 'Invalid or expired token' });
      }

      // Check expiry (10 minutes)
      const expiryTime = 10 * 60 * 1000;
      if (Date.now() - tokenData.createdAt > expiryTime) {
        paymentTokens.delete(token);
        return res.json({ ok: false, error: 'Token expired' });
      }

      // Check if already used
      if (tokenData.used) {
        return res.json({ ok: false, error: 'Token already used' });
      }

      // Mark token as used (single-use)
      tokenData.used = true;

      // Extract userId and amount from token
      userId = tokenData.userId;
      amount = tokenData.amount;

      console.log(`Token Auth Payment: ${token.substring(0, 8)}... userId=${userId} amount=${amount}`);
    }

    // Legacy check (for backward compatibility with WebView calls)
    if (!amount || !userId) {
      return res.json({ ok: false, error: 'Missing Amount or User' });
    }

    // Apply 18% GST if not already applied (Token flow already has it)
    if (!token) {
      const baseAmt = parseFloat(amount);
      const gst = Math.round(baseAmt * 0.18 * 100) / 100;
      amount = Math.round((baseAmt + gst) * 100) / 100;
      console.log(`Legacy Auth Payment: userId=${userId} Base: ${baseAmt} + GST: ${gst} = Total: ${amount}`);
    }

    // Fetch User to get real mobile number
    const userObj = await User.findOne({ userId });
    const rawPhone = (userObj && userObj.phone) ? userObj.phone : "9999999999";
    const userMobile = rawPhone.replace(/[^0-9]/g, '').slice(-10);

    const merchantTransactionId = "MT" + Date.now() + Math.floor(Math.random() * 1000);
    const redirectUrl = `https://astroluna.in/api/payment/callback`;

    // Create Pending Record
    await Payment.create({
      transactionId: merchantTransactionId,
      merchantTransactionId,
      userId,
      amount, // THIS IS NOW THE TOTAL AMOUNT (BASE + GST)
      status: 'pending',
      isApp: !!isApp // Store the source
    });

    // PhonePe Standard Checkout v2
    const amountInPaisa = Math.round(amount * 100);
    const callbackRedirectUrl = isApp
      ? `https://astroluna.in/api/payment/callback?isApp=true&txnId=${merchantTransactionId}`
      : `https://astroluna.in/api/payment/callback?txnId=${merchantTransactionId}`;

    const phonepeResult = await callPhonePePayV2(
      merchantTransactionId,
      amountInPaisa,
      callbackRedirectUrl,
      userMobile
    );

    if (phonepeResult.success) {
      const payUrl = phonepeResult.data.redirectUrl;
      const orderId = phonepeResult.data.orderId;

      console.log(`[PhonePe v2] Payment created: orderId=${orderId}, redirectUrl=${payUrl ? 'YES' : 'NO'}`);

      if (!payUrl) {
        return res.json({
          ok: false,
          payload: null,
          error: 'No payment URL received from PhonePe'
        });
      }

      res.json({
        ok: true,
        payload: {
          merchantTransactionId: merchantTransactionId,
          orderId: orderId,
          paymentUrl: payUrl,
          useWebFlow: true
        },
        error: null
      });
    } else {
      console.error("PhonePe v2 Initiation Failed:", JSON.stringify(phonepeResult.data));
      const errorMsg = phonepeResult.data?.message || phonepeResult.data?.code || 'Payment Init Failed';
      res.json({
        ok: false,
        payload: null,
        error: errorMsg
      });
    }
  } catch (e) {
    logActivity('payment', 'Payment Initiation CRITICAL Error', { error: e.message, stack: e.stack });
    console.error("Payment Create Error Details:", e);
    res.json({
      ok: false,
      payload: null,
      error: 'Internal Error',
      details: e.message
    });
  }
});

// ===== PhonePe v2: Check Order Status API =====
async function checkPhonePeOrderStatus(merchantOrderId) {
  const endpoint = `https://api.phonepe.com/apis/pg/checkout/v2/order/${merchantOrderId}/status?details=true`;

  const oauthToken = await getValidPhonePeToken();
  if (!oauthToken) {
    console.error("[PhonePe Status] No OAuth token");
    return { success: false, state: 'ERROR' };
  }

  try {
    const response = await fetch(endpoint, {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `O-Bearer ${oauthToken}`,
        'accept': 'application/json'
      }
    });

    const text = await response.text();
    let data;
    try {
      data = JSON.parse(text);
    } catch (e) {
      console.error("[PhonePe Status] Non-JSON Response:", text.substring(0, 500));
      return { success: false, state: 'ERROR' };
    }

    console.log(`[PhonePe Status] OrderId: ${merchantOrderId}, State: ${data.state}, Status: ${response.status}`);

    // Debug Log
    try {
      const logMsg = `\n--- ${new Date().toISOString()} ---\n[v2 STATUS] OrderId: ${merchantOrderId}\nState: ${data.state}\nRes: ${JSON.stringify(data).substring(0, 500)}\n`;
      fs.appendFileSync('phonepe_debug.log', logMsg);
    } catch (err) { }

    return { success: true, data, state: data.state || 'UNKNOWN' };
  } catch (err) {
    console.error("[PhonePe Status] Error:", err.message);
    return { success: false, state: 'ERROR' };
  }
}

// Helper: Process payment result (shared between GET redirect and POST callback)
async function processPaymentResult(merchantTransactionId, isSuccess, providerReferenceId, isApp, res) {
  const payment = await Payment.findOne({
    $or: [
      { transactionId: merchantTransactionId },
      { merchantTransactionId: merchantTransactionId }
    ]
  });

  if (!payment) {
    console.error('Payment not found for:', merchantTransactionId);
    return res.redirect('/?status=fail&reason=not_found');
  }

  const redirectIsApp = isApp || payment.isApp;

  console.log(`[WALLET DEBUG] isSuccess: ${isSuccess}, Payment: ${payment._id}, userId: ${payment.userId}, amount: ${payment.amount}, currentStatus: ${payment.status}`);

  if (isSuccess) {
    if (payment.status !== 'success') {
      payment.status = 'success';
      payment.providerRefId = providerReferenceId || '';
      await payment.save();

      // Credit Wallet
      const user = await User.findOne({ userId: payment.userId });
      if (user) {
        // Rule: 18% GST. If user pays 118, credit 100.
        const creditAmount = Math.round(payment.amount / 1.18);
        user.walletBalance += creditAmount;
        await user.save();
        console.log(`✅ Wallet Credited: ${user.name} +₹${creditAmount} (Paid: ₹${payment.amount}, GST deducted)`);

        // Notify Socket if online
        const sId = userSockets.get(user.userId);
        if (sId) {
          io.to(sId).emit('wallet-update', {
            balance: user.walletBalance,
            totalEarnings: user.totalEarnings
          });
          io.to(sId).emit('app-notification', { text: `✅ Recharge Successful! +₹${creditAmount} (Excl. 18% GST)` });
        }
      }
    }

    if (redirectIsApp) {
      const txnId = merchantTransactionId || '';
      return res.redirect(`/payment-success?amount=${payment.amount || ''}&txnId=${txnId}`);
    }
    return res.redirect(`/wallet?status=success&amount=${payment.amount}`);
  } else {
    // Failure
    if (payment.status !== 'success') {
      payment.status = 'failed';
      await payment.save();
    }

    if (redirectIsApp) {
      return res.redirect('/payment-failed');
    }
    return res.redirect(`/wallet?status=failure`);
  }
}

// 2a. Callback - GET (User redirect from PhonePe v2)
app.get('/api/payment/callback', async (req, res) => {
  console.log('=================================');
  console.log('[CALLBACK GET] /api/payment/callback');
  console.log('[CALLBACK GET] Query:', req.query);
  console.log('=================================');

  try {
    const merchantTransactionId = req.query.txnId;
    const isApp = req.query.isApp === 'true';

    if (!merchantTransactionId) {
      console.error('[CALLBACK GET] No txnId in query');
      return res.redirect('/wallet?status=failure&reason=no_txnId');
    }

    // Check payment status via PhonePe Order Status API
    const statusResult = await checkPhonePeOrderStatus(merchantTransactionId);
    const state = statusResult.state;

    // v2 states: COMPLETED, FAILED, PENDING
    const isSuccess = state === 'COMPLETED';
    const providerRefId = statusResult.data?.paymentDetails?.[0]?.providerReferenceId || '';

    console.log(`[CALLBACK GET] txnId: ${merchantTransactionId}, State: ${state}, isSuccess: ${isSuccess}`);

    await processPaymentResult(merchantTransactionId, isSuccess, providerRefId, isApp, res);

  } catch (e) {
    console.error("Callback GET Error:", e);
    return res.redirect('/?status=error');
  }
});

// 2b. Callback - POST (S2S webhook from PhonePe)
app.post('/api/payment/callback', async (req, res) => {
  console.log('=================================');
  console.log('[CALLBACK POST] /api/payment/callback');
  console.log('[CALLBACK POST] Body:', JSON.stringify(req.body).substring(0, 200));
  console.log('[CALLBACK POST] Query:', req.query);
  console.log('=================================');

  try {
    let decoded = {};

    // Case 1: Base64 Encoded JSON (S2S callback)
    if (req.body.response) {
      decoded = JSON.parse(Buffer.from(req.body.response, 'base64').toString('utf-8'));
    }
    // Case 2: Direct Form POST
    else if (req.body.code || req.body.merchantTransactionId || req.body.state) {
      decoded = req.body;
    }
    // Case 3: v2 redirect with txnId in query — check status via API
    else if (req.query.txnId) {
      const merchantTransactionId = req.query.txnId;
      const isApp = req.query.isApp === 'true';
      const statusResult = await checkPhonePeOrderStatus(merchantTransactionId);
      const isSuccess = statusResult.state === 'COMPLETED';
      const providerRefId = statusResult.data?.paymentDetails?.[0]?.providerReferenceId || '';

      return await processPaymentResult(merchantTransactionId, isSuccess, providerRefId, isApp, res);
    }
    else {
      logActivity('payment', 'Callback POST failed: No data found', { body: req.body, query: req.query });
      console.log('[CALLBACK POST] No payment data found');

      const userAgent = req.headers['user-agent'] || '';
      const isAndroidApp = req.query.isApp === 'true' || userAgent.includes('Android') || userAgent.includes('astrolunaApp');

      if (isAndroidApp) {
        const intentUrl = `intent://payment-failed?reason=no_response#Intent;scheme=astroluna;package=com.astroluna.app;end`;
        const customScheme = `astroluna://payment-failed?reason=no_response`;
        return res.send(`
          <html>
          <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>body{font-family:sans-serif;text-align:center;padding:20px;}</style>
          </head>
          <body>
          <h3>Redirecting...</h3>
          <script>
            window.location.href = "${intentUrl}";
            setTimeout(() => { window.location.href = "${customScheme}"; }, 800);
          </script>
          </body></html>
        `);
      }

      // Send 200 OK for S2S (PhonePe expects it)
      return res.status(200).json({ ok: true });
    }

    // Process decoded v1/legacy response
    const code = decoded.code || decoded.state;
    const merchantTransactionId = decoded.data?.merchantTransactionId || decoded.merchantTransactionId || decoded.merchantOrderId || req.query.txnId;
    const providerReferenceId = decoded.data?.providerReferenceId || decoded.providerReferenceId;

    console.log(`[CALLBACK POST] Decoded: txnId=${merchantTransactionId}, code=${code}`);

    // v1 success codes + v2 COMPLETED state
    const isSuccess = code === 'PAYMENT_SUCCESS' || code === 'SUCCESS' || code === 'COMPLETED';
    const isApp = req.query.isApp === 'true';

    await processPaymentResult(merchantTransactionId, isSuccess, providerReferenceId, isApp, res);

  } catch (e) {
    console.error("Callback POST Error:", e);
    return res.redirect('/?status=error');
  }
});

// --- 3. Public Status Pages ---
app.get('/payment-success', (req, res) => {
  const { amount, txnId } = req.query;
  const intentUrl = `intent://payment-success?status=success&txnId=${txnId}#Intent;scheme=astroluna;package=com.astroluna.app;end`;
  const customSchemeUrl = `astroluna://payment-success?status=success&txnId=${txnId}`;

  res.send(`
    <!DOCTYPE html>
    <html>
      <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Success</title>
        <style>
          body { display:flex; flex-direction:column; align-items:center; justify-content:center; height:100vh; font-family:sans-serif; background:#f0fdf4; margin:0; text-align:center; }
          .card { background:white; padding:40px; border-radius:20px; box-shadow:0 10px 30px rgba(0,0,0,0.1); width:320px; }
          .icon { font-size:60px; color:#22c55e; margin-bottom:20px; }
          .btn { display:block; padding:15px; background:#16a34a; color:white; text-decoration:none; border-radius:10px; font-weight:bold; margin-top:20px; }
        </style>
      </head>
      <body>
        <div class="card">
          <div class="icon">✓</div>
          <h2>Success!</h2>
          <p>₹${amount || '--'}</p>
          <a href="${intentUrl}" class="btn">Return to Home</a>
          <script>
             function openApp() {
               // Try Intent first (Chrome/Android)
               window.location.href = "${intentUrl}";
               // Immediate Deep Link fallback
               setTimeout(() => { window.location.href = "${customSchemeUrl}"; }, 100);
               // Backup force link
               setTimeout(() => { window.location.href = "astroluna://payment-success"; }, 500);
             }
             openApp();
          </script>
        </div>
      </body>
    </html>
  `);
});

app.get('/payment-failed', (req, res) => {
  const intentUrl = `intent://payment-failed?status=failed#Intent;scheme=astroluna;package=com.astroluna.app;end`;
  const customSchemeUrl = `astroluna://payment-failed?status=failed`;
  res.send(`
    <!DOCTYPE html>
    <html>
      <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Failed</title>
        <style>
          body { display:flex; flex-direction:column; align-items:center; justify-content:center; height:100vh; font-family:sans-serif; background:#fef2f2; margin:0; text-align:center; }
          .card { background:white; padding:40px; border-radius:20px; box-shadow:0 10px 30px rgba(0,0,0,0.1); width:320px; }
          .icon { font-size:60px; color:#ef4444; margin-bottom:20px; }
          .btn { display:block; padding:15px; background:#b91c1c; color:white; text-decoration:none; border-radius:10px; font-weight:bold; margin-top:20px; }
        </style>
      </head>
      <body>
        <div class="card">
          <div class="icon">✗</div>
          <h2>Failed</h2>
          <a href="${intentUrl}" class="btn">Return to Home</a>
          <script>
             function openApp() { window.location.href = "${intentUrl}"; setTimeout(() => { window.location.href = "${customSchemeUrl}"; }, 100); }
             openApp();
          </script>
        </div>
      </body>
    </html>
  `);
});

// 3. Payment History API
app.get('/api/payment/history/:userId', async (req, res) => {
  try {
    const { userId } = req.params;
    if (!userId) return res.status(400).json({ error: 'UserId required' });

    // Fetch last 20 transactions
    const transactions = await Payment.find({ userId })
      .sort({ createdAt: -1 })
      .limit(20)
      .lean();

    res.json({ ok: true, data: transactions });
  } catch (e) {
    console.error("Payment History Error:", e);
    res.status(500).json({ ok: false, error: 'Internal Server Error' });
  }
});

// ===== PhonePe SDK API (Native App Payment) =====

// PhonePe SDK Init - For React Native PhonePe SDK
app.post('/api/phonepe/init', async (req, res) => {
  try {
    const { userId, amount } = req.body;
    if (!userId || !amount) {
      return res.status(400).json({ ok: false, error: 'userId and amount required' });
    }

    // Fetch User
    let user = await User.findOne({ userId });
    if (!user) {
      if (userId === 'DEMO_USER_123') {
        user = await User.create({
          userId: 'DEMO_USER_123',
          phone: '9999999999',
          name: 'Demo User',
          walletBalance: 0,
          referralCode: 'DEMO' + Math.floor(Math.random() * 1000)
        });
        console.log('[PhonePe Init] Created Demo User');
      } else {
        return res.status(404).json({ ok: false, error: 'User not found' });
      }
    }

    const userMobile = (user.phone || "9999999999").replace(/[^0-9]/g, '').slice(-10);
    const merchantTransactionId = "TXN_" + Date.now() + "_" + Math.floor(Math.random() * 1000);
    const cleanUserId = userId.replace(/[^a-zA-Z0-9]/g, '');

    // Create Pending Payment Record
    await Payment.create({
      transactionId: merchantTransactionId,
      merchantTransactionId,
      userId,
      amount,
      status: 'pending'
    });

    // PhonePe Payload
    const payload = {
      merchantId: PHONEPE_MERCHANT_ID,
      merchantTransactionId: merchantTransactionId,
      merchantUserId: cleanUserId,
      amount: Math.round(amount * 100),
      redirectUrl: `https://astroluna.in/api/payment/callback?isApp=true`,
      redirectMode: "REDIRECT", // Changed from POST to REDIRECT
      callbackUrl: `https://astroluna.in/api/phonepe/callback`,
      mobileNumber: userMobile,
      paymentInstrument: {
        type: "PAY_PAGE"
      }
    };

    const phonepeResult = await callPhonePePay(payload);
    const data = phonepeResult.data;
    console.log('[PhonePe SDK Init]', JSON.stringify(data));

    if (data && data.success) {
      res.json({
        ok: true,
        transactionId: merchantTransactionId,
        data: data.data
      });
    } else {
      // Return detailed error from PhonePe
      const errorMsg = data?.message || data?.code || 'Payment initialization failed';
      console.error(`[PhonePe Init Failed] Status: ${phonepeResult.status}`, data);
      res.json({
        ok: false,
        error: errorMsg,
        details: data // Send full data for debugging
      });
    }

  } catch (e) {
    console.error("PhonePe SDK Init Error:", e);
    res.status(500).json({ ok: false, error: 'Internal Server Error' });
  }
});

// NEW: Signature Endpoint for Native Android SDK
app.post('/api/phonepe/sign', async (req, res) => {
  try {
    const { userId, amount } = req.body;
    if (!userId || !amount) {
      return res.status(400).json({ ok: false, error: 'userId and amount required' });
    }

    const user = await User.findOne({ userId });
    const userMobile = user ? (user.phone || "9999999999").replace(/[^0-9]/g, '').slice(-10) : "9999999999";
    const merchantTransactionId = "TXN_" + Date.now() + "_" + Math.floor(Math.random() * 1000);
    const cleanUserId = userId.replace(/[^a-zA-Z0-9]/g, '');

    // Record intent in DB
    await Payment.create({
      transactionId: merchantTransactionId,
      merchantTransactionId,
      userId,
      amount,
      status: 'pending'
    });

    // Native SDK Payload
    const payload = {
      merchantId: PHONEPE_MERCHANT_ID,
      merchantTransactionId: merchantTransactionId,
      merchantUserId: cleanUserId,
      amount: Math.round(amount * 100), // Ensure Integer
      callbackUrl: "https://astroluna.in/api/phonepe/callback",
      mobileNumber: userMobile,
      paymentInstrument: {
        type: "PAY_PAGE"
      }
    };

    const base64Payload = Buffer.from(JSON.stringify(payload)).toString('base64');
    const stringToSign = base64Payload + "/pg/v1/pay" + PHONEPE_SALT_KEY;
    const sha256 = crypto.createHash('sha256').update(stringToSign).digest('hex');
    const checksum = sha256 + "###" + PHONEPE_SALT_INDEX;

    res.json({
      ok: true,
      payload: base64Payload,
      checksum: checksum,
      transactionId: merchantTransactionId,
      error: null
    });


  } catch (e) {
    console.error("PhonePe Sign Error:", e);
    res.json({
      ok: false,
      payload: null,
      error: 'Signing failed',
      details: e.message
    });
  }
});

// PhonePe Status Check - Verify payment after return from PhonePe
app.get('/api/phonepe/status/:transactionId', async (req, res) => {
  try {
    const { transactionId } = req.params;
    if (!transactionId) {
      return res.status(400).json({ ok: false, error: 'Transaction ID required' });
    }

    // Check DB first
    const payment = await Payment.findOne({
      $or: [{ transactionId }, { merchantTransactionId: transactionId }]
    });

    if (payment && payment.status === 'success') {
      return res.json({
        ok: true,
        status: 'success',
        amount: payment.amount,
        userId: payment.userId
      });
    }

    // Verify with PhonePe API
    const statusPath = `/pg/v1/status/${PHONEPE_MERCHANT_ID}/${transactionId}`;
    const stringToSign = statusPath + PHONEPE_SALT_KEY;
    const sha256 = crypto.createHash('sha256').update(stringToSign).digest('hex');
    const checksum = sha256 + "###" + PHONEPE_SALT_INDEX;

    const oauthToken = await getValidPhonePeToken();
    const response = await fetch(`${PHONEPE_HOST_URL}${statusPath}`, {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
        'X-VERIFY': checksum,
        'X-MERCHANT-ID': PHONEPE_MERCHANT_ID,
        'Authorization': `Bearer ${oauthToken}`
      }
    });

    const data = await response.json();
    console.log('[PhonePe Status Check]', transactionId, data.code);

    if (data.success && data.code === 'PAYMENT_SUCCESS') {
      // Update payment record and credit wallet if not already done
      if (payment && payment.status !== 'success') {
        payment.status = 'success';
        payment.providerRefId = data.data?.transactionId;
        await payment.save();

        // Credit Wallet
        const user = await User.findOne({ userId: payment.userId });
        if (user) {
          user.walletBalance += payment.amount;
          user.hasRecharged = true;
          await user.save();
          console.log(`[PhonePe] Wallet Credited: ${user.name} +₹${payment.amount}`);

          // Notify via Socket
          const sId = userSockets.get(user.userId);
          if (sId) {
            io.to(sId).emit('wallet-update', { balance: user.walletBalance });
            io.to(sId).emit('app-notification', { text: `✅ Recharge Successful! +₹${payment.amount}` });
          }
        }
      }

      return res.json({ ok: true, status: 'success', amount: payment?.amount });
    } else if (data.code === 'PAYMENT_PENDING') {
      return res.json({ ok: true, status: 'pending' });
    } else {
      // Update as failed if exists
      if (payment && payment.status === 'pending') {
        payment.status = 'failed';
        await payment.save();
      }
      return res.json({ ok: true, status: 'failed', error: data.message });
    }

  } catch (e) {
    console.error("PhonePe Status Error:", e);
    res.status(500).json({ ok: false, error: 'Internal Server Error' });
  }
});

// PhonePe Callback (S2S Webhook)
app.post('/api/phonepe/callback', async (req, res) => {
  try {
    const base64Response = req.body.response;
    if (!base64Response) {
      return res.status(400).send('Invalid callback');
    }

    const decoded = JSON.parse(Buffer.from(base64Response, 'base64').toString('utf-8'));
    const { code, merchantTransactionId, transactionId } = decoded;

    console.log(`[PhonePe Callback] ${merchantTransactionId} | Status: ${code}`);

    const payment = await Payment.findOne({
      $or: [{ transactionId: merchantTransactionId }, { merchantTransactionId }]
    });

    if (!payment) {
      console.error('[PhonePe Callback] Payment not found:', merchantTransactionId);
      return res.status(200).send('OK'); // Always return 200 to PhonePe
    }

    if (code === 'PAYMENT_SUCCESS' && payment.status !== 'success') {
      payment.status = 'success';
      payment.providerRefId = transactionId;
      await payment.save();

      // Credit Wallet
      const user = await User.findOne({ userId: payment.userId });
      if (user) {
        user.walletBalance += payment.amount;
        user.hasRecharged = true;
        await user.save();
        console.log(`[PhonePe Callback] Wallet Credited: ${user.name} +₹${payment.amount}`);

        // Notify Socket if online
        const sId = userSockets.get(user.userId);
        if (sId) {
          io.to(sId).emit('wallet-update', { balance: user.walletBalance });
          io.to(sId).emit('app-notification', { text: `✅ Recharge Successful! +₹${payment.amount}` });
        }
      }
    } else if (code !== 'PAYMENT_SUCCESS' && payment.status === 'pending') {
      payment.status = 'failed';
      await payment.save();
    }

    res.status(200).send('OK');

  } catch (e) {
    console.error("PhonePe Callback Error:", e);
    res.status(200).send('OK'); // Always return 200
  }
});

// ============================================================================
// MOBILE APP SPECIFIC ENDPOINTS (from mobileapp/server/server.js)
// ============================================================================

/**
 * Register user's FCM token
 * POST /register
 */
// [DEPRECATED] - Use the MongoDB /register endpoint at line 524
// app.post('/register', (req, res) => {
//   const { userId, fcmToken } = req.body;
//   if (!userId || typeof userId !== 'string' || !fcmToken || typeof fcmToken !== 'string') {
//     return res.status(400).json({ success: false, error: 'Invalid input' });
//   }
//   mobileTokenStore.set(userId, fcmToken);
//   console.log(`[Mobile] Registered: ${userId} → ${fcmToken.substring(0, 20)}...`);
//   res.json({ success: true, message: `User ${userId} registered successfully` });
// });

/**
 * List all registered users (for debugging)
 * GET /users
 */
app.get('/users', (req, res) => {
  const users = [];
  mobileTokenStore.forEach((token, userId) => {
    users.push({ userId, tokenPreview: `${token.substring(0, 15)}...` });
  });
  res.json({ count: users.length, users });
});

/**
 * Unregister a user
 * DELETE /unregister/:userId
 */
app.delete('/unregister/:userId', (req, res) => {
  const { userId } = req.params;
  if (mobileTokenStore.has(userId)) {
    mobileTokenStore.delete(userId);
    res.json({ success: true, message: `User ${userId} unregistered` });
  } else {
    res.status(404).json({ success: false, error: 'User not found' });
  }
});

/**
 * Initiate a call to a user
 * POST /call
 */
app.post('/call', async (req, res) => {
  const { callerId, calleeId, callerName } = req.body;

  if (!callerId || !calleeId) {
    return res.status(400).json({ success: false, error: 'Missing callerId or calleeId' });
  }

  // Check if Firebase is initialized
  // Check if Firebase is initialized
  if (!callApp) {
    console.error('[Mobile] Call App Firebase NOT initialized. Check firebase-service-account.json');
    return res.status(503).json({
      success: false,
      error: 'Push notification service unavailable (Server Config Error)',
      details: global.callAppInitError || 'Unknown initialization error' // Exposed for debugging
    });
  }

  // UPDATED: Look up from MongoDB (User collection)
  // const fcmToken = mobileTokenStore.get(calleeId);
  const user = await User.findOne({ userId: calleeId });
  const fcmToken = user ? user.fcmToken : null;

  if (!fcmToken) {
    return res.status(404).json({ success: false, error: 'User not online/registered' });
  }

  const callId = `call_${Date.now()}_${Math.random().toString(36).substring(7)}`;

  const message = {
    token: fcmToken,
    data: {
      type: 'INCOMING_CALL',
      callId: callId,
      callerId: callerId,
      callerName: callerName || callerId,
      timestamp: Date.now().toString()
    },
    android: {
      priority: 'high',
      directBootOk: true
    }
  };

  console.log(`[Mobile] Sending call: ${callerId} → ${calleeId} (callId: ${callId})`);

  try {
    const response = await callApp.messaging().send(message);
    console.log(`[Mobile] Call notification sent: ${response}`);
    res.json({ success: true, callId, message: 'Call sent' });
  } catch (error) {
    console.error('[Mobile] FCM Error:', error.message);
    if (error.code === 'messaging/invalid-registration-token' ||
      error.code === 'messaging/registration-token-not-registered') {
      // Remove invalid token from DB
      await User.updateOne({ userId: calleeId }, { $unset: { fcmToken: 1 } });
      console.log(`[Mobile] Invalid token removed for user ${calleeId}`);
    }
    // Return 500 only for actual sending errors, not config errors
    res.status(500).json({ success: false, error: error.message });
  }
});

const PORT = process.env.PORT || 3000;

if (require.main === module) {
  server.listen(PORT, () => {
    console.log(`Server running on http://0.0.0.0:${PORT}`);
  });
}

// Graceful shutdown - prevents port stuck issues
process.on('SIGTERM', () => {
  console.log('SIGTERM received, shutting down gracefully...');
  server.close(() => {
    console.log('Server closed');
    process.exit(0);
  });
});

process.on('SIGINT', () => {
  console.log('SIGINT received, shutting down gracefully...');
  server.close(() => {
    console.log('Server closed');
    process.exit(0);
  });
});

module.exports = { app, server, sendFcmV1Push };