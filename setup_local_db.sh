#!/bin/bash

# Astroluna - NO-FAIL MongoDB Installation Script
# This script tries 3 different ways to get MongoDB on your server

echo "🚀 Starting NO-FAIL MongoDB Installation for Astroluna..."

# 0. ENABLE UNIVERSE REPOSITORY
echo "⚙️ Enabling Ubuntu Universe repo..."
sudo add-apt-repository universe -y

# 1. SWAP FILE (Critical for 512MB RAM)
if [ ! -f /swapfile ]; then
    echo "💾 Adding 2GB Swap for stability..."
    sudo fallocate -l 2G /swapfile || sudo dd if=/dev/zero of=/swapfile bs=1M count=2048
    sudo chmod 600 /swapfile
    sudo mkswap /swapfile
    sudo swapon /swapfile
    echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
fi

# 2. METHOD 1: OFFICIAL REPO
echo "📦 Trying Method 1: Official MongoDB 7.0..."
CODENAME=$(lsb_release -cs)
TARGET_REPO=$CODENAME
if [[ "$CODENAME" == "noble" ]]; then TARGET_REPO="jammy"; fi

curl -fsSL https://pgp.mongodb.com/server-7.0.asc | sudo gpg --dearmor -o /usr/share/keyrings/mongodb-server-7.0.gpg --yes
echo "deb [ arch=amd64,arm64 signed-by=/usr/share/keyrings/mongodb-server-7.0.gpg ] https://repo.mongodb.org/apt/ubuntu $TARGET_REPO/mongodb-org/7.0 multiverse" | sudo tee /etc/apt/sources.list.d/mongodb-org-7.0.list

sudo apt-get update
sudo apt-get install -y mongodb-org

# 3. METHOD 2: UBUNTU DEFAULT REPO (Fallback)
if ! command -v mongod &> /dev/null; then
    echo "⚠️ Method 1 failed. Trying Method 2: Default Ubuntu MongoDB..."
    sudo apt-get install -y mongodb-server mongodb
fi

# 4. METHOD 3: SNAP INSTALL (Final Fallback)
if ! command -v mongod &> /dev/null && ! command -v mongodb &> /dev/null; then
    echo "⚠️ Method 2 failed. Trying Method 3: Snap Install (Slow but works)..."
    sudo snap install mongodb
fi

# 5. START AND CONFIGURE
echo "⚙️ Starting database..."
sudo systemctl daemon-reload
# Try starting all possible service names
sudo systemctl enable mongod || sudo systemctl enable mongodb
sudo systemctl start mongod || sudo systemctl start mongodb

# 6. PERMISSIONS & DIRECTORY FIX
echo "🛠️ Finalizing permissions..."
sudo mkdir -p /var/lib/mongodb /var/log/mongodb
sudo chown -R mongodb:mongodb /var/lib/mongodb /var/log/mongodb 2>/dev/null
sudo chmod -R 755 /var/lib/mongodb

# 7. FINAL CHECK
if pgrep -x "mongod" > /dev/null || pgrep -x "mongodb" > /dev/null || command -v mongod &> /dev/null; then
    echo "✅ SUCCESS! Database is installed."

    # Update .env
    if [ -f .env ]; then
        # Ensure database name is astroluna
        sed -i 's|^MONGODB_URI=.*|MONGODB_URI=mongodb://localhost:27017/astroluna|' .env
        echo "✅ .env updated with astroluna database."
    fi
else
    echo "❌ CRITICAL ERROR: Could not install MongoDB via any method."
    echo "Please check if your server is out of disk space: 'df -h'"
    exit 1
fi

# Ensure PM2 is installed for restarting app
if ! command -v pm2 &> /dev/null; then
    echo "⚙️ PM2 not found. Installing globally..."
    npm install -g pm2 || sudo npm install -g pm2
fi

echo "------------------------------------------------"
echo "🎉 Setup complete. Use 'pm2 restart all' to apply."
echo "------------------------------------------------"
