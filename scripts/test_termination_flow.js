const io = require('socket.io-client');
const mongoose = require('mongoose');

const MONGO_URI = 'mongodb+srv://murugannagaraja781_db_user:NewLife2025@cluster0.tp2gekn.mongodb.net/astrofive';
const URL = 'http://localhost:3000';

const BillingLedger = mongoose.model('BillingLedger', new mongoose.Schema({
    sessionId: String,
    minuteIndex: Number,
    reason: String,
    adminAmount: Number
}));

const Session = mongoose.model('SessionTestTerm', new mongoose.Schema({
    sessionId: String,
    status: String,
    endTime: Number
}, { strict: false }), 'sessions');

async function runTest() {
    await mongoose.connect(MONGO_URI);
    console.log('DB Connected');

    const clientSocket = io(URL);
    const astroSocket = io(URL);

    try {
        const cPhone = '8000000008';
        const aPhone = '9000000008';

        const clientId = await register(clientSocket, cPhone);
        const astroId = await register(astroSocket, aPhone);
        console.log(`Pair: ${clientId} + ${astroId}`);

        // --- Scenario 1: explicit-end ---
        console.log('\n--- Scenario 1: explicit-end ---');
        const s1 = await createSession(clientSocket, astroId);
        astroSocket.emit('session-connect', { sessionId: s1 });
        await sleep(200);
        clientSocket.emit('session-connect', { sessionId: s1 });

        console.log('Waiting 65s (Min 1 + fraction)...');
        await sleep(65000);

        console.log('Sending end-session...');
        clientSocket.emit('session-ended', {
            sessionId: s1,
            toUserId: astroId,
            type: 'call',
            durationMs: 65000
        });

        // Wait for server to process
        await sleep(2000);

        // Verify DB
        const sess1 = await Session.findOne({ sessionId: s1 });
        // Note: server doesn't explicitly setting status=ended in session-ended handler?
        // It calls endSessionRecord.
        // endSessionRecord sets endTime.
        if (sess1.endTime) console.log('✅ Session 1 Marked Ended in DB.');
        else console.error('❌ Session 1 End Time Missing.');

        // Check Rounding
        const l1 = await BillingLedger.findOne({ sessionId: s1, reason: 'rounded' });
        if (l1) console.log('✅ Session 1 Rounding Applied.');
        else console.error('❌ Session 1 Rounding Missing.');


        // --- Scenario 2: disconnect-end ---
        console.log('\n--- Scenario 2: disconnect-end ---');
        const s2 = await createSession(clientSocket, astroId);
        astroSocket.emit('session-connect', { sessionId: s2 });
        await sleep(200);
        clientSocket.emit('session-connect', { sessionId: s2 });

        console.log('Waiting 65s...');
        await sleep(65000); // 60s + 5s fraction

        console.log('Client Disconnecting...');
        clientSocket.disconnect();

        await sleep(3000);

        const sess2 = await Session.findOne({ sessionId: s2 });
        if (sess2.endTime) console.log('✅ Session 2 Marked Ended (via Disconnect).');
        else console.error('❌ Session 2 End Time Missing.');

        const l2 = await BillingLedger.findOne({ sessionId: s2, reason: 'rounded' });
        if (l2) console.log('✅ Session 2 Rounding Applied.');
        else console.error('❌ Session 2 Rounding Missing.');

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
