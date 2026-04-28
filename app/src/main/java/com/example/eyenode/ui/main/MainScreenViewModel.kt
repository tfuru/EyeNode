package com.example.eyenode.ui.main

import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eyenode.data.DataRepository
import com.example.eyenode.ui.main.MainScreenUiState.Success
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class MainScreenViewModel(val dataRepository: DataRepository) : ViewModel() {
    private val _logs = MutableStateFlow<List<String>>(listOf("システムを起動しました"))
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    val uiState: StateFlow<MainScreenUiState> =
        dataRepository.data
            .map<List<String>, MainScreenUiState>(::Success)
            .catch { emit(MainScreenUiState.Error(it)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MainScreenUiState.Loading)

    val voiceTriggerMode: StateFlow<String> = dataRepository.voiceTriggerMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "ALWAYS")

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val _captureRequested = MutableStateFlow(false)
    val captureRequested: StateFlow<Boolean> = _captureRequested.asStateFlow()

    private val _lastAnalyzedImage = MutableStateFlow<Bitmap?>(null)
    val lastAnalyzedImage: StateFlow<Bitmap?> = _lastAnalyzedImage.asStateFlow()

    private val _isTriggerLocked = MutableStateFlow(false)
    val isTriggerLocked: StateFlow<Boolean> = _isTriggerLocked.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _isAiSpeaking = MutableStateFlow(false)
    val isAiSpeaking: StateFlow<Boolean> = _isAiSpeaking.asStateFlow()

    private val _isContinuousDialogue = MutableStateFlow(false)
    val isContinuousDialogue: StateFlow<Boolean> = _isContinuousDialogue.asStateFlow()

    private val _dialogueRestartRequested = MutableSharedFlow<Unit>()
    val dialogueRestartRequested = _dialogueRestartRequested.asSharedFlow()

    private var lastVoiceText: String? = null
    private var lastIsDialogue: Boolean = false
    private var autoAnalysisJob: Job? = null
    private var lockJob: Job? = null
    
    // 音声再生用のキュー (nullは発話セッションの終了マーカー)
    private val audioQueue = Channel<ByteArray?>(Channel.UNLIMITED)

    init {
        // 自動解析ループの監視
        viewModelScope.launch {
            combine(
                dataRepository.autoAnalysisEnabled,
                dataRepository.autoAnalysisInterval
            ) { enabled, interval ->
                enabled to interval
            }.collect { (enabled, interval) ->
                startAutoAnalysisLoop(enabled, interval)
            }
        }

        // 音声再生ループの開始
        startPlaybackLoop()
    }

    private fun startAutoAnalysisLoop(enabled: Boolean, intervalSeconds: Int) {
        autoAnalysisJob?.cancel()
        if (enabled) {
            autoAnalysisJob = viewModelScope.launch {
                while (isActive) {
                    delay(intervalSeconds * 1000L)
                    // ロック中や対話中、または解析中は自動解析をスキップ
                    if (!_isTriggerLocked.value && !_isContinuousDialogue.value && !_isAnalyzing.value) {
                        _captureRequested.value = true
                    }
                }
            }
        }
    }

    private val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)

    private fun startPlaybackLoop() {
        viewModelScope.launch {
            for (audioData in audioQueue) {
                if (audioData == null) {
                    // 発話セッション終了。
                    _isAiSpeaking.value = false
                    if (_isContinuousDialogue.value) {
                        // 次の聞き取りのためにタイマーをリフレッシュ (5分)
                        lockTriggers(300000L, true)
                        _dialogueRestartRequested.emit(Unit)
                    }
                    continue
                }
                
                _isAiSpeaking.value = true
                playAudio(audioData)
                // 各チャンクの間は少しだけフラグを維持してマイクが即座に開かないようにする
            }
            // チャンネルがクローズされた場合など
            _isAiSpeaking.value = false
        }
    }

    private fun playTriggerSound() {
        try {
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
        } catch (e: Exception) {
            Log.e("MainScreenViewModel", "Failed to play tone", e)
        }
    }

    fun lockTriggers(durationMs: Long, isDialogue: Boolean = false) {
        if (isDialogue) {
            _isContinuousDialogue.value = true
        }
        lockJob?.cancel() // 古いタイマーをキャンセル
        lockJob = viewModelScope.launch {
            playTriggerSound()
            _isTriggerLocked.value = true
            // 対話モードならより長いタイムアウト(デフォルト5分)を使用
            val timeout = if (isDialogue) 300000L else durationMs
            delay(timeout)
            _isTriggerLocked.value = false
            // タイムアウトでロック解除されたら連続対話も終了
            if (isDialogue) {
                _isContinuousDialogue.value = false
                addLog("無音が続いたため対話モードを終了しました")
            }
        }
    }

    fun stopContinuousDialogue() {
        playTriggerSound() // 終了音を鳴らす
        _isContinuousDialogue.value = false
        unlockTriggers()
        addLog("対話モードを終了しました")
    }

    fun unlockTriggers() {
        if (_isContinuousDialogue.value) {
            addLog("対話モードを解除しました")
        }
        lockJob?.cancel() // タイマーを止める
        _isTriggerLocked.value = false
        _isContinuousDialogue.value = false
    }

    fun addLog(message: String) {
        _logs.update { it + message }
    }

    fun requestCapture(voiceText: String? = null, isDialogue: Boolean = false) {
        if (isDialogue && voiceText != null) {
            val endKeywords = listOf("対話終了", "対話終わり", "おしまい", "バイバイ", "さようなら")
            if (endKeywords.any { voiceText.contains(it) }) {
                stopContinuousDialogue()
                return
            }
        }

        if (isDialogue) {
            // 継続モードの場合は完全な解除はせず、タイマーをリセット(延長)する (5分)
            lockTriggers(300000L, true) 
        } else if (_isTriggerLocked.value) {
            // ロック中は通常のトリガー（自動・指差し・通常音声）は無視
            return 
        }
        
        playTriggerSound()
        lastVoiceText = voiceText
        lastIsDialogue = isDialogue
        _captureRequested.value = true
    }

    fun onCaptureCompleted() {
        _captureRequested.value = false
    }

    fun updateAudioLevel(level: Float) {
        _audioLevel.value = level
    }

    fun analyzeImage(
        bitmap: Bitmap, 
        position: com.example.eyenode.ai.HandGestureDetector.FingerPosition? = null
    ) {
        val voiceText = lastVoiceText
        val isDialogue = lastIsDialogue
        lastVoiceText = null // Clear for next trigger
        lastIsDialogue = false
        
        _lastAnalyzedImage.value = bitmap

        if (position != null) {
            addLog("指差しを検知しました")
            playTriggerSound()
        } else if (isDialogue) {
            addLog("対話（日常会話）を開始します...")
        } else if (voiceText == null) {
            addLog("自動解析を実行中...")
        }

        viewModelScope.launch {
            _isAnalyzing.value = true
            try {
                if (!isDialogue) {
                    addLog("AI解析を開始します...")
                }
                val response = dataRepository.analyzeImage(bitmap, position, voiceText, isDialogue)
                addLog("AI: $response")
                
                // 文章を細かく分割して音声合成
                processLongTextToSpeech(response)
            } catch (e: Exception) {
                addLog("解析エラー: ${e.message}")
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    private fun processLongTextToSpeech(text: String) {
        viewModelScope.launch {
            // 句読点や改行で分割
            val chunks = text.split(Regex("[。！？\n]"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            
            for (chunk in chunks) {
                val fullChunk = chunk + "。" // イントネーションのために句点を戻す
                val audioData = dataRepository.synthesizeSpeech(fullChunk)
                if (audioData != null) {
                    audioQueue.send(audioData)
                }
            }
            // セッション終了マーカーを送信
            audioQueue.send(null)
        }
    }

    private suspend fun playAudio(data: ByteArray) {
        val tempFile = File.createTempFile("tts_output", ".mp3")
        tempFile.writeBytes(data)
        
        val mediaPlayer = MediaPlayer()
        val completionChannel = Channel<Unit>(1)

        try {
            mediaPlayer.setDataSource(tempFile.absolutePath)
            mediaPlayer.prepare()
            mediaPlayer.start()
            
            mediaPlayer.setOnCompletionListener {
                completionChannel.trySend(Unit)
            }
            
            // 再生が終わるまで待機
            completionChannel.receive()
        } catch (e: Exception) {
            Log.e("MainScreenViewModel", "Playback failed", e)
        } finally {
            mediaPlayer.release()
            tempFile.delete()
        }
    }

    override fun onCleared() {
        super.onCleared()
        toneGenerator.release()
    }
}

sealed interface MainScreenUiState {
    object Loading : MainScreenUiState
    data class Error(val throwable: Throwable) : MainScreenUiState
    data class Success(val data: List<String>) : MainScreenUiState
}
