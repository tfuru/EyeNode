package com.example.eyenode.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eyenode.data.DataRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val dataRepository: DataRepository) : ViewModel() {
    val serverUrl: StateFlow<String> = dataRepository.serverUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "192.168.10.106:1234")
        
    val systemPrompt: StateFlow<String> = dataRepository.systemPrompt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val selectedModel: StateFlow<String> = dataRepository.selectedModel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "google/gemma-4-e4b")

    val availableModels: StateFlow<List<String>> = dataRepository.availableModels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val voiceTriggerMode: StateFlow<String> = dataRepository.voiceTriggerMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "ALWAYS")

    val voiceKeywords: StateFlow<String> = dataRepository.voiceKeywords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "これ何,教えて,解析")

    val ttsServerUrl: StateFlow<String> = dataRepository.ttsServerUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "192.168.10.106:8080")

    val ttsApiKey: StateFlow<String> = dataRepository.ttsApiKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "w1PdXyb9aWBGkkf4A7U4wfXhDBv1QP3ZcBH1Mgqr4kk")

    val ttsSpeaker: StateFlow<String> = dataRepository.ttsSpeaker
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "ずんだもん")

    val availableSpeakers: StateFlow<List<String>> = dataRepository.availableSpeakers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val autoAnalysisEnabled: StateFlow<Boolean> = dataRepository.autoAnalysisEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val autoAnalysisInterval: StateFlow<Int> = dataRepository.autoAnalysisInterval
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 10)

    fun saveSettings(url: String, prompt: String, model: String) {
        viewModelScope.launch {
            dataRepository.updateSettings(url, prompt, model)
        }
    }

    fun saveVoiceSettings(mode: String, keywords: String) {
        viewModelScope.launch {
            dataRepository.updateVoiceSettings(mode, keywords)
        }
    }

    fun saveTtsSettings(url: String, apiKey: String, speaker: String) {
        viewModelScope.launch {
            dataRepository.updateTtsSettings(url, apiKey, speaker)
        }
    }

    fun saveAutoAnalysisSettings(enabled: Boolean, interval: Int) {
        viewModelScope.launch {
            dataRepository.updateAutoAnalysisSettings(enabled, interval)
        }
    }

    fun refreshModels(url: String) {
        viewModelScope.launch {
            dataRepository.fetchAvailableModels(url)
        }
    }

    fun refreshSpeakers(url: String, apiKey: String) {
        viewModelScope.launch {
            dataRepository.fetchAvailableSpeakers(url, apiKey)
        }
    }

    fun testVoice(text: String, speakerName: String) {
        viewModelScope.launch {
            val audioData = dataRepository.synthesizeSpeech(text, speakerName)
            if (audioData != null) {
                playAudio(audioData)
            }
        }
    }

    private fun playAudio(data: ByteArray) {
        try {
            val tempFile = java.io.File.createTempFile("tts_test", ".mp3")
            tempFile.writeBytes(data)
            
            val mediaPlayer = android.media.MediaPlayer()
            mediaPlayer.setDataSource(tempFile.absolutePath)
            mediaPlayer.prepare()
            mediaPlayer.start()
            
            mediaPlayer.setOnCompletionListener {
                it.release()
                tempFile.delete()
            }
        } catch (e: Exception) {
            android.util.Log.e("SettingsViewModel", "Playback failed", e)
        }
    }
}
