#!/bin/bash

# Astroluna Server Total Backup Dispatcher
# This script is designed to run on your PRODUCTION SERVER.

# Set the base directory absolutely
BASE_DIR="/var/www/Astroluna"
JS_BACKUP_SCRIPT="$BASE_DIR/scripts/backup.js"

echo "==========================================="
echo "   Astroluna Server Backup Initializing   "
echo "==========================================="

# 1. Self-Installation Check
if ! command -v rclone &> /dev/null; then
    echo "[!] Rclone not found. Attempting to install..."
    if command -v apt-get &> /dev/null; then
        sudo apt-get update && sudo apt-get install rclone -y
    else
        echo "[!] Auto-install only supports Ubuntu. Please install rclone manually."
        exit 1
    fi
fi

if ! command -v mongodump &> /dev/null; then
    echo "[!] mongodump not found. Please install MongoDB Tools."
    echo "    (sudo apt install mongodb-database-tools -y)"
    # Attempt to install it if possible
    sudo apt-get update && sudo apt-get install mongodb-database-tools -y
fi

# 2. Run the Node.js Backup Logic
echo "[+] Starting Node.js Backup Engine..."
if [ -f "$JS_BACKUP_SCRIPT" ]; then
    node "$JS_BACKUP_SCRIPT"
else
    echo "!! ERROR: Backup script not found at $JS_BACKUP_SCRIPT"
    echo "Current directory: $(pwd)"
    ls -l "$BASE_DIR/scripts"
    exit 1
fi

# 3. Check for Success
if [ $? -eq 0 ]; then
    echo "==========================================="
    echo "   BACKUP PROCESS COMPLETED SUCCESSFULLY   "
    echo "==========================================="
else
    echo "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
    echo "   BACKUP PROCESS FAILED! Check logs.      "
    echo "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
    exit 1
fi
