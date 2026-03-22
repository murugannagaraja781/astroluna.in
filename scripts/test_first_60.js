const io = require('socket.io-client');
const mongoose = require('mongoose');

const MONGO_URI = 'mongodb+srv://murugannagaraja781_db_user:NewLife2025@cluster0.tp2gekn.mongodb.net/astrofive';
const URL = 'http://localhost:3000';

async function runTest() {
    await mongoose.connect(MONGO_URI);
    console.log('DB Connected for Validation');

    const BillingLedger = mongoose.model('BillingLedger', new mongoose.Schema({
        sessionId: String,
        minuteIndex: Number,
        chargedToClient: Number,
        creditedToAstrologer: Number,
        adminAmount: Number,
        reason: String
    }));

    const clientSocket = io(URL);
    const astroSocket = io(URL);

    try {
        // 1. Setup
        const clientId = await register(clientSocket, '8111111111');
        const astroId = await register(astroSocket, '9111111111');
        console.log('Registered IDs:', clientId, astroId);

        // --- Scenario A: Early Exit (Wait 5s, then End) ---
        console.log('\n--- Scenario A: Early Exit (<60s) ---');
        const sessA = await createSession(clientSocket, astroId);
        console.log('Session A:', sessA);

        astroSocket.emit('session-connect', { sessionId: sessA });
        await sleep(200);
        clientSocket.emit('session-connect', { sessionId: sessA });

        console.log('Waiting 5s...');
        await sleep(7000); // 2s buffer + 5s tick

        console.log('Ending Session A...');
        // Simulate end by disconnect or explicit answer-session(end)?
        // Usually 'answer-session' with accept=false or just disconnect.
        // In our code ‘disconnect’ triggers endSessionRecord automatically if session is active.

        // Let's use disconnect on client
        clientSocket.disconnect();

        // Wait for async DB write
        await sleep(3000);

        // Verify DB
        const ledgerA = await BillingLedger.findOne({ sessionId: sessA });
        if (ledgerA) {
            console.log('✅ Ledger Found for A:', ledgerA.toObject());
            if (ledgerA.reason === 'first_60_partial' && ledgerA.creditedToAstrologer === 0 && ledgerA.adminAmount > 0) {
                console.log('✅ Scenario A Passed: Pro-rata Admin charge applied.');
            } else {
                console.error('❌ Scenario A Failed: Incorrect ledger values.');
            }
        } else {
            console.error('❌ Scenario A Failed: No ledger entry found.');
        }

        // Reconnect client for next test
        clientSocket.connect();
        await new Promise(r => clientSocket.once('connect', r));
        await register(clientSocket, '8111111111', clientId);

        // --- Scenario B: Full Minute (Wait 62s) ---
        console.log('\n--- Scenario B: Full Minute (60s) ---');
        // Note: Waiting 60s is long for a test script.
        // Ideally we mock time or set interval shorter.
        // For this specific mandatory verified task, I will wait the 60s to be absolutely sure.
        // OR I can temporarily hack the server to tick faster.
        // I already modified server.js.
        // I will wait 65s.

        const sessB = await createSession(clientSocket, astroId);
        console.log('Session B:', sessB);

        astroSocket.emit('session-connect', { sessionId: sessB });
        await sleep(200);
        clientSocket.emit('session-connect', { sessionId: sessB });

        console.log('Waiting 65s for full minute tick...');
        // We print a dot every 5s to keep alive
        for (let i = 0; i < 13; i++) {
            process.stdout.write('.');
            await sleep(5000);
        }
        console.log('\nTime up.');

        const ledgerB = await BillingLedger.findOne({ sessionId: sessB });
        if (ledgerB) {
            console.log('✅ Ledger Found for B:', ledgerB.toObject());
            if (ledgerB.reason === 'first_60' && ledgerB.creditedToAstrologer === 0 && ledgerB.adminAmount > 0) {
                console.log('✅ Scenario B Passed: Full minute Admin charge applied.');
            } else {
                console.error('❌ Scenario B Failed: Incorrect ledger values.');
            }
        } else {
            console.error('❌ Scenario B Failed: No ledger entry found.');
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
