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
        // Use fresh pair to be clean (or reuse)
        // We expect Slab 3 start.
        // Test: 70s duration.
        // 0-60: First 60 (Admin)
        // 60-70: 10s fraction -> Rounded (Admin)

        const clientId = await register(clientSocket, '8000000005');
        const astroId = await register(astroSocket, '9000000005');
        console.log(`Pair: ${clientId} + ${astroId}`);

        // Create Session
        const sess = await createSession(clientSocket, astroId);
        console.log('Session:', sess);

        // Connect
        astroSocket.emit('session-connect', { sessionId: sess });
        await sleep(200);
        clientSocket.emit('session-connect', { sessionId: sess });

        console.log('Waiting 75s (60s tick + 15s fraction)...');
        // Tick 1 (60s) should happen. then we wait a bit more and disconnect.

        // 60s
        for (let i = 0; i < 12; i++) {
            process.stdout.write('.');
            await sleep(5000);
        }
        console.log('\nMinute 1 Passed.');

        // 15s more
        await sleep(15000);
        console.log('\nDisconnecting to trigger end...');

        clientSocket.disconnect();

        await sleep(3000); // Wait for DB write

        // Verify
        const entries = await BillingLedger.find({ sessionId: sess }).sort({ minuteIndex: 1 });
        console.log(`Found ${entries.length} Ledger Entries.`);

        if (entries.length >= 2) {
            const l1 = entries[0];
            const l2 = entries[1];

            console.log('Entry 1:', l1.reason, 'Admin:', l1.adminAmount, 'Astro:', l1.creditedToAstrologer);
            console.log('Entry 2:', l2.reason, 'Admin:', l2.adminAmount, 'Astro:', l2.creditedToAstrologer);

            const passed1 = l1.reason === 'first_60' && l1.creditedToAstrologer === 0;
            const passed2 = l2.reason === 'rounded' && l2.creditedToAstrologer === 0 && l2.adminAmount > 0;

            if (passed1 && passed2) {
                console.log('✅ Phase 6 Verified: first_60 and rounded logic correct.');
            } else {
                console.error('❌ Phase 6 Failed: Logic mismatch.');
            }
        } else {
            console.error('❌ Phase 6 Failed: Missing entries. (Expected 2)');
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
