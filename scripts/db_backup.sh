#!/bin/bash

# Configuration
PROJECT_DIR="/Users/wohozo/Documents/Astroluna"
BACKUP_DIR="$PROJECT_DIR/backups"
TIMESTAMP=$(date +"%Y-%m-%d_%H-%M-%S")
BACKUP_NAME="astroluna_db_$TIMESTAMP"

# Load environment variables
if [ -f "$PROJECT_DIR/.env" ]; then
    export $(grep -v '^#' "$PROJECT_DIR/.env" | xargs)
fi

MONGODB_URI=${MONGODB_URI}

if [ -z "$MONGODB_URI" ]; then
    echo "Error: MONGODB_URI not found in .env file"
    exit 1
fi

# Create backup directory if it doesn't exist
mkdir -p "$BACKUP_DIR"

echo "Starting MongoDB backup..."

# Use mongodump to create a backup
# For Atlas, we use the full URI string
mongodump --uri="$MONGODB_URI" --out="$BACKUP_DIR/$BACKUP_NAME"

# Check if mongodump succeeded
if [ $? -eq 0 ]; then
    echo "Backup successful. Compressing..."

    # Compress the backup
    cd "$BACKUP_DIR"
    tar -czf "$BACKUP_NAME.tar.gz" "$BACKUP_NAME"

    # Clean up the uncompressed backup folder
    rm -rf "$BACKUP_NAME"

    echo "Backup saved as: $BACKUP_DIR/$BACKUP_NAME.tar.gz"

    # Optional: Delete backups older than 7 days
    find "$BACKUP_DIR" -type f -name "*.tar.gz" -mtime +7 -delete

    # Trigger Rclone sync (if installed and configured)
    if command -v rclone &> /dev/null; then
        echo "Syncing to Google Drive..."
        # Change 'gdrive:astroluna_backups' to your rclone remote name and folder
        rclone sync "$BACKUP_DIR" gdrive:astroluna_backups
    else
        echo "Rclone not installed. Skipping Google Drive sync."
        echo "Please install rclone and configure a remote named 'gdrive' to enable auto-sync."
    fi
else
    echo "Error: mongodump failed."
    exit 1
fi
