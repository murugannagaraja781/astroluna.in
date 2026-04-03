const crypto = require('crypto');
const { Session, User, PairMonth, BillingLedger, Notification } = require('../models');

module.exports = function(io, state, shared) {
  const { 
    userSockets, socketToUser, userActiveSession, 
    activeSessions 
  } = state;

  const { logActivity, broadcastAstroUpdate, getOtherUserIdFromSession, sendFcmV1Push } = shared;

  async function processBillingCharge(sessionId, durationSeconds, minuteIndex, type) {
    const session = activeSessions.get(sessionId);
    if (!session) return;
    
    try {
      const astroId = session.astrologerId;
      const clientId = session.clientId;
      const astro = await User.findOne({ userId: astroId });
      const client = await User.findOne({ userId: clientId });

      if (!astro || !client) return;

      const ratePerMin = astro.price || 20;
      const totalCharge = ratePerMin; 
      const astroShare = Math.floor(totalCharge * 0.7); 
      const adminShare = totalCharge - astroShare;

      if (client.walletBalance < totalCharge) {
        console.log(`[Billing] Insufficient balance for ${clientId}.`);
        await endSessionRecord(sessionId, 'insufficient_funds');
        return;
      }

      await User.updateOne({ userId: clientId }, { $inc: { walletBalance: -totalCharge } });
      await User.updateOne({ userId: astroId }, { $inc: { walletBalance: astroShare, totalEarnings: astroShare } });

      await BillingLedger.create({
        billingId: crypto.randomUUID(), sessionId, minuteIndex,
        chargedToClient: totalCharge, creditedToAstrologer: astroShare,
        adminAmount: adminShare, reason: type, createdAt: new Date()
      });

      session.totalDeducted = (session.totalDeducted || 0) + totalCharge;
      session.totalEarned = (session.totalEarned || 0) + astroShare;

      io.to(clientId).emit('wallet-update', { balance: client.walletBalance - totalCharge });
      io.to(astroId).emit('wallet-update', { balance: astro.walletBalance + astroShare });

      console.log(`[Billing] Session ${sessionId}: Min ${minuteIndex} charged ₹${totalCharge}`);
    } catch (e) { console.error('[Billing] Charge error:', e); }
  }

  async function endSessionRecord(sessionId, reason = 'ended') {
    let session = activeSessions.get(sessionId);
    
    // If not in memory, try to at least clear isBusy flags for this session's participants from DB
    if (!session) {
      try {
        const dbSession = await Session.findOne({ sessionId });
        if (dbSession) {
          console.log(`[Session] Found stale session ${sessionId} in DB. Clearing busy flags.`);
          await User.updateMany(
            { userId: { $in: [dbSession.clientId, dbSession.astrologerId] } }, 
            { isBusy: false }
          );
          broadcastAstroUpdate();
          
          // Also cleanup userActiveSession if they point here
          if (userActiveSession.get(dbSession.clientId) === sessionId) userActiveSession.delete(dbSession.clientId);
          if (userActiveSession.get(dbSession.astrologerId) === sessionId) userActiveSession.delete(dbSession.astrologerId);
        }
      } catch (e) { console.error('[Session] Stale cleanup error:', e); }
      return;
    }

    try {
      const { clientId, astrologerId } = session;
      const endTime = Date.now();
      const duration = Math.floor((endTime - (session.actualBillingStart || session.startedAt)) / 1000);

      await Session.updateOne({ sessionId }, {
        status: 'ended', sessionEndAt: endTime, duration: duration,
        totalEarned: session.totalEarned || 0, totalDeducted: session.totalDeducted || 0
      });

      activeSessions.delete(sessionId);
      userActiveSession.delete(clientId);
      userActiveSession.delete(astrologerId);

      await User.updateMany({ userId: { $in: [clientId, astrologerId] } }, { isBusy: false });
      broadcastAstroUpdate();

      io.to(sessionId).emit('session-ended', { 
        sessionId, reason, summary: { duration, deducted: session.totalDeducted || 0, earned: session.totalEarned || 0 }
      });
      console.log(`[Session] Cleaned up ${sessionId}. Duration: ${duration}s`);

      // Post-call notifications (FCM)
      const deducted = session.totalDeducted || 0;
      const earned = session.totalEarned || 0;

      if (deducted > 0) {
        const cUser = await User.findOne({ userId: clientId }, { fcmToken: 1 });
        if (cUser?.fcmToken) {
          sendFcmV1Push(cUser.fcmToken, { type: 'BILLING_DEBIT' }, {
            title: 'Wallet Debited',
            body: `₹${deducted} debited for your recent consultation.`,
            color: '#FF0000' // Red hint
          });
        }
      }

      if (earned > 0) {
        const aUser = await User.findOne({ userId: astrologerId }, { fcmToken: 1 });
        if (aUser?.fcmToken) {
          sendFcmV1Push(aUser.fcmToken, { type: 'BILLING_CREDIT' }, {
            title: 'Wallet Credited',
            body: `₹${earned} credited for your recent consultation.`,
            color: '#008000' // Green hint
          });
        }
      }

      // reliable CALL_ENDED signal (FCM fallback)
      [clientId, astrologerId].forEach(async (uid) => {
        const u = await User.findOne({ userId: uid }, { fcmToken: 1 });
        if (u?.fcmToken) {
           sendFcmV1Push(u.fcmToken, { type: 'CALL_ENDED', sessionId }, null);
           console.log(`[FCM] Sent CALL_ENDED to ${uid}`);
        }
      });
    } catch (err) { console.error('[Session] Cleanup error:', err); }
  }

  return { processBillingCharge, endSessionRecord };
};
