const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '../.env') });

/**
 * Astroluna Total Backup Script (JS version)
 * Handles: MongoDB Dump -> Zip -> Google Drive Sync
 */

const MONGO_URI = process.env.MONGODB_URI;
const BACKUP_DIR = path.join(__dirname, '../backups');
const TIMESTAMP = new Date().toISOString().replace(/[:.]/g, '-');
const BACKUP_NAME = `astroluna_db_${TIMESTAMP}`;
const FULL_PATH = path.join(BACKUP_DIR, BACKUP_NAME);

async function runBackup() {
    try {
        if (!MONGO_URI) throw new Error("MONGODB_URI not found in .env");

        // 1. Create backup directory
        if (!fs.existsSync(BACKUP_DIR)) {
            fs.mkdirSync(BACKUP_DIR, { recursive: true });
        }

        console.log(`[${new Date().toLocaleString()}] Starting Backup: ${BACKUP_NAME}`);

        // 2. Run mongodump
        console.log("-> Running mongodump...");
        execSync(`mongodump --uri="${MONGO_URI}" --out="${FULL_PATH}"`);

        // 3. Zip the backup
        console.log("-> Compressing backup...");
        const tarFile = `${BACKUP_NAME}.tar.gz`;
        execSync(`tar -czf ${tarFile} ${BACKUP_NAME}`, { cwd: BACKUP_DIR });

        // 4. Cleanup uncompressed folder
        console.log("-> Cleaning up temporary files...");
        execSync(`rm -rf ${BACKUP_NAME}`, { cwd: BACKUP_DIR });

        // 5. Sync to Google Drive using rclone
        console.log("-> Syncing to Google Drive (rclone)...");
        try {
            // This assumes rclone is configured with 'gdrive' remote
            execSync(`rclone sync "${BACKUP_DIR}" gdrive:astroluna_backups`);
            console.log("-> Sync complete!");
        } catch (rcloneErr) {
            console.error("!! Rclone sync failed. Check if rclone is configured.");
            console.error(rcloneErr.message);
        }

        // 6. Delete backups older than 7 days
        console.log("-> Deleting local backups older than 7 days...");
        const files = fs.readdirSync(BACKUP_DIR);
        const now = Date.now();
        const sevenDaysMs = 7 * 24 * 60 * 60 * 1000;

        files.forEach(file => {
            if (file.endsWith('.tar.gz')) {
                const filePath = path.join(BACKUP_DIR, file);
                const stats = fs.statSync(filePath);
                if (now - stats.mtimeMs > sevenDaysMs) {
                    fs.unlinkSync(filePath);
                    console.log(`   Deleted old backup: ${file}`);
                }
            }
        });

        console.log(`[${new Date().toLocaleString()}] Backup Finished Successfully!`);

    } catch (err) {
        console.error("FATAL ERROR during backup:");
        console.error(err);
        process.exit(1);
    }
}

runBackup();
