const io = require('socket.io-client');
const fetch = require('node-fetch');
const mongoose = require('mongoose');

const MONGO_URI = 'mongodb+srv://murugannagaraja781_db_user:NewLife2025@cluster0.tp2gekn.mongodb.net/astrofive';
const URL = 'http://localhost:3000';

async function runTest() {
  await mongoose.connect(MONGO_URI);
  console.log('DB Connected');
  // Need to clear user balance or create new user
  // Let's create new user 'LowBalanceUser'
  
  const clientSocket = io(URL);
  const astroSocket = io(URL);
  
  try {
    const cPhone = '8000000007';
    const aPhone = '9000000007';
    
    const clientId = await register(clientSocket, cPhone);
    const astroId = await register(astroSocket, aPhone);
    console.log(`Pair: ${clientId} + ${astroId}`);
    
    // Set Wallet Balance to allow 1 minute but fail 2nd minute
    // Price? Assuming Astrologer default price is set in DB? 
    // We didn't set price in test scripts, relying on seed or default. 
    // Let's force set price and balance via Mongo directly to be safe.
    
    const User = mongoose.model('UserVarTest', new mongoose.Schema({ userId: String, name: String, phone: String, role: String, walletBalance: Number, price: Number }, { strict: false }), 'users');
    
    const PRICE = 10;
    await User.updateOne({ userId: astroId }, { price: PRICE }); // Set Astro Price to 10
    await User.updateOne({ userId: clientId }, { walletBalance: 15 }); // Client has 15 (Enough for 1 min (10), not 2 (20))
    console.log(`Set Price=${PRICE}, ClientBal=15. Predicting End at Min 2.`);

    const sess = await createSession(clientSocket, astroId);
    console.log('Session:', sess);
    
    astroSocket.emit('session-connect', { sessionId: sess });
    await sleep(200);
    clientSocket.emit('session-connect', { sessionId: sess });
    
    // Wait for Min 1 (60s) -> Should succeed (Bal 15 -> 5)
    console.log('Waiting for Minute 1 (62s)...');
    for(let i=0; i<13; i++) {
        process.stdout.write('.');
        await sleep(5000);
    }
    console.log('\nMinute 1 Passed.');
    
    // Check Status? 
    // Proceed to Min 2 (120s) -> Should Fail (Need 10, Have 5) -> Disconnect
    console.log('Waiting for Minute 2 (60s)...');
    
    // Listen for disconnect or session-ended
    let output = '';
    clientSocket.on('session-ended', (d) => { output = d.reason; console.log('\nReceived session-ended:', d); });
    
    for(let i=0; i<13; i++) {
        process.stdout.write('.');
        await sleep(5000);
        if (output) break;
    }
    
    if (output === 'insufficient_funds') {
        console.log('✅ Session correctly ended due to insufficient funds.');
    } else {
        console.error('❌ Failed to end session or wrong reason:', output);
    }
    
    // Forced end should trigger Rounding check?
    // We ended at exactly 120s (Min 2 boundary). 
    // So 120-60 = 60 eligible. 60/60 = 1 full. Remainder 0.
    // So NO rounding charge expected. 
    // Wait, the fail happened AT the tick of 120s. 
    // The tick logic: 
    //  processBillingCharge -> false -> endSessionRecord.
    //  endSessionRecord reads billableSeconds (120).
    //  120-60 = 60. 60%60 = 0. No rounding.
    // Correct behavior: User paid for 1 min. Astro talked for 2 mins?
    // User disconnected at start of min 2.
    // Actually, at 120s, Minute 2 IS completed. We are trying to pay for Minute 2.
    // If fail, user shouldn't have been allowed to talk?
    // But we are POST-paid per minute (Prepaid wallet, but charged at end of minute).
    // So user talked Min 2, then failed to pay.
    // Debt? 
    // Our logic currently just cuts them off. Admin/Astro lose that minute?
    // "If charge fails, disconnect immediately".
    // We might accept that loss or implement debt logic later. 
    // Rounded check: 
    // If we want to test rounding, we should fail at 130s?
    // But failing usually happens at minute boundary.
    // UNLESS we ran out of funds mid-minute? No, we check at tick boundaries (minutes).
    // So we usually fail at minute boundary.
    // Verification of "Overdraft" for rounding requires a session that ends manually with fraction and low balance.
    
    // Let's test "Overdraft Rounding" quickly.
    // Use the remaining 5 balance. 
    // Top up to 5? It is 5.
    // Start new session. Talk 10s.
    // Min 1 requires 10. We have 5.
    // Wait... Min 1 is "first_60". 
    // If we end at 10s -> "early_exit".
    // Charge is pro-rata: (10/60)*10 = 1.66.
    // We have 5. Success.
    
    // Valid Overdraft scenario:
    // Bal = 1. Price = 10.
    // Talk 10s "early_exit". Charge 1.6. Fails?
    // early_exit calls processBillingCharge. 
    // processBillingCharge returns false if fail?
    // But endSessionRecord doesn't check return value of processBillingCharge.
    // So it just fails?
    // "rounded" is the one we allowed overdraft for.
    // "early_exit" != "rounded".
    
    // Real "Rounded" scenario:
    // Talk 70s.
    // 60s charge (10) succeeds.
    // 10s fraction -> "rounded" charge (10).
    // If we have 15. 
    // 60s pays 10. Rem 5.
    // 70s rounding needs 10. Have 5.
    // Should OVERDRAFT to -5.
    
    console.log('\n--- Scenario 2: Overdraft Rounding ---');
    await User.updateOne({ userId: clientId }, { walletBalance: 15 }); // Reset to 15
    console.log('Reset Bal 15.');
    
    const sess2 = await createSession(clientSocket, astroId);
    astroSocket.emit('session-connect', { sessionId: sess2 });
    await sleep(200);
    clientSocket.emit('session-connect', { sessionId: sess2 });
    
    console.log('Waiting 75s...');
    await sleep(75000); // 60s + 15s
    clientSocket.disconnect();
    
    await sleep(2000);
    const uFinal = await User.findOne({ userId: clientId });
    console.log('Final Balance:', uFinal.walletBalance);
    // Exp: 15 - 10 (Min 1) - 10 (Rounding) = -5.
    if (uFinal.walletBalance === -5) {
        console.log('✅ Overdraft applied successfully.');
    } else {
        console.error('❌ Overdraft failed. Bal:', uFinal.walletBalance);
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
