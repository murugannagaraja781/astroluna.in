const io = require('socket.io-client');
const mongoose = require('mongoose');

const MONGO_URI = 'mongodb+srv://murugannagaraja781_db_user:NewLife2025@cluster0.tp2gekn.mongodb.net/astrofive';
const URL = 'http://localhost:3000';

const PairMonth = mongoose.model('PairMonthTest', new mongoose.Schema({
    pairId: String,
    yearMonth: String,
    currentSlab: Number,
    slabLockedAt: Number
}, { strict: false }), 'pairmonths');

async function runTest() {
    await mongoose.connect(MONGO_URI);
    console.log('DB Connected');

    const clientSocket = io(URL);
    const astroSocket = io(URL);

    try {
        const cPhone = '8000000001';
        const aPhone = '9000000001';

        // Helper to calc pairId
        // We need userIds first.
        const clientId = await register(clientSocket, cPhone);
        const astroId = await register(astroSocket, aPhone);
        const pairId = `${clientId}_${astroId}`;
        console.log(`Pair: ${pairId}`);

        // 1. Manually create a "Previous Month" record
        // e.g., '2024-01' or just 'PREV-MONTH' if logic allowed, but logic uses current Date.
        // The server uses: new Date().toISOString().slice(0, 7).
        // So to test "New Month" triggering, we must simply ensure NO record exists for the CURRENT month.
        // Or, we can modify the DB to simulate that we HAD a record for LAST month.

        const currentMonth = new Date().toISOString().slice(0, 7);
        const prevMonth = '2024-01'; // Surely past

        // Clean up current month if exists (to force creation)
        await PairMonth.deleteOne({ pairId, yearMonth: currentMonth });

        // Create Past Record with High Slab
        await PairMonth.create({
            pairId,
            clientId,
            astrologerId: astroId,
            yearMonth: prevMonth,
            currentSlab: 4,
            slabLockedAt: 1000
        });
        console.log(`Created Mock Past Record (${prevMonth}) with Slab 4.`);

        // 2. Connect Session
        const sess = await createSession(clientSocket, astroId);
        console.log('Session Created:', sess);

        astroSocket.emit('session-connect', { sessionId: sess });
        await sleep(200);
        clientSocket.emit('session-connect', { sessionId: sess });

        // Wait for logic to trigger (it happens on connect and saves to activeSessions)
        await sleep(2000);

        // 3. Verify New Record Created for Current Month
        const newRec = await PairMonth.findOne({ pairId, yearMonth: currentMonth });
        if (newRec) {
            console.log(`✅ New Month Record Found: ${newRec.yearMonth}`);
            if (newRec.currentSlab === 3 && newRec.slabLockedAt === 0) {
                console.log('✅ Slab Reset to 3 Verified.');
            } else {
                console.error('❌ Slab Reset Failed:', newRec);
            }
        } else {
            console.error('❌ New Month Record NOT Created.');
        }

        // 4. Verify Past Record Untouched
        const oldRec = await PairMonth.findOne({ pairId, yearMonth: prevMonth });
        if (oldRec && oldRec.currentSlab === 4) {
            console.log('✅ Historical Data Preserved.');
        } else {
            console.error('❌ Historical Data Corrupted.');
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
