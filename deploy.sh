#!/bin/bash

# Configuration
PROJECT_DIR="." 
REPO_URL="https://github.com/murugannagaraja781/astroluna.in.git"

echo "🚀 Starting Deployment for AstroLuna..."

# 1. Check/Install Git
if ! command -v git &> /dev/null; then
    echo "⚙️ git not found. Installing..."
    sudo apt update && sudo apt install git -y || { echo "❌ Git install failed!"; exit 1; }
fi

# 2. Check/Install Node.js & NPM
if ! command -v npm &> /dev/null; then
    echo "⚙️ Node.js not found. Installing..."
    curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
    sudo apt install -y nodejs || { echo "❌ Node.js install failed!"; exit 1; }
fi

# 3. Check/Install PM2
if ! command -v pm2 &> /dev/null; then
    echo "⚙️ PM2 not found. Installing..."
    sudo npm install pm2@latest -g || { echo "❌ PM2 install failed!"; exit 1; }
fi

# --- Main Deployment ---

echo "📥 Syncing latest code..."
# Ensure the directory is a git repo
if [ ! -d ".git" ]; then
    echo "Initializing Git Repository..."
    git init
    git remote add origin $REPO_URL
fi

# Pull latest code
git fetch origin
git reset --hard origin/main || git reset --hard origin/master

# Install Dependencies
echo "📦 Installing project dependencies..."
npm install --omit=dev

# Restart Server (Using PM2)
echo "🔄 Restarting AstroLuna server..."
pm2 delete astroluna 2>/dev/null # Remove old instance if exists
pm2 start server.js --name "astroluna" --update-env

echo "✅ AstroLuna is now RUNNING!"
