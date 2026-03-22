const mongoose = require('mongoose');
const crypto = require('crypto');

const MONGO_URI = 'mongodb+srv://murugannagaraja781_db_user:NewLife2025@cluster0.tp2gekn.mongodb.net/astrofive';

// --- Schema Definitions (Mirrors server.js) ---

const PairMonthSchema = new mongoose.Schema({
    pairId: { type: String, required: true, index: true },
    clientId: String,
    astrologerId: String,
    yearMonth: { type: String, required: true },
    currentSlab: { type: Number, default: 0 },
    slabLockedAt: { type: Number, default: 0 },
    resetAt: Date
});
PairMonthSchema.index({ pairId: 1, yearMonth: 1 }, { unique: true });
// Use a different model name to avoid conflicts if we were importing,
// but here we are standalone.
const PairMonth = mongoose.model('PairMonthTest', PairMonthSchema);

const SessionSchema = new mongoose.Schema({
    sessionId: { type: String, unique: true },
    clientId: String,
    astrologerId: String,
    status: { type: String, enum: ['active', 'ended'], default: 'active' },
    // ... other fields
});
const Session = mongoose.model('SessionTest', SessionSchema);

const BillingLedgerSchema = new mongoose.Schema({
    billingId: { type: String, unique: true },
    sessionId: { type: String, required: true, index: true },
    minuteIndex: { type: Number, required: true },
    chargedToClient: Number,
    creditedToAstrologer: Number,
    adminAmount: Number,
    reason: { type: String, enum: ['first_60', 'slab', 'rounded'] },
    createdAt: { type: Date, default: Date.now }
});
const BillingLedger = mongoose.model('BillingLedgerTest', BillingLedgerSchema);

// --- Verification Logic ---

async function runVerification() {
    try {
        await mongoose.connect(MONGO_URI);
        console.log('✅ Connected to MongoDB');

        // 1. Test PairMonth Uniqueness
        console.log('\n--- Testing PairMonth Uniqueness ---');
        const pairId = 'client_1_astro_1';
        const yearMonth = '2023-12';

        // Cleanup first
        await PairMonth.deleteMany({ pairId, yearMonth });

        await PairMonth.create({
            pairId,
            clientId: 'client_1',
            astrologerId: 'astro_1',
            yearMonth
        });
        console.log('✅ Created first PairMonth record');

        try {
            await PairMonth.create({
                pairId,
                clientId: 'client_1',
                astrologerId: 'astro_1',
                yearMonth
            });
            console.error('❌ Failed: Duplicate PairMonth allowed!');
        } catch (e) {
            if (e.code === 11000) {
                console.log('✅ Success: Duplicate PairMonth prevented (Code 11000)');
            } else {
                console.error('❌ Unexpected error during duplicate check:', e);
            }
        }

        // 2. Test Session Creation
        console.log('\n--- Testing Session Creation ---');
        const sessionId = crypto.randomUUID();
        await Session.create({
            sessionId,
            clientId: 'c1',
            astrologerId: 'a1',
            status: 'active'
        });
        console.log('✅ Created Session record');

        // 3. Test Billing Ledger
        console.log('\n--- Testing Billing Ledger ---');
        await BillingLedger.create({
            billingId: crypto.randomUUID(),
            sessionId,
            minuteIndex: 1,
            chargedToClient: 10,
            creditedToAstrologer: 5,
            adminAmount: 5,
            reason: 'first_60'
        });
        console.log('✅ Created BillingLedger record');

    } catch (err) {
        console.error('Verification Fatal Error:', err);
    } finally {
        await mongoose.connection.close();
    }
}

runVerification();
