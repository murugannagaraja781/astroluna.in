package com.astroluna.utils

/**
 * CallState - Global state to prevent duplicate call handling and crashes
 */
object CallState {
    @Volatile
    var isCallActive = false

    @Volatile
    var currentSessionId: String? = null

    /**
     * Determines if a new call/chat session can be received.
     * Block if a session is already active (CallActivity/ChatActivity).
     */
    fun canReceiveCall(newSessionId: String?): Boolean {
        // If we are already in an active call/chat, block new ones
        if (isCallActive) return false

        // If the new session is the same as the current one, we can either allow it (to refresh UI)
        // or block it if we know an activity is already handling it.
        // For now, only block if isCallActive is true.
        // The Activity's launchMode (singleTop) will handle duplicate intents for the same session.

        return true
    }
}
