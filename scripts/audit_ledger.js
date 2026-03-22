const mongoose = require('mongoose');

const MONGO_URI = 'mongodb+srv://murugannagaraja781_db_user:NewLife2025@cluster0.tp2gekn.mongodb.net/astrofive';

const BillingLedger = mongoose.model('BillingLedger', new mongoose.Schema({
    sessionId: String,
    minuteIndex: Number,
    chargedToClient: Number,
    creditedToAstrologer: Number,
    adminAmount: Number,
    reason: String
}));

async function runAudit() {
    await mongoose.connect(MONGO_URI);
    console.log('--- Ledger Audit Starting ---');

    const entries = await BillingLedger.find({});
    console.log(`Analyzing ${entries.length} Ledger Entries.`);

    let totalClientCharge = 0;
    let totalAstroCredit = 0;
    let totalAdminRevenue = 0;
    let errors = 0;

    for (const entry of entries) {
        totalClientCharge += entry.chargedToClient;
        totalAstroCredit += entry.creditedToAstrologer;
        totalAdminRevenue += entry.adminAmount;

        // Invariant 1: Sum Check
        // Use epsilon for float comparison just in case, though usually we deal with ints or fixed decimals.
        const sum = entry.creditedToAstrologer + entry.adminAmount;
        if (Math.abs(sum - entry.chargedToClient) > 0.01) {
            console.error(`❌ Invariant Failed (Sum): Session ${entry.sessionId} Min ${entry.minuteIndex}. Charge ${entry.chargedToClient} != ${sum}`);
            errors++;
        }

        // Invariant 2: Rounded Rule
        if (entry.reason === 'rounded' || entry.reason === 'first_60' || entry.reason === 'first_60_partial') {
            if (entry.creditedToAstrologer !== 0) {
                console.error(`❌ Invariant Failed (Zero Astro): Session ${entry.sessionId} Reason ${entry.reason} but Astro got ${entry.creditedToAstrologer}`);
                errors++;
            }
        }

        // Invariant 3: Integer Minute
        if (!Number.isInteger(entry.minuteIndex)) {
            console.error(`❌ Invariant Failed (Integer Min): Session ${entry.sessionId} Index ${entry.minuteIndex}`);
            errors++;
        }
    }

    console.log('--- Audit Summary ---');
    console.log(`Total Client Charges: ₹${totalClientCharge}`);
    console.log(`Total Astro Payouts:  ₹${totalAstroCredit}`);
    console.log(`Total Admin Revenue:  ₹${totalAdminRevenue}`);
    console.log(`Total Errors Found:   ${errors}`);

    if (errors === 0) console.log('✅ All Invariants Passed.');
    else console.log('❌ Audit Failed.');

    await mongoose.connection.close();
    process.exit(errors === 0 ? 0 : 1);
}

runAudit();
