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
        io.to(sessionId).emit('insufficient-balance', { sessionId });
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
    const session = activeSessions.get(sessionId);
    if (!session) return;

    try {
      const { clientId, astrologerId } = session;
      const endTime = Date.now();
      const duration = Math.floor((endTime - session.startedAt) / 1000);

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
    } catch (err) { console.error('[Session] Cleanup error:', err); }
  }

  return { processBillingCharge, endSessionRecord };
};
