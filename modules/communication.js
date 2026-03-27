const crypto = require('crypto');
const { User, Session, Notification, PairMonth, BillingLedger, ChatMessage } = require('../models');

module.exports = function(io, shared) {
  const { 
    userSockets, socketToUser, userActiveSession, 
    activeSessions, pendingMessages, otpStore,
    offlineTimeouts, savedAstroStatus, sessionDisconnectTimeouts,
    ICE_SERVERS, logActivity, broadcastAstroUpdate,
    endSessionRecord, processBillingCharge, sendFcmV1Push,
    getOtherUserIdFromSession
  } = shared;

  // Cleanup helper for Ack
  function safeAck(cb, data) {
    if (typeof cb === 'function') cb(data);
  }

  io.on('connection', (socket) => {
    logActivity('socket', `New connection: ${socket.id}`);

    // --- Register user ---
    socket.on('register', (data, cb) => {
      try {
        const { phone, userId: providedUserId } = data || {};
        const userId = providedUserId || socketToUser.get(socket.id);

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

          const resolvedUserId = user.userId;
          userSockets.set(resolvedUserId, socket.id);
          socketToUser.set(socket.id, resolvedUserId);
          socket.join(resolvedUserId); 

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

          // Cancel pending SESSION timeout
          if (sessionDisconnectTimeouts.has(resolvedUserId)) {
            clearTimeout(sessionDisconnectTimeouts.get(resolvedUserId));
            sessionDisconnectTimeouts.delete(resolvedUserId);
            console.log(`[Session] Cancelled disconnect timeout for ${user.name} (reconnected in time!)`);
          }

          if (user.role === 'astrologer') {
            if (offlineTimeouts.has(resolvedUserId)) {
              clearTimeout(offlineTimeouts.get(resolvedUserId));
              offlineTimeouts.delete(resolvedUserId);
            }
            broadcastAstroUpdate();
          }
          
          if (user.role === 'superadmin') {
            socket.join('superadmin');
          }
        });
      } catch (err) {
        console.error('register error', err);
        safeAck(cb, { ok: false, error: 'Internal error' });
      }
    });

    // --- Rejoin Session ---
    socket.on('rejoin-session', (data) => {
      try {
        const { sessionId } = data || {};
        const userId = socketToUser.get(socket.id);
        if (sessionId && userId) {
          socket.join(sessionId);
          socket.to(sessionId).emit('peer-reconnected', { userId });
          logActivity('session', 'User rejoined session', { userId, sessionId });
        }
      } catch (err) { console.error('rejoin-session error', err); }
    });

    // --- Get Astrologers List ---
    socket.on('get-astrologers', async (cb) => {
      try {
        const astros = await User.find({ role: 'astrologer' });
        socket.emit('astrologer-update', astros);
        safeAck(cb, { astrologers: astros });
      } catch (e) { safeAck(cb, { astrologers: [] }); }
    });

    // --- Toggle Status (Generic) ---
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
          if (data.type === 'online') {
            user.lastSeen = new Date();
            user.isOnline = user.isChatOnline || user.isAudioOnline || user.isVideoOnline;
            user.isAvailable = user.isOnline;
          } else {
            Object.assign(user, update);
            user.isOnline = user.isChatOnline || user.isAudioOnline || user.isVideoOnline;
            user.isAvailable = user.isOnline;
            user.lastSeen = new Date();
          }
          await user.save();
          broadcastAstroUpdate();
        }
      } catch (e) { console.error(e); }
    });

    // --- Update Service Status ---
    socket.on('update-service-status', async (data) => {
      const userId = data.userId || socketToUser.get(socket.id);
      if (!userId) return;
      try {
        const update = {};
        const isEnabled = !!data.isEnabled;
        if (data.service === 'chat') update.isChatOnline = isEnabled;
        if (data.service === 'call') update.isAudioOnline = isEnabled;
        if (data.service === 'video') update.isVideoOnline = isEnabled;

        let user = await User.findOne({ userId });
        if (user) {
          Object.assign(user, update);
          user.isOnline = user.isAvailable; // Follow master available toggle
          user.lastSeen = new Date();
          await user.save();
          broadcastAstroUpdate();
        }
      } catch (e) { console.error(e); }
    });

    // --- Request Session ---
    socket.on('request-session', async (data, cb) => {
      logActivity('session', 'New session request', data);
      try {
        const { toUserId, type, birthData } = data || {};
        const fromUserId = socketToUser.get(socket.id);
        if (!fromUserId) return safeAck(cb, { ok: false, error: 'You are not registered (send register first)' });
        if (!toUserId || !type) return safeAck(cb, { ok: false, error: 'Missing target user or session type' });

        const toUser = await User.findOne({ userId: toUserId });
        const fromUser = await User.findOne({ userId: fromUserId });

        if (!toUser) {
           console.error(`[Signal] Session failed: Target user ${toUserId} does not exist in database`);
           return safeAck(cb, { ok: false, error: 'Target user not found in database' });
        }

        if (userActiveSession.has(toUserId)) {
          const sid = userActiveSession.get(toUserId);
          if (activeSessions.has(sid)) return safeAck(cb, { ok: false, error: 'User busy' });
          userActiveSession.delete(toUserId);
        }

        const sessionId = crypto.randomUUID();
        let clientId = fromUser.role === 'client' ? fromUserId : toUserId;
        let astrologerId = fromUser.role === 'astrologer' ? fromUserId : toUserId;

        await Session.create({ sessionId, fromUserId, toUserId, type, startTime: Date.now(), clientId, astrologerId });
        
        activeSessions.set(sessionId, {
          type, users: [fromUserId, toUserId], startedAt: Date.now(),
          clientId, astrologerId, elapsedBillableSeconds: 0, status: 'ringing'
        });
        userActiveSession.set(fromUserId, sessionId);
        userActiveSession.set(toUserId, sessionId);
        socket.join(sessionId); // Join room immediately after creation

        const callPayload = {
          sessionId, fromUserId, callerName: fromUser.name || 'Client',
          type, birthData: birthData || null, iceServers: ICE_SERVERS
        };

        io.to(toUserId).emit('incoming-session', callPayload);

        if (toUser.fcmToken) {
          const fcmData = {
            type: 'INCOMING_CALL', sessionId, callType: type,
            callerName: fromUser.name || 'Client', callerId: fromUserId,
            birthData: JSON.stringify(birthData || {}),
            iceServers: JSON.stringify(ICE_SERVERS)
          };
          sendFcmV1Push(toUser.fcmToken, fcmData, { title: '📞 Incoming Call', body: `${fromUser.name} is calling you` });
        }

        safeAck(cb, { ok: true, sessionId, iceServers: ICE_SERVERS });

        // Missed call timeout
        setTimeout(async () => {
          const s = activeSessions.get(sessionId);
            if (s && s.status === 'ringing') {
            io.to(fromUserId).emit('session-ended', { sessionId, reason: 'no_answer' });
            io.to(toUserId).emit('session-ended', { sessionId, reason: 'missed' });
            userActiveSession.delete(fromUserId);
            userActiveSession.delete(toUserId);
            activeSessions.delete(sessionId);
            await Session.updateOne({ sessionId }, { status: 'missed', endTime: Date.now() });
            
            // Item 9: If astrologer misses call, set them offline
            const target = await User.findOne({ userId: toUserId });
            if (target && target.role === 'astrologer') {
              console.log(`[Status] Setting ${toUserId} offline due to missed call.`);
              await User.updateOne({ userId: toUserId }, { isAvailable: false, isOnline: false });
              broadcastAstroUpdate();
            }
          }
        }, 30000);
      } catch (err) {
        console.error('request-session error', err);
        safeAck(cb, { ok: false, error: 'Server error' });
      }
    });

    // --- Save Intake Details ---
    socket.on('save-intake-details', async (data, cb) => {
      const userId = socketToUser.get(socket.id);
      if (!userId) return;
      try {
        const u = await User.findOne({ userId });
        if (u) {
          u.birthDetails = {
            dob: `${data.year}-${String(data.month).padStart(2, '0')}-${String(data.day).padStart(2, '0')}`,
            tob: `${String(data.hour).padStart(2, '0')}:${String(data.minute).padStart(2, '0')}`,
            pob: data.city, lat: data.latitude, lon: data.longitude
          };
          u.name = data.name;
          u.intakeDetails = { gender: data.gender, marital: data.marital, occupation: data.occupation, topic: data.topic, partner: data.partner };
          await u.save();
          safeAck(cb, { ok: true });

          const sessionId = userActiveSession.get(userId);
          if (sessionId) {
            const partnerId = getOtherUserIdFromSession(sessionId, userId);
            if (partnerId) io.to(partnerId).emit('client-birth-chart', { sessionId, fromUserId: userId, birthData: data });
          }
        }
      } catch (e) { console.error(e); }
    });

    // --- Answer Session (Web) ---
    socket.on('answer-session', (data) => {
      const { sessionId, toUserId, accept } = data || {};
      const fromUserId = socketToUser.get(socket.id);
      if (!fromUserId || !sessionId || !toUserId) return;
      socket.join(sessionId); // Callee joins room upon answering

      if (!accept) {
        endSessionRecord(sessionId);
      } else {
        const session = activeSessions.get(sessionId);
        if (session) {
          session.status = 'active';
          session.actualBillingStart = Date.now(); // Start per-minute billing
          
          User.findOne({ userId: session.astrologerId }).then(async (astro) => {
             if (astro) {
               await User.updateOne({ userId: session.astrologerId }, { isBusy: true });
               broadcastAstroUpdate();
               
               // ITEM 5: Emit billing-started with wallet/available time
               const client = await User.findOne({ userId: session.clientId });
               if (client) {
                 const rate = astro.price || 20;
                 const mins = Math.floor(client.walletBalance / rate);
                 const info = {
                   startTime: session.actualBillingStart,
                   clientBalance: client.walletBalance,
                   ratePerMinute: rate,
                   availableMinutes: mins
                 };
                 io.to(session.clientId).emit('billing-started', info);
                 io.to(session.astrologerId).emit('billing-started', info);
                 console.log(`[Billing] Session ${sessionId} started. Available: ${mins} mins`);
               }
             }
          });
        }
      }
      io.to(toUserId).emit('session-answered', { sessionId, fromUserId, accept: !!accept, iceServers: ICE_SERVERS });
    });

    // --- Answer Session Native ---
    socket.on('answer-session-native', async (data, cb) => {
      try {
        const { sessionId, accept, callType } = data || {};
        const astrologerId = socketToUser.get(socket.id);
        if (!astrologerId || !sessionId) return safeAck(cb, { ok: false, error: 'Invalid data' });
        socket.join(sessionId); // Callee joins room upon answering (Native)

        const session = activeSessions.get(sessionId);
        if (!session) {
          const dbSession = await Session.findOne({ sessionId });
          if (!dbSession) return safeAck(cb, { ok: false, error: 'Session not found' });
          const fromUserId = dbSession.fromUserId;
          if (accept) {
            User.updateOne({ userId: astrologerId }, { isBusy: true }).then(() => broadcastAstroUpdate());
            io.to(fromUserId).emit('session-answered', { sessionId, fromUserId: astrologerId, type: callType || dbSession.type, accept: true, iceServers: ICE_SERVERS });
            safeAck(cb, { ok: true, fromUserId, iceServers: ICE_SERVERS });
          } else {
            io.to(fromUserId).emit('session-answered', { sessionId, fromUserId: astrologerId, accept: false });
            endSessionRecord(sessionId);
            safeAck(cb, { ok: true });
          }
          return;
        }

        const fromUserId = session.users.find(u => u !== astrologerId);
        if (accept) {
          if (session.status === 'active') return safeAck(cb, { ok: true, fromUserId });
          session.status = 'active';
          session.actualBillingStart = Date.now(); // Start per-minute billing

          User.findOne({ userId: astrologerId }).then(async (astro) => {
            if (astro) {
              await User.updateOne({ userId: astrologerId }, { isBusy: true });
              broadcastAstroUpdate();

              // ITEM 5: Push billing info
              const client = await User.findOne({ userId: session.clientId });
              if (client) {
                const rate = astro.price || 20;
                const mins = Math.floor(client.walletBalance / rate);
                const info = {
                  startTime: session.actualBillingStart,
                  clientBalance: client.walletBalance,
                  ratePerMinute: rate,
                  availableMinutes: mins
                };
                io.to(session.clientId).emit('billing-started', info);
                io.to(session.astrologerId).emit('billing-started', info);
                console.log(`[Billing] Session ${sessionId} started (Native). Available: ${mins} mins`);
              }
            }
          });
          io.to(fromUserId).emit('session-answered', { sessionId, fromUserId: astrologerId, type: callType || session.type, accept: true, iceServers: ICE_SERVERS });
          console.log(`[Signal] ${astrologerId} answered ${sessionId} (Native). Peer: ${fromUserId}`);
          safeAck(cb, { ok: true, fromUserId, iceServers: ICE_SERVERS });
        } else {
          io.to(fromUserId).emit('session-answered', { sessionId, fromUserId: astrologerId, accept: false });
          endSessionRecord(sessionId);
          safeAck(cb, { ok: true });
        }
      } catch (err) { safeAck(cb, { ok: false, error: 'Server error' }); }
    });

    // --- Signaling Relay ---
    socket.on('signal', async (data) => {
      let { sessionId, toUserId, signal } = data || {};
      const fromUserId = socketToUser.get(socket.id);
      if (!fromUserId || !sessionId || !signal) return;

      // Fallback: If toUserId is missing or unknown, find it from the session
      if (!toUserId || toUserId === 'Unknown') {
        const session = activeSessions.get(sessionId) || await Session.findOne({ sessionId });
        if (session) {
          const users = session.users || [session.fromUserId, session.toUserId];
          toUserId = users.find(u => u && u !== fromUserId);
        }
      }

      if (!toUserId || toUserId === 'Unknown') {
        console.warn(`[Signal] Cannot relay signal: Target user Unknown for session ${sessionId}`);
        return;
      }

      const type = signal.type || (signal.candidate ? 'ice-candidate' : 'unknown');
      console.log(`[Signal] Relay ${type} from ${fromUserId} to ${toUserId} | Session: ${sessionId}`);
      
      io.to(toUserId).emit('signal', { sessionId, fromUserId, signal });
    });

    // --- ITEM 4: Message Status Relay (Double Tick) ---
    socket.on('message-status', (data) => {
      const { toUserId, messageId, status, sessionId } = data || {};
      if (!toUserId || !messageId || !status) return;
      io.to(toUserId).emit('message-status', { messageId, status, sessionId });
      console.log(`[Chat] Relay status ${status} for ${messageId} to ${toUserId}`);
    });

    socket.on('client-birth-chart', (data) => {
      const { sessionId, toUserId, birthData } = data || {};
      if (sessionId && toUserId && birthData) {
        io.to(toUserId).emit('client-birth-chart', { sessionId, birthData });
        console.log(`[Signal] Relay client-birth-chart to ${toUserId} for session ${sessionId}`);
      }
    });

    // --- ITEM 8: Logout Handler ---
    socket.on('logout', async (data) => {
      const userId = data?.userId || socketToUser.get(socket.id);
      if (userId) {
        console.log(`[Status] Explicit logout for ${userId}`);
        await User.updateOne({ userId }, { isOnline: false, isAvailable: false, isBusy: false });
        broadcastAstroUpdate();
      }
    });


    // --- Session Connect (Room Join) ---
    socket.on('session-connect', async (data, cb) => {
      try {
        const { sessionId } = data || {};
        if (sessionId) {
          socket.join(sessionId);
          logActivity('session', `Socket ${socket.id} joined room ${sessionId}`);
          
          let toUserId;
          const session = activeSessions.get(sessionId) || await Session.findOne({ sessionId });
          if (session) {
             const fromUserId = socketToUser.get(socket.id);
             const users = session.users || [session.fromUserId, session.toUserId];
             toUserId = users.find(u => u && u !== fromUserId);
          }
          
          safeAck(cb, { ok: true, iceServers: ICE_SERVERS, toUserId });
        } else {
          safeAck(cb, { ok: false, error: 'No sessionId' });
        }
      } catch (err) { console.error('session-connect error', err); }
    });

    // --- End Session ---
    socket.on('end-session', async (data) => {
      const { sessionId } = data || {};
      if (sessionId) endSessionRecord(sessionId);
    });

    // --- Chat Message ---
    socket.on('chat-message', async (data) => {
      try {
        const { toUserId, sessionId, content, messageId } = data || {};
        const fromUserId = socketToUser.get(socket.id);
        if (!fromUserId || !toUserId || !content || !messageId) return;

        socket.emit('message-status', { messageId, status: 'sent' });

        ChatMessage.create({ messageId, sessionId, fromUserId, toUserId, text: content.text, timestamp: Date.now() });

        io.to(toUserId).emit('chat-message', { fromUserId, content, sessionId, timestamp: Date.now(), messageId });

        // Push notification
        const toUser = await User.findOne({ userId: toUserId });
        if (toUser && toUser.fcmToken) {
          const payload = { type: 'CHAT_MESSAGE', sessionId: sessionId || '', text: content.text, messageId, timestamp: Date.now().toString() };
          sendFcmV1Push(toUser.fcmToken, payload, null);
        }
      } catch (err) { console.error('chat-message error', err); }
    });

    // --- Super Admin Handlers ---
    socket.on('admin-get-ledger-stats', async (data, cb) => {
      const fromUserId = socketToUser.get(socket.id);
      const user = await User.findOne({ userId: fromUserId });
      if (!user || user.role !== 'superadmin') return safeAck(cb, { ok: false, error: 'Unauthorized' });

      try {
        const ledger = await BillingLedger.find();
        const stats = {
          totalRevenue: 0,
          adminProfit: 0,
          astroPayout: 0,
          totalDuration: 0,
          totalUsers: await User.countDocuments({ role: { $ne: 'superadmin' } }),
          activeSessions: activeSessions.size
        };

        ledger.forEach(l => {
          stats.totalRevenue += (l.chargedToClient || 0);
          stats.adminProfit += (l.adminAmount || 0);
          stats.astroPayout += (l.creditedToAstrologer || 0);
          // If we want minutes, we'd sum durations, but here we can approximate from charged amounts or add a duration field to ledger
          // For now, let's just sum the credited amounts
        });

        // Sum actual sessions duration from DB sessions
        const allSessions = await Session.find({ status: 'completed' });
        allSessions.forEach(s => {
          stats.totalDuration += (s.duration || 0);
        });

        safeAck(cb, { ok: true, stats, fullLedger: ledger });
      } catch (e) {
        console.error('admin-get-ledger-stats error', e);
        safeAck(cb, { ok: false, error: 'Server error' });
      }
    });

    socket.on('admin-update-user-details', async (data, cb) => {
      const fromUserId = socketToUser.get(socket.id);
      const admin = await User.findOne({ userId: fromUserId });
      if (!admin || admin.role !== 'superadmin') return safeAck(cb, { ok: false, error: 'Unauthorized' });

      try {
        const { userId, updates } = data;
        await User.updateOne({ userId }, updates);
        logActivity('admin', `User ${userId} details updated by admin`, updates);
        safeAck(cb, { ok: true });
        
        // Broadcast update to all astrologers if it's an astro update
        broadcastAstroUpdate();
      } catch (e) {
        console.error('admin-update-user-details error', e);
        safeAck(cb, { ok: false, error: 'Server error' });
      }
    });

    // --- System Log Handler ---
    socket.on('admin-get-logs', async (data, cb) => {
      const fromUserId = socketToUser.get(socket.id);
      const admin = await User.findOne({ userId: fromUserId });
      if (!admin || admin.role !== 'superadmin') return safeAck(cb, { ok: false, error: 'Unauthorized' });

      try {
          const logs = await fs.promises.readFile('activity.log', 'utf8').catch(() => '');
          safeAck(cb, { ok: true, logs });
      } catch (e) { safeAck(cb, { ok: false }); }
    });

    socket.on('disconnect', () => {
      const userId = socketToUser.get(socket.id);
      if (userId) {
        userSockets.delete(userId);
        socketToUser.delete(socket.id);
        logActivity('socket', `User disconnected: ${userId}`);
        
        User.findOne({ userId }).then(user => {
            if (user && user.role === 'astrologer') {
              const timeoutId = setTimeout(async () => {
                if (!userSockets.has(userId)) {
                  await User.updateOne({ userId }, { isOnline: false, isAvailable: false });
                  broadcastAstroUpdate();
                }
              }, 10000);
              offlineTimeouts.set(userId, timeoutId);
            }
        });
      }
    });
  });

  setInterval(() => {
    const now = Date.now();
    for (const [sessionId, session] of activeSessions) {
      if (!session.actualBillingStart || now < session.actualBillingStart) continue;
      session.elapsedBillableSeconds = (session.elapsedBillableSeconds || 0) + 1;
      if (session.elapsedBillableSeconds % 60 === 0) {
        processBillingCharge(sessionId, 60, session.elapsedBillableSeconds / 60, 'slab');
      }
    }
  }, 1000);
};
