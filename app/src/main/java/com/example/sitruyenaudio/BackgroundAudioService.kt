package com.example.sitruyenaudio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.core.app.NotificationCompat
import java.util.Locale

class BackgroundAudioService : Service(), TextToSpeech.OnInitListener {

    private val CHANNEL_ID = "SiTruyenAudioChannel"
    private var wakeLock: PowerManager.WakeLock? = null

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private val binder = LocalBinder()

    var onSpeechComplete: (() -> Unit)? = null
    var onParagraphStarted: ((Int) -> Unit)? = null

    private var isStopped = false
    private var currentParagraphs = listOf<String>()
    
    inner class LocalBinder : Binder() {
        fun getService(): BackgroundAudioService = this@BackgroundAudioService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
        tts = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("vi", "VN"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Ignore for now
            } else {
                isTtsReady = true
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        if (utteranceId?.startsWith("Para_") == true) {
                            val index = utteranceId.substringAfter("Para_").toIntOrNull()
                            if (index != null) {
                                onParagraphStarted?.invoke(index)
                            }
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == "SiTruyenTTS_End" && !isStopped) {
                            onSpeechComplete?.invoke()
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {}
                })
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SiTruyen Audio")
            .setContentText("Dang doc truyen...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
        return START_STICKY
    }

    fun getVietnameseVoices(): List<Voice> {
        if (!isTtsReady) return emptyList()
        val allVoices = tts?.voices ?: return emptyList()
        return allVoices.filter { it.locale.language == "vi" }
    }

    fun setVoice(voiceName: String) {
        if (!isTtsReady) return
        val voice = tts?.voices?.find { it.name == voiceName }
        if (voice != null) {
            tts?.voice = voice
        }
    }

    fun setSpeechRate(rate: Float) {
        if (!isTtsReady) return
        tts?.setSpeechRate(rate)
    }

    fun setParagraphs(paragraphs: List<String>) {
        this.currentParagraphs = paragraphs
    }

    fun playFromIndex(startIndex: Int) {
        if (!isTtsReady || currentParagraphs.isEmpty()) return
        
        isStopped = false
        tts?.stop()
        
        if (startIndex >= currentParagraphs.size) {
            if (!isStopped) onSpeechComplete?.invoke()
            return
        }

        val maxLen = TextToSpeech.getMaxSpeechInputLength()
        
        var isFirst = true
        for (i in startIndex until currentParagraphs.size) {
            val p = currentParagraphs[i]
            if (p.isBlank()) continue
            
            val chunks = if (p.length <= maxLen) listOf(p) else chunkText(p, maxLen - 100)
            
            for (j in chunks.indices) {
                val mode = if (isFirst) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                isFirst = false
                
                val isLastChunkOfLastParagraph = (i == currentParagraphs.size - 1) && (j == chunks.size - 1)
                val utteranceId = if (isLastChunkOfLastParagraph) "SiTruyenTTS_End" else "Para_$i"
                
                tts?.speak(chunks[j], mode, null, utteranceId)
            }
        }
    }

    private fun chunkText(text: String, maxLen: Int): List<String> {
        val words = text.split(" ")
        val chunks = mutableListOf<String>()
        var currentChunk = ""
        for (word in words) {
            if (currentChunk.length + word.length + 1 > maxLen) {
                chunks.add(currentChunk.trim())
                currentChunk = word + " "
            } else {
                currentChunk += word + " "
            }
        }
        if (currentChunk.isNotBlank()) chunks.add(currentChunk.trim())
        return chunks
    }

    fun stopSpeaking() {
        isStopped = true
        if (isTtsReady) {
            tts?.stop()
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "SiTruyen Audio Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SiTruyenAudio::WakeLock")
        wakeLock?.acquire()
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }
}