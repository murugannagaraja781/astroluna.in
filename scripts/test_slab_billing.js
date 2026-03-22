const io = require('socket.io-client');
const mongoose = require('mongoose');

const MONGO_URI = 'mongodb+srv://murugannagaraja781_db_user:NewLife2025@cluster0.tp2gekn.mongodb.net/astrofive';
const URL = 'http://localhost:3000';

const BillingLedger = mongoose.model('BillingLedger', new mongoose.Schema({
  sessionId: String,
  minuteIndex: Number,
  chargedToClient: Number,
  creditedToAstrologer: Number,
  adminAmount: Number,
  reason: String
}));

async function runTest() {
  await mongoose.connect(MONGO_URI);
  console.log('DB Connected');
  
  const clientSocket = io(URL);
  const astroSocket = io(URL);
  
  try {
    // 1. Setup
    // Reuse specific numbers to get Slab 3 (or fresh if purged, defaults Slab 3)
    const cPhone = '8000000002'; // use a fresh seeded number if possible, or just reuse
    const aPhone = '9000000002'; 
    
    // We want to test Slab Billing. 
    // Assuming Start Slab = 3. 
    // Mins 1 -> Admin.
    // Mins 2 -> Slab 3 (40% Astro).
    
    const clientId = await register(clientSocket, cPhone);
    const astroId = await register(astroSocket, aPhone);
    console.log(`Pair: ${clientId} + ${astroId}`);
    
    const sess = await createSession(clientSocket, astroId);
    console.log('Session:', sess);
    
    astroSocket.emit('session-connect', { sessionId: sess });
    await sleep(200);
    clientSocket.emit('session-connect', { sessionId: sess });
    
    console.log('Waiting for Minute 1 tick (62s)...');
    // Using simple wait. Ideally we could speed up server time.
    // To save time, I will wait 62s.
    // Printing dots.
    for(let i=0; i<13; i++) {
      process.stdout.write('.');
      await sleep(5000);
    }
    console.log('\nMinute 1 Passed.');
    
    // Verify Min 1 Ledger
    let l1 = await BillingLedger.findOne({ sessionId: sess, minuteIndex: 1 });
    if (l1 && l1.reason === 'first_60' && l1.creditedToAstrologer === 0) {
      console.log('✅ Minute 1 Billing Correct (100% Admin).');
    } else {
      console.error('❌ Minute 1 Failed.', l1);
    }
    
    console.log('Waiting for Minute 2 tick (60s)...');
    for(let i=0; i<12; i++) {
        process.stdout.write('.');
        await sleep(5000);
    }
    console.log('\nMinute 2 Passed.');
    
    // Verify Min 2 Ledger
    let l2 = await BillingLedger.findOne({ sessionId: sess, minuteIndex: 2 });
    if (l2) {
      console.log('Ledger 2:', l2.toObject());
      // Expect reason 'slab_3' (default)
      // Expect astro share > 0. (Slab 3 is 40%)
      if (l2.reason.startsWith('slab') && l2.creditedToAstrologer > 0) {
        console.log(`✅ Minute 2 Billing Verified. Astro got ${l2.creditedToAstrologer} (${l2.creditedToAstrologer/l2.chargedToClient*100}%)`);
      } else {
        console.error('❌ Minute 2 Billing Failed (Astro Share 0?)');
      }
    } else {
      console.error('❌ Minute 2 Ledger Missing.');
    }

  } catch (e) {
    console.error(e);
  } finally {
    if (clientSocket.connected) clientSocket.disconnect();
    if (astroSocket.connected) astroSocket.disconnect();
    await mongoose.connection.close();
    process.exit();
  }
}

function register(socket, phone, existingUserId) {
  return new Promise((resolve, reject) => {
    socket.emit('register', { phone, existingUserId }, (res) => {
      if (res.ok) resolve(res.userId);
      else reject(res.error);
    });
  });
}

function createSession(socket, toUserId) {
  return new Promise((resolve, reject) => {
    socket.emit('request-session', { toUserId, type: 'chat' }, (res) => {
       if (res.ok) resolve(res.sessionId);
       else reject(res.error);
    });
  });
}

function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }

runTest();
