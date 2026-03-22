package com.astroluna.data.repository

import android.content.Context
import com.astroluna.data.local.AppDatabase
import com.astroluna.data.local.entity.ChatMessageEntity
import com.astroluna.data.remote.SocketManager
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class ChatRepository(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val chatDao = db.chatDao() // Corrected

    // Local DB Operations
    fun getMessages(sessionId: String): Flow<List<ChatMessageEntity>> {
        return chatDao.getMessages(sessionId) // Corrected from getMessagesBySession
    }

    suspend fun saveMessage(message: ChatMessageEntity) {
        chatDao.insertMessage(message)
    }

    suspend fun updateMessageStatus(messageId: String, status: String) {
        chatDao.updateStatus(messageId, status)
    }

    // Remote Operations (Socket)
    fun sendMessage(data: JSONObject) {
        SocketManager.getSocket()?.emit("chat-message", data)
    }

    fun sendTyping(toUserId: String) {
        val data = JSONObject().put("toUserId", toUserId)
        SocketManager.getSocket()?.emit("typing", data)
    }

    fun sendStopTyping(toUserId: String) {
        val data = JSONObject().put("toUserId", toUserId)
        SocketManager.getSocket()?.emit("stop-typing", data)
    }

    fun markDelivered(messageId: String, toUserId: String, sessionId: String) {
        val data = JSONObject().apply {
            put("messageId", messageId)
            put("toUserId", toUserId)
            put("status", "delivered")
            put("sessionId", sessionId)
        }
        SocketManager.getSocket()?.emit("message-status", data)
    }

    fun markRead(messageId: String, toUserId: String, sessionId: String) {
         val data = JSONObject().apply {
            put("messageId", messageId)
            put("toUserId", toUserId)
            put("status", "read")
            put("sessionId", sessionId)
        }
        SocketManager.getSocket()?.emit("message-status", data)
    }

    fun acceptSession(sessionId: String, toUserId: String) {
        val payload = JSONObject().apply {
            put("sessionId", sessionId)
            put("toUserId", toUserId)
            put("accept", true)
        }
        SocketManager.getSocket()?.emit("answer-session", payload)
    }

    // Listeners
    fun listenIncoming(onMessage: (JSONObject) -> Unit) {
        SocketManager.getSocket()?.off("chat-message")
        SocketManager.getSocket()?.on("chat-message") { args ->
            if (args != null && args.isNotEmpty()) {
                val data = args[0] as JSONObject
                onMessage(data)
            }
        }
    }

    fun listenMessageStatus(onStatus: (JSONObject) -> Unit) {
        SocketManager.onMessageStatus(onStatus)
    }

    fun listenTyping(onTyping: () -> Unit) {
        SocketManager.getSocket()?.off("typing")
        SocketManager.getSocket()?.on("typing") {
            onTyping()
        }
    }

    fun listenStopTyping(onStop: () -> Unit) {
        SocketManager.getSocket()?.off("stop-typing")
        SocketManager.getSocket()?.on("stop-typing") {
            onStop()
        }
    }

    fun removeListeners() {
        SocketManager.removeChatListeners()
    }

    // Sync
    suspend fun fetchHistoryFromServer(sessionId: String, limit: Int = 50, before: Long? = null): Boolean {
        // Implementation calling Socket 'get-history'
        val jsonList = suspendCancellableCoroutine<List<JSONObject>> { continuation ->
            SocketManager.getHistory(sessionId) { list ->
                continuation.resume(list)
            }
        }

        if (jsonList.isNotEmpty()) {
            val myUserId = com.astroluna.data.local.TokenManager(context).getUserSession()?.userId

            jsonList.forEach { json ->
                try {
                     val content = json.optJSONObject("content")
                     val text = content?.optString("text") // Handle both structure styles if needed
                        ?: json.optString("text", "") // Fallback

                     val msgId = json.optString("messageId")
                     val senderId = json.optString("fromUserId")
                     // Use timestamp from server or fallback
                     var timestamp = json.optLong("timestamp", 0L)
                     if (timestamp == 0L) timestamp = json.optLong("createdAt", System.currentTimeMillis())

                     val isMe = (senderId == myUserId)

                     if (msgId.isNotEmpty()) {
                         val entity = ChatMessageEntity(
                             messageId = msgId,
                             sessionId = sessionId,
                             text = text,
                             senderId = senderId,
                             timestamp = timestamp,
                             status = "read",
                             isSentByMe = isMe
                         )
                         saveMessage(entity)
                     }
                } catch(e: Exception) { e.printStackTrace() }
            }
            return true
        }
        return false
    }
}
