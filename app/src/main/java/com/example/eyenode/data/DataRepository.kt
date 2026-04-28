package com.example.eyenode.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import android.graphics.Bitmap
import android.util.Log
import android.util.Base64
import java.io.ByteArrayOutputStream
import io.ktor.http.*

@Serializable
data class ModelData(val id: String)

@Serializable
data class ModelsResponse(val data: List<ModelData>)

data class AutoAnalysisSettings(
    val enabled: Boolean = false,
    val interval: Int = 10
)

interface DataRepository {
  val data: Flow<List<String>>
  val serverUrl: Flow<String>
  val systemPrompt: Flow<String>
  val selectedModel: Flow<String>
  val availableModels: Flow<List<String>>
  val voiceTriggerMode: Flow<String> // "ALWAYS" or "TAP"
  val voiceKeywords: Flow<String>
  val ttsServerUrl: Flow<String>
  val ttsApiKey: Flow<String>
  val ttsSpeaker: Flow<String>
  val availableSpeakers: Flow<List<String>>
  val autoAnalysisSettings: Flow<AutoAnalysisSettings>
  
  suspend fun updateSettings(url: String, prompt: String, model: String)
  suspend fun updateVoiceSettings(mode: String, keywords: String)
  suspend fun updateTtsSettings(url: String, apiKey: String, speaker: String)
  suspend fun updateAutoAnalysisSettings(enabled: Boolean, interval: Int)
  suspend fun fetchAvailableModels(url: String): List<String>
  suspend fun fetchAvailableSpeakers(url: String, apiKey: String): List<String>
  suspend fun synthesizeSpeech(text: String, speakerName: String? = null): ByteArray?
  suspend fun analyzeImage(
      bitmap: Bitmap, 
      position: com.example.eyenode.ai.HandGestureDetector.FingerPosition? = null, 
      voiceText: String? = null,
      isDialogue: Boolean = false
  ): String
}

@Serializable
data class Speaker(
    val name: String,
    val speaker_uuid: String,
    val styles: List<SpeakerStyle>
)

@Serializable
data class SpeakerStyle(
    val name: String,
    val id: Int
)

@Serializable
data class TtsRequest(
    val text: String,
    val speaker: Int,
    val format: String = "mp3"
)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: List<ChatContent>
)

@Serializable
data class ChatContent(
    val type: String,
    val text: String? = null,
    val image_url: ImageUrl? = null
)

@Serializable
data class ImageUrl(val url: String)

@Serializable
data class ChatResponse(
    val choices: List<ChatChoice>
)

@Serializable
data class ChatChoice(
    val message: ResponseMessage
)

@Serializable
data class ResponseMessage(
    val content: String
)

class DefaultDataRepository : DataRepository {
  override val data: Flow<List<String>> = flow { emit(listOf("Android")) }
  
  private val _serverUrl = MutableStateFlow("192.168.10.106:1234")
  override val serverUrl = _serverUrl.asStateFlow()
  
  private val _systemPrompt = MutableStateFlow("あなたは可愛い物が好きな AIアシスタントです。可愛いものを見つけたら名前を教えてくれたり、調べてくれたりします。回答は簡潔に、短く要約して伝えてください。")
  override val systemPrompt = _systemPrompt.asStateFlow()

  private val _selectedModel = MutableStateFlow("google/gemma-4-e4b")
  override val selectedModel = _selectedModel.asStateFlow()

  private val _availableModels = MutableStateFlow<List<String>>(emptyList())
  override val availableModels = _availableModels.asStateFlow()

  private val _voiceTriggerMode = MutableStateFlow("ALWAYS")
  override val voiceTriggerMode = _voiceTriggerMode.asStateFlow()

  private val _voiceKeywords = MutableStateFlow("これ何,教えて,解析")
  override val voiceKeywords = _voiceKeywords.asStateFlow()

  private val _ttsServerUrl = MutableStateFlow("192.168.10.106:8080")
  override val ttsServerUrl = _ttsServerUrl.asStateFlow()

  private val _ttsApiKey = MutableStateFlow("w1PdXyb9aWBGkkf4A7U4wfXhDBv1QP3ZcBH1Mgqr4kk")
  override val ttsApiKey = _ttsApiKey.asStateFlow()

  private val _ttsSpeaker = MutableStateFlow("ずんだもん")
  override val ttsSpeaker = _ttsSpeaker.asStateFlow()

  private val _availableSpeakers = MutableStateFlow<List<String>>(emptyList())
  override val availableSpeakers = _availableSpeakers.asStateFlow()

  private val _autoAnalysisSettings = MutableStateFlow(AutoAnalysisSettings(enabled = false, interval = 10))
  override val autoAnalysisSettings = _autoAnalysisSettings.asStateFlow()

  private val speakerIdMap = mutableMapOf<String, Int>()

  private val client = HttpClient(Android) {
    install(ContentNegotiation) {
      json(Json { 
        ignoreUnknownKeys = true 
        coerceInputValues = true
      })
    }
  }

  override suspend fun updateSettings(url: String, prompt: String, model: String) {
    _serverUrl.value = url
    _systemPrompt.value = prompt
    _selectedModel.value = model
  }

  override suspend fun updateVoiceSettings(mode: String, keywords: String) {
    _voiceTriggerMode.value = mode
    _voiceKeywords.value = keywords
  }

  override suspend fun fetchAvailableModels(url: String): List<String> {
    return try {
        // http:// が付いていない場合は補完する
        val baseUrl = if (url.startsWith("http")) url else "http://$url"
        val response: ModelsResponse = client.get("$baseUrl/v1/models").body()
        val models = response.data.map { it.id }
        _availableModels.value = models
        models
    } catch (e: Exception) {
        emptyList()
    }
  }

  override suspend fun updateTtsSettings(url: String, apiKey: String, speaker: String) {
    _ttsServerUrl.value = url
    _ttsApiKey.value = apiKey
    _ttsSpeaker.value = speaker
  }

  override suspend fun updateAutoAnalysisSettings(enabled: Boolean, interval: Int) {
    Log.d("DataRepository", "Updating auto analysis: enabled=$enabled, interval=$interval")
    _autoAnalysisSettings.value = AutoAnalysisSettings(enabled, interval)
  }

  override suspend fun fetchAvailableSpeakers(url: String, apiKey: String): List<String> {
    return try {
        val baseUrl = if (url.startsWith("http")) url else "http://$url"
        val response: List<Speaker> = client.get("$baseUrl/api/speakers") {
            header("X-API-KEY", apiKey)
        }.body()
        
        val flattenedList = mutableListOf<String>()
        speakerIdMap.clear()
        
        response.forEach { speaker ->
            speaker.styles.forEach { style ->
                val displayName = "${speaker.name} (${style.name})"
                flattenedList.add(displayName)
                speakerIdMap[displayName] = style.id
            }
        }
        
        _availableSpeakers.value = flattenedList
        flattenedList
    } catch (e: Exception) {
        Log.e("DataRepository", "Failed to fetch speakers", e)
        emptyList()
    }
  }

  override suspend fun synthesizeSpeech(text: String, speakerName: String?): ByteArray? {
    return try {
        val url = _ttsServerUrl.value
        val apiKey = _ttsApiKey.value
        val currentSpeakerName = speakerName ?: _ttsSpeaker.value
        
        // Ensure speakerIdMap is populated
        if (speakerIdMap.isEmpty()) {
            Log.d("DataRepository", "speakerIdMap is empty, fetching speakers...")
            fetchAvailableSpeakers(url, apiKey)
        }
        
        val baseUrl = if (url.startsWith("http")) url else "http://$url"
        val speakerId = speakerIdMap[currentSpeakerName] ?: 1
        Log.d("DataRepository", "Synthesizing speech for speaker: $currentSpeakerName (ID: $speakerId)")
        
        val response = client.post("$baseUrl/api/synthesis") {
            header("X-API-KEY", apiKey)
            contentType(ContentType.Application.Json)
            setBody(TtsRequest(text = text, speaker = speakerId))
        }
        
        if (response.status == HttpStatusCode.OK) {
            response.body<ByteArray>()
        } else {
            null
        }
    } catch (e: Exception) {
        Log.e("DataRepository", "TTS failed", e)
        null
    }
  }

  override suspend fun analyzeImage(
      bitmap: Bitmap,
      position: com.example.eyenode.ai.HandGestureDetector.FingerPosition?,
      voiceText: String?,
      isDialogue: Boolean
  ): String {
    // 1. プロンプトの組み立て
    val contextInfo = StringBuilder()
    
    if (isDialogue) {
        contextInfo.append("あなたは親切なAIアシスタントです。ユーザーと楽しく日常会話をしてください。画像の内容については、ユーザーから特に質問がない限り触れる必要はありません。")
        if (!voiceText.isNullOrEmpty()) {
            contextInfo.append("\nユーザー: ${voiceText}")
        }
    } else {
        if (position != null) {
            val xPercent = (position.x * 100).toInt()
            val yPercent = (position.y * 100).toInt()
            contextInfo.append("ユーザーは画像の水平方向 ${xPercent}%、垂直方向 ${yPercent}% の位置（左上を0%とする）を指差しています。")
        }
        if (!voiceText.isNullOrEmpty()) {
            contextInfo.append("ユーザーからの音声指示: 「${voiceText}」")
        }
    }
    
    val finalPrompt = if (isDialogue) {
        contextInfo.toString()
    } else if (contextInfo.isNotEmpty()) {
        "${contextInfo}\n\nもし画像内にユーザーの指が写っている場合は、その指が指し示している物体を最優先で特定し、詳しく解説してください。"
    } else {
        "この画像を解析してください。もし画像内に指差しをしている手や指が写っている場合は、その指が指し示している物体を優先的に特定して解説してください。"
    }

    // 2. 画像の処理と送信
    return try {
        val url = _serverUrl.value
        val baseUrl = if (url.startsWith("http")) url else "http://$url"
        
        val messages = mutableListOf<ChatMessage>()
        val systemPromptValue = _systemPrompt.value
        if (systemPromptValue.isNotEmpty()) {
            messages.add(ChatMessage(
                role = "system", 
                content = listOf(ChatContent(type = "text", text = systemPromptValue))
            ))
        }

        if (isDialogue) {
            // 対話モードの場合はテキストのみ送信
            messages.add(ChatMessage(
                role = "user", 
                content = listOf(ChatContent(type = "text", text = finalPrompt))
            ))
        } else {
            // 通常モードは画像も送信
            val maxDimension = 512
            val width = bitmap.width
            val height = bitmap.height
            val (newWidth, newHeight) = if (width > height) {
                val ratio = width.toFloat() / maxDimension
                maxDimension to (height / ratio).toInt()
            } else {
                val ratio = height.toFloat() / maxDimension
                (width / ratio).toInt() to maxDimension
            }
            
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
            val stream = ByteArrayOutputStream()
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
            val base64Image = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
            
            if (resizedBitmap != bitmap) {
                resizedBitmap.recycle()
            }

            messages.add(ChatMessage(
                role = "user", 
                content = listOf(
                    ChatContent(type = "text", text = finalPrompt),
                    ChatContent(type = "image_url", image_url = ImageUrl(url = "data:image/jpeg;base64,$base64Image"))
                )
            ))
        }

        val request = ChatRequest(
            model = _selectedModel.value,
            messages = messages
        )
        
        val response: ChatResponse = client.post("$baseUrl/v1/chat/completions") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
        
        response.choices.firstOrNull()?.message?.content ?: "No response from AI"
    } catch (e: Exception) {
        "Error: ${e.localizedMessage}"
    }
  }
}
