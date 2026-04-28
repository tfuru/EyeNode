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

data class CaptureRequest(
    val source: String,
    val voiceText: String? = null,
    val isDialogue: Boolean = false
)

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

    private val _captureRequested = MutableStateFlow<CaptureRequest?>(null)
    val captureRequested = _captureRequested.asStateFlow()

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

    private val _isForeground = MutableStateFlow(true)
    val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

    private val _dialogueRestartRequested = MutableSharedFlow<Unit>()
    val dialogueRestartRequested = _dialogueRestartRequested.asSharedFlow()


    private var autoAnalysisJob: Job? = null
    private var lockJob: Job? = null
    private var playbackJob: Job? = null
    
    // 音声再生用のキュー (nullは発話セッションの終了マーカー)
    private val audioQueue = Channel<ByteArray?>(Channel.UNLIMITED)

    init {
        // 自動解析ループの監視
        viewModelScope.launch {
            dataRepository.autoAnalysisSettings
                .distinctUntilChanged()
                .collect { settings ->
                    Log.d("MainViewModel", "Auto-analysis settings changed: enabled=${settings.enabled}, interval=${settings.interval}")
                    startAutoAnalysisLoop(settings.enabled, settings.interval)
                }
        }

        // 音声再生ループの開始
        startPlaybackLoop()
    }

    private fun startAutoAnalysisLoop(enabled: Boolean, intervalSeconds: Int) {
        if (autoAnalysisJob != null) {
            Log.d("MainViewModel", "Cancelling existing auto-analysis job")
            autoAnalysisJob?.cancel()
        }
        if (enabled) {
            Log.d("MainViewModel", "Starting responsive auto-analysis job with interval: $intervalSeconds s")
            autoAnalysisJob = viewModelScope.launch {
                var lastAnalysisTime = 0L
                
                // 全てのガード条件を監視
                combine(
                    _isForeground,
                    _isTriggerLocked,
                    _isContinuousDialogue,
                    _isAnalyzing,
                    _isAiSpeaking
                ) { foreground, locked, dialogue, analyzing, speaking ->
                    foreground && !locked && !dialogue && !analyzing && !speaking
                }.collectLatest { canAnalyze ->
                    if (canAnalyze) {
                        val currentTime = System.currentTimeMillis()
                        val elapsed = currentTime - lastAnalysisTime
                        val waitTime = (intervalSeconds * 1000L) - elapsed
                        
                        if (waitTime > 0) {
                            delay(waitTime)
                        }
                        
                        // ディレイ後にもう一度条件をチェック (collectLatestにより、ディレイ中に条件が変わればキャンセルされる)
                        addLog("自動解析を実行します...")
                        _captureRequested.value = CaptureRequest("自動解析")
                        lastAnalysisTime = System.currentTimeMillis()
                        
                        // 次回まで間隔を空ける
                        delay(intervalSeconds * 1000L)
                    }
                }
            }
        }
    }

    private val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)

    private fun startPlaybackLoop() {
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            for (audioData in audioQueue) {
                if (audioData == null) {
                    // 発話セッション終了。
                    _isAiSpeaking.value = false
                    if (_isContinuousDialogue.value) {
                        // 次の聞き取りのためにタイマーをリフレッシュ (5分)
                        lockTriggers(300000L, true)
                        _dialogueRestartRequested.emit(Unit)
                    }
                    if (!_isContinuousDialogue.value) break // 対話終了なら再生ループも抜ける
                    continue
                }
                
                _isAiSpeaking.value = true
                playAudio(audioData)
                // 各チャンクの間は少しだけフラグを維持してマイクが即座に開かないようにする
            }
            // キューに残っているデータをクリア
            while (audioQueue.tryReceive().isSuccess) { /* empty */ }
            _isAiSpeaking.value = false
        }
    }

    fun stopAiSpeaking() {
        _isAiSpeaking.value = false
        playbackJob?.cancel() // 現在の再生を即座に中断
        // チャンネルの中身を空にする
        while (audioQueue.tryReceive().isSuccess) { /* empty */ }
        addLog("AIの発話を中断しました")
        startPlaybackLoop() // ループを再起動して次の発話に備える
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
            try {
                playTriggerSound()
                _isTriggerLocked.value = true
                // 対話モードならより長いタイムアウト(デフォルト5分)を使用
                val timeout = if (isDialogue) 300000L else durationMs
                delay(timeout)
            } finally {
                _isTriggerLocked.value = false
                // 本当にタイムアウト（キャンセルされずに終了）した場合のみ
                if (isDialogue && this.isActive) {
                    _isContinuousDialogue.value = false
                    addLog("無音が続いたため対話モードを終了しました")
                }
            }
        }
    }

    fun stopContinuousDialogue() {
        addLog("対話モードの終了リクエストを受理しました")
        playTriggerSound() // 終了音を鳴らす
        stopAiSpeaking() // AIの発話を止める
        _isContinuousDialogue.value = false
        unlockTriggers()
        addLog("対話モードを完全に終了しました")
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
            val normalizedText = voiceText.trim().replace(Regex("[。？！]"), "")
            val endKeywords = listOf("対話終了", "対話終わり", "おしまい", "バイバイ", "さようなら", "終了", "ストップ", "さよなら")
            if (endKeywords.any { normalizedText.contains(it) }) {
                addLog("終了キーワードを検知（$normalizedText）: 対話モードを終了します")
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
        _captureRequested.value = CaptureRequest(
            source = if (isDialogue) "対話リクエスト" else "音声トリガー",
            voiceText = voiceText,
            isDialogue = isDialogue
        )
    }

    fun onCaptureCompleted() {
        _captureRequested.value = null
    }

    fun setIsForeground(foreground: Boolean) {
        _isForeground.value = foreground
        if (foreground) {
            Log.d("MainViewModel", "App returned to foreground")
        } else {
            Log.d("MainViewModel", "App moved to background")
        }
    }

    fun updateAudioLevel(level: Float) {
        _audioLevel.value = level
    }

    fun analyzeImage(
        bitmap: Bitmap, 
        position: com.example.eyenode.ai.HandGestureDetector.FingerPosition? = null,
        voiceText: String? = null,
        isDialogue: Boolean = false,
        source: String = "不明"
    ) {
        _lastAnalyzedImage.value = bitmap
        
        if (position != null) {
            addLog("指差しを検知しました")
            playTriggerSound()
        } else if (isDialogue) {
            addLog("対話（日常会話）を開始します...")
        } else if (voiceText == null) {
            addLog("自動解析を実行中（$source）...")
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
