const io = require('socket.io-client');
const mongoose = require('mongoose');

const MONGO_URI = 'mongodb+srv://murugannagaraja781_db_user:NewLife2025@cluster0.tp2gekn.mongodb.net/astrofive';
const URL = 'http://localhost:3000';

const PairMonthSchema = new mongoose.Schema({
    pairId: String,
    currentSlab: Number,
    slabLockedAt: Number
});
const PairMonth = mongoose.model('PairMonthTestSlab', PairMonthSchema, 'pairmonths');
// Ensure collection name matches default for 'PairMonth' model created in server.js which is likely 'pairmonths'

// Need to match server code collection name.
// server.js: const PairMonth = mongoose.model('PairMonth', PairMonthSchema);
// Mongoose default is lowercase plural 'pairmonths'.

async function runTest() {
    await mongoose.connect(MONGO_URI);
    console.log('DB Connected');

    const clientSocket = io(URL);
    const astroSocket = io(URL);

    try {
        // 1. Setup New Pair
        // Use random phone to ensure new pair
        const rand = Math.floor(Math.random() * 10000);
        const cPhone = `88000${rand}`;
        const aPhone = `99000${rand}`;

        const clientId = await register(clientSocket, cPhone);
        const astroId = await register(astroSocket, aPhone);
        console.log(`Pair: ${clientId} + ${astroId}`);

        // 2. Create Session (New Pair -> Slab 3)
        const sessionId = await createSession(clientSocket, astroId);
        console.log('Session Created:', sessionId);

        astroSocket.emit('session-connect', { sessionId });
        await sleep(200);
        clientSocket.emit('session-connect', { sessionId });

        console.log('Waiting for Init (4s)...');
        await sleep(4000); // 2s buffer + 2s tick

        // Check DB for Slab 3
        const pairId = `${clientId}_${astroId}`;
        let pm = await PairMonth.findOne({ pairId });
        if (pm) {
            console.log(`Initial Slab: ${pm.currentSlab} (Expected 3)`);
            if (pm.currentSlab === 3) console.log('✅ Default Slab 3 Verified.');
            else console.error('❌ Failed: Expected Slab 3.');
        } else {
            console.error('❌ Failed: PairMonth not created.');
        }

        // 3. Test Upgrade?
        // To test upgrade we need > 900s total.
        // Initial is 0.
        // We can manually hack the DB to set slabLockedAt = 895 and see if it upgrades to 4 after 6 seconds?
        console.log('Simulating accumulated time close to Slab 4 limit...');
        if (pm) {
            // Set to 905s (Slab 4 threshold is > 900? NO. 901-1200 is Slab 4.)
            // 0-300:1, 301-600:2, 601-900:3.
            // So > 900 is Slab 4.
            // Let's set slabLockedAt to 1195 (Close to Slab 4 max? No, we are AT Slab 3.)
            // Wait, if I set it to 1000, lookup says Slab 4.
            // But current is 3. Max(4, 3) = 4.
            // Let's create a NEW session or update this one?
            // Since session loaded `initialPairSeconds` at start, changing DB now won't affect running session's memory.
            // So we must END this session, update DB, start NEW session.
        }

        clientSocket.disconnect();
        astroSocket.disconnect();
        await sleep(2000);

        // Update DB to 1195 seconds (Slab 4 territory).
        // Actually, stored Slab should be 3 if we manually set seconds?
        // No, let's say we manually set seconds to 1195.
        // Next session loads 1195.
        // 1195 is Slab 4.
        // So next session should start at Slab 4? Or upgrade immediately?
        // getSlab(1195) -> 4.
        // Init logic: `activeSession.currentSlab` reads from DB.
        // If DB says Slab 3, but lockedAt 1195.
        // Ticker: total = 1195 + 1 = 1196 -> Slab 4.
        // Max(4, 3) -> 4. Upgrade!

        await PairMonth.updateOne({ pairId }, { slabLockedAt: 1195, currentSlab: 3 });
        console.log('Updated DB: lockedAt=1195, slab=3. Next session should trigger upgrade to 4.');

        // Reconnect & New Session
        clientSocket.connect();
        astroSocket.connect();
        await new Promise(r => clientSocket.once('connect', r));
        await new Promise(r => astroSocket.once('connect', r));

        await register(clientSocket, cPhone, clientId);
        await register(astroSocket, aPhone, astroId);

        const sess2 = await createSession(clientSocket, astroId);
        console.log('Session 2:', sess2);
        astroSocket.emit('session-connect', { sessionId: sess2 });
        await sleep(200);
        clientSocket.emit('session-connect', { sessionId: sess2 });

        console.log('Waiting for Ticks (6s)...');
        await sleep(6000);

        pm = await PairMonth.findOne({ pairId });
        console.log(`Updated Slab: ${pm.currentSlab} (Expected 4)`);
        if (pm.currentSlab === 4) console.log('✅ Slab Upgrade Checked.');
        else console.error('❌ Failed: Expected Slab 4.');

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
