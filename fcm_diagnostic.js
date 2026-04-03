const admin = require('firebase-admin');
const path = require('path');
const fs = require('fs');

async function debug() {
    console.log("\n--- FCM Diagnostic ---");
    const saPath = path.join(__dirname, 'firebase-service-account.json');
    
    if (!fs.existsSync(saPath)) {
        console.log("❌ Error: firebase-service-account.json NOT FOUND in " + __dirname);
        return;
    }

    try {
        const saText = fs.readFileSync(saPath, 'utf8');
        const sa = JSON.parse(saText);
        console.log("✅ Service Account Project ID:", sa.project_id);
        
        if (sa.project_id !== 'astroluna-76da1') {
            console.log("⚠️ WARNING: Project ID mismatch! Expected: astroluna-76da1");
        } else {
            console.log("✓ Project ID matches google-services.json");
        }

        const app = admin.initializeApp({
            credential: admin.credential.cert(sa)
        }, 'testApp');
        console.log("✓ Firebase Admin initialized successfully.");

        // Check ENV
        require('dotenv').config();
        console.log("✓ FCM_SERVER_KEY (for legacy): " + (process.env.FCM_SERVER_KEY ? "EXISTS" : "MISSING"));
        
    } catch (e) {
        console.log("❌ Error during diagnostic:", e.message);
    }
    process.exit();
}
debug();
