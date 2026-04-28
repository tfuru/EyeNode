package com.example.eyenode.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

class VoiceTriggerHandler(
    private val context: Context,
    private val onTrigger: (String, Boolean) -> Unit,
    private val onCancelDialogue: () -> Unit, // 追加
    private val onLevelChanged: (Float) -> Unit,
    private val onLog: (String) -> Unit
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var isDialogueMode = false // ジェスチャーなどで強制的に対話を開始したか
    private var hasLoggedReady = false // 「お話しください」をログに出したか
    private var isRestarting = false // 再起動中（キャンセルのトリガー中）か
    private var keywords: List<String> = listOf("これ何", "教えて", "解析")
    private var mode: String = "ALWAYS"

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            if (isDialogueMode && !hasLoggedReady) {
                onLog("対話モード: お話しください")
                hasLoggedReady = true
            }
        }

        override fun onBeginningOfSpeech() {
            // 発話開始
        }

        override fun onRmsChanged(rmsdB: Float) {
            // rmsdB は通常 -2.0 〜 10.0 程度。これを 0.0 〜 1.0 に正規化
            val normalizedLevel = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            onLevelChanged(normalizedLevel)
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            // onLog("発話終了")
        }

        override fun onError(error: Int) {
            val message = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "No match found"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RecognitionService busy"
                SpeechRecognizer.ERROR_SERVER -> "Server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                else -> "Unknown error"
            }
            Log.e("VoiceTrigger", "Error: $message")
            
            // 対話モード中の沈黙(NO_MATCH)などは、5分タイムアウトまでは無視して再開させる
            if (isDialogueMode && !isRestarting) {
                // ロギングは抑制して再開
            }
            
            // 常時待機または対話モード継続なら再開
            if ((mode == "ALWAYS" || isDialogueMode) && isListening && !isRestarting) {
                startListening()
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            var triggered = false
            matches?.forEach { text ->
                if (isDialogueMode || keywords.any { text.contains(it) }) {
                    val logPrefix = if (isDialogueMode) "対話認識: " else "キーワード検知: "
                    onLog("$logPrefix $text")
                    onTrigger(text, isDialogueMode)
                    triggered = true
                    isDialogueMode = false 
                    hasLoggedReady = false // 認識成功時のみリセットして次回の待機ログを許可
                    return@forEach
                }
            }
            if (isDialogueMode && !triggered && !isRestarting) {
                // 認識できなかったが、対話モードは維持して再開
                Log.d("VoiceTrigger", "No match in dialogue mode, restarting...")
            }
            // 常時待機または対話モード継続なら再開
            if ((mode == "ALWAYS" || isDialogueMode) && isListening && !isRestarting) {
                startListening()
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun updateSettings(newMode: String, newKeywords: String) {
        mode = newMode
        keywords = newKeywords.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (mode == "ALWAYS") {
            startListening()
        } else {
            stopListening()
        }
    }

    fun startDialogue() {
        onLog("電話ジェスチャー検知: 音声対話を開始します")
        isDialogueMode = true
        hasLoggedReady = false
        isRestarting = true
        
        // 確実にリセットするために一度キャンセル
        speechRecognizer?.cancel()
        
        // RecognizerがIdleになるのを待ってから開始 (Busyエラー回避)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            isRestarting = false
            startListening()
        }, 300)
    }

    fun startListening() {
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(recognitionListener)
            }
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ja-JP")
        }
        
        speechRecognizer?.startListening(intent)
        isListening = true
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        isListening = false
    }

    fun stopDialogueMode() {
        isDialogueMode = false
        hasLoggedReady = false
        // 対話モードを抜けるだけで、ALWAYSモードなら聞き取り自体は継続される
    }

    fun release() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}
