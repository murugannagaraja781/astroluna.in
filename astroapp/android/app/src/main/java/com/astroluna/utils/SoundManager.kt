package com.astroluna.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.media.ToneGenerator
import android.os.Build

object SoundManager {

    private var toneGenerator: ToneGenerator? = null

    // Simple system tones for lightweight feedback
    fun playSentSound() {
        try {
            if (toneGenerator == null) {
                toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 50)
            }
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP)
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun playReceiveSound() {
        try {
            if (toneGenerator == null) {
                toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70)
            }
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK)
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun playEndChatSound() {
         try {
            if (toneGenerator == null) {
                toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70)
            }
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_NACK) // Distinct "End" tone
        } catch (e: Exception) { e.printStackTrace() }
    }

    /*
       For richer sounds (Accept/Reject), normally we'd play MP3s from res/raw.
       Since we don't have files, we'll use system sounds.
    */
    fun playAcceptSound() {
         try {
            if (toneGenerator == null) {
                toneGenerator = ToneGenerator(AudioManager.STREAM_RING, 80)
            }
            toneGenerator?.startTone(ToneGenerator.TONE_SUP_CONFIRM)
        } catch (e: Exception) { e.printStackTrace() }
    }

     fun playRejectSound() {
         try {
            if (toneGenerator == null) {
                toneGenerator = ToneGenerator(AudioManager.STREAM_RING, 80)
            }
            toneGenerator?.startTone(ToneGenerator.TONE_SUP_ERROR)
        } catch (e: Exception) { e.printStackTrace() }
    }
}
