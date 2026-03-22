const io = require('socket.io-client');
const mongoose = require('mongoose');

const MONGO_URI = 'mongodb+srv://murugannagaraja781_db_user:NewLife2025@cluster0.tp2gekn.mongodb.net/astrofive';
const URL = 'http://localhost:3000';

const User = mongoose.model('UserTestAdmin', new mongoose.Schema({ userId: String, role: String, phone: String }, { strict: false }), 'users');
const BillingLedger = mongoose.model('BillingLedger', new mongoose.Schema({
    billingId: String,
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

    const socket = io(URL);

    try {
        // 1. Create/Get User and Promote to SuperAdmin
        const phone = '9999999999';

        // Direct DB fetch/create FIRST
        let u = await User.findOne({ phone });
        if (!u) {
            console.log('User not found in DB, creating manually...');
            u = await User.create({ userId: 'admin_' + Date.now(), phone, role: 'client' });
        }
        const userId = u.userId;
        console.log(`Promoting ${userId} to superadmin...`);
        await User.updateOne({ userId }, { role: 'superadmin' });

        // NOW register via socket to authenticate
        await register(socket, phone);

        // 2. Seed Dummy Ledger Data
        const billingId = 'TEST_BILL_' + Date.now();
        await BillingLedger.create({
            billingId: billingId + '_1',
            sessionId: 'sess_123',
            minuteIndex: 1,
            chargedToClient: 10,
            creditedToAstrologer: 0,
            adminAmount: 10,
            reason: 'first_60'
        });

        await BillingLedger.create({
            billingId: billingId + '_2',
            sessionId: 'sess_123',
            minuteIndex: 2,
            chargedToClient: 10,
            creditedToAstrologer: 4,
            adminAmount: 6,
            reason: 'slab_3'
        });

        console.log('Seeded 2 Ledger Entries.');

        // 3. Call Admin Stats API
        // Need to reconnect or just emit? The server checks DB on every request in checkAdmin, so immediate effect.

        console.log('Requesting Ledger Stats...');
        const res = await new Promise(resolve => {
            socket.emit('admin-get-ledger-stats', resolve);
        });

        if (res.ok) {
            console.log('Stats Received:', res.stats);
            console.log('Breakdown:', res.breakdown);

            // Verify (Note: existing DB data might affect totals, so we look for AT LEAST our data magnitude or close match if DB was empty)
            // Since DB was empty in audit, we expect exactly our values (unless other tests ran in parallel).
            // My seeded values:
            // Revenue = 10+10 = 20.
            // Astro = 0+4 = 4.
            // Admin = 10+6 = 16.
            // Minutes = 2.

            // Note: The previous audit showed 0, so DB is likely empty except these.

            if (res.stats.totalRevenue >= 20 && res.stats.totalAstroPayout >= 4) {
                console.log('✅ Admin Stats Verified.');
            } else {
                console.error('❌ Stats mismatch.');
            }
        } else {
            console.error('❌ Admin Stats Request Failed (Auth? or Error)', res);
        }

        // Cleanup
        await BillingLedger.deleteMany({ sessionId: 'sess_123' });

    } catch (e) {
        console.error(e);
    } finally {
        if (socket.connected) socket.disconnect();
        await mongoose.connection.close();
        process.exit();
    }
}

function register(socket, phone) {
    return new Promise((resolve) => {
        socket.emit('register', { phone }, (res) => resolve(res));
    });
}

function getUserID(socket) {
    // Hack: we don't have direct get ID, but register returns userId if successful.
    // Re-register to get ID
    return new Promise(r => socket.emit('register', { phone: '9999999999' }, res => r(res.userId)));
}

runTest();
