#!/bin/bash

# Astroluna - Auto Deploy Script
# Run this on server: curl -fsSL https://raw.githubusercontent.com/murugannagaraja781/Astroluna/main/autodeploy.sh | bash

echo "=========================================="
echo "      Astroluna Auto Deploy"
echo "=========================================="

# Variables
APP_DIR="/var/www/Astroluna"
REPO_URL="https://github.com/murugannagaraja781/astroluna.in.git"
APP_NAME="astroluna"

# Step 1: Setup Swap if memory is low (Mandatory for 512MB RAM)
total_mem=$(free -m | awk '/^Mem:/{print $2}')
swap_count=$(swapon --show | wc -l)

if [ "$total_mem" -lt 1000 ] && [ "$swap_count" -le 1 ]; then
    echo "[1/6] Low memory detected ($total_mem MB). Creating 1GB swap file..."
    if [ ! -f "/swapfile" ]; then
        sudo fallocate -l 1G /swapfile
        sudo chmod 600 /swapfile
        sudo mkswap /swapfile
        sudo swapon /swapfile
        echo "/swapfile none swap sw 0 0" | sudo tee -a /etc/fstab
        echo "Swap file created and activated."
    else
        sudo swapon /swapfile 2>/dev/null || true
        echo "Existing swap file activated."
    fi
fi

# Step 2: Clone or pull latest code
echo "[2/6] Getting latest code from $REPO_URL..."

# Optimization for low memory npm
export NODE_OPTIONS="--max-old-space-size=448"

# Define SSH Key Command if key exists
if [ -f "github_action_key" ]; then
    echo "Found github_action_key. Configuring Git to use it..."
    chmod 600 github_action_key
    # Important: Use full path for key
    export GIT_SSH_COMMAND="ssh -i $(pwd)/github_action_key -o IdentitiesOnly=yes -o StrictHostKeyChecking=no"

    # Ensure remote is SSH if we have key
    if [ -d ".git" ]; then
        current_url=$(git remote get-url origin)
        if [[ "$current_url" == https* ]]; then
             echo "Switching remote to SSH..."
             git remote set-url origin git@github.com:murugannagaraja781/Astroluna.git
        fi
    fi
fi

if [ -d ".git" ]; then
    echo "Pulling latest changes..."
    # Reset any local changes
    git reset --hard
    git fetch origin main
    git reset --hard origin/main
else
    echo "Cloning repository..."
    cd /var/www
    sudo rm -rf Astroluna
    git clone $REPO_URL Astroluna
    cd $APP_DIR
fi

# Step 3: Set permissions
echo "[3/6] Setting permissions..."
sudo chown -R $USER:$USER $APP_DIR
chmod -R 755 $APP_DIR

# IMPORTANT: Reset private key to 600 or SSH will fail next time
if [ -f "$APP_DIR/github_action_key" ]; then
    chmod 600 "$APP_DIR/github_action_key"
    echo "Secured github_action_key (600)"
fi

# Step 3.5: Check for critical configuration files
if [ ! -f "firebase-service-account.json" ]; then
    echo "=========================================="
    echo "⚠️  CRITICAL WARNING: firebase-service-account.json MISSING"
    echo "------------------------------------------"
    echo "This file is ignored by Git for security."
    echo "You MUST upload it manually to: $APP_DIR"
    echo "Example: scp firebase-service-account.json user@server:$APP_DIR"
    echo "=========================================="
fi

# Step 4: Install dependencies
echo "[4/6] Installing dependencies..."
# Use memory-efficient npm install
npm install --production --no-audit --no-fund --prefer-offline || {
    echo "Initial npm install failed. Retrying with --no-package-lock..."
    rm -rf node_modules
    npm install --production --no-audit --no-fund --no-package-lock
}

# Step 4.5: Ensure PM2 is installed
if ! command -v pm2 &> /dev/null; then
    echo "[4.5/6] PM2 not found. Installing globally..."
    # If not root, you might need 'sudo npm install -g pm2'
    # But since the script runs setup tasks with sudo, we'll try direct installation
    npm install -g pm2 || sudo npm install -g pm2
fi

# Step 5: Setup PM2
echo "[5/6] Setting up PM2 for $APP_NAME..."
pm2 delete $APP_NAME 2>/dev/null || true
pm2 start server.js --name $APP_NAME

# Step 6: Save PM2 config
echo "[6/6] Saving PM2 configuration..."
pm2 save

echo ""
echo "=========================================="
echo "    Deployment Complete!"
echo "=========================================="
echo ""
echo "App running on port 3000"
echo "PM2 status: pm2 status"
echo "PM2 logs: pm2 logs $APP_NAME"
echo ""
