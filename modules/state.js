// modules/state.js
const userSockets = new Map(); // userId -> socketId
const socketToUser = new Map(); // socketId -> userId
const userActiveSession = new Map(); // userId -> sessionId
const activeSessions = new Map(); // sessionId -> { type, users... }
const pendingMessages = new Map();
const otpStore = new Map();

// Presence / Disconnect Grace Periods
const offlineTimeouts = new Map(); // userId -> timeoutId
const savedAstroStatus = new Map(); // userId -> { chat, audio, video, timestamp }
const sessionDisconnectTimeouts = new Map(); // userId -> timeoutId

module.exports = {
  userSockets,
  socketToUser,
  userActiveSession,
  activeSessions,
  pendingMessages,
  otpStore,
  offlineTimeouts,
  savedAstroStatus,
  sessionDisconnectTimeouts
};
