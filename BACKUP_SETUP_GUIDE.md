# MongoDB Backup Setup Walkthrough (Astroluna)

I have created a backup script for you at `/Users/wohozo/Documents/Astroluna/scripts/db_backup.sh`. This script will:
1. Dump your MongoDB database.
2. Zip it into a `.tar.gz` file.
3. Save it in a folder called `backups/`.
4. Keep only the last 7 days of backups (to save local space).

To complete the **Google Drive Sync (Free)**, please follow these steps:

### 1. Install Rclone
Open your terminal and run:
`brew install rclone`
*(If you don't have Homebrew, download it from [rclone.org](https://rclone.org/downloads/))*

### 2. Configure Google Drive
Run this command and follow the interactive steps:
`rclone config`
*   Type `n` for **New remote**.
*   Name it: `gdrive`
*   Type `drive` when it asks for the storage type (Google Drive).
*   Leave **client_id** and **client_secret** blank.
*   Choose scope `1` (Full access to all files).
*   When it asks to **Use auto config**, type `Y`.
*   A browser window will open. **Log in to your Google Account** and click **Allow**.
*   Done!

### 3. Setup Automatic Cron Job (Daily Backup)
To make this run every night at 2 AM automatically:
1. Run `crontab -e` in your terminal.
2. Paste this line at the bottom:
`0 2 * * * /Users/wohozo/Documents/Astroluna/scripts/db_backup.sh >> /Users/wohozo/Documents/Astroluna/backups/backup.log 2>&1`

### 4. Test it now
You can run it manually right away to see if it works:
`/Users/wohozo/Documents/Astroluna/scripts/db_backup.sh`

---
**Security Note**: This script uses the URI from your `.env` file. Do not share your `.env` file with anyone.
