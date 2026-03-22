package com.astroluna

import android.app.Application

class AstrologerApp : Application() {
    override fun onCreate() {
        super.onCreate()
         com.astroluna.data.remote.SocketManager.init()
    }
}
