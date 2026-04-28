package com.example.eyenode.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.eyenode.data.DefaultDataRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel
) {
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()
    val systemPrompt by viewModel.systemPrompt.collectAsStateWithLifecycle()
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    val availableModels by viewModel.availableModels.collectAsStateWithLifecycle()
    val voiceTriggerMode by viewModel.voiceTriggerMode.collectAsStateWithLifecycle()
    val voiceKeywords by viewModel.voiceKeywords.collectAsStateWithLifecycle()
    val ttsServerUrl by viewModel.ttsServerUrl.collectAsStateWithLifecycle()
    val ttsApiKey by viewModel.ttsApiKey.collectAsStateWithLifecycle()
    val ttsSpeaker by viewModel.ttsSpeaker.collectAsStateWithLifecycle()
    val availableSpeakers by viewModel.availableSpeakers.collectAsStateWithLifecycle()
    val autoAnalysisEnabled by viewModel.autoAnalysisEnabled.collectAsStateWithLifecycle()
    val autoAnalysisInterval by viewModel.autoAnalysisInterval.collectAsStateWithLifecycle()

    var urlInput by remember { mutableStateOf("") }
    var promptInput by remember { mutableStateOf("") }
    var modelInput by remember { mutableStateOf("") }
    var voiceModeInput by remember { mutableStateOf("ALWAYS") }
    var voiceKeywordsInput by remember { mutableStateOf("") }
    var ttsServerUrlInput by remember { mutableStateOf("") }
    var ttsApiKeyInput by remember { mutableStateOf("") }
    var ttsSpeakerInput by remember { mutableStateOf("") }
    var autoAnalysisEnabledInput by remember { mutableStateOf(false) }
    var autoAnalysisIntervalInput by remember { mutableStateOf("") }
    var isModelMenuExpanded by remember { mutableStateOf(false) }
    var isSpeakerMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(serverUrl, systemPrompt, selectedModel, voiceTriggerMode, voiceKeywords, ttsServerUrl, ttsApiKey, ttsSpeaker, autoAnalysisEnabled, autoAnalysisInterval) {
        urlInput = serverUrl
        promptInput = systemPrompt
        modelInput = selectedModel
        voiceModeInput = voiceTriggerMode
        voiceKeywordsInput = voiceKeywords
        ttsServerUrlInput = ttsServerUrl
        ttsApiKeyInput = ttsApiKey
        ttsSpeakerInput = ttsSpeaker
        autoAnalysisEnabledInput = autoAnalysisEnabled
        autoAnalysisIntervalInput = autoAnalysisInterval.toString()
    }

    LaunchedEffect(Unit) {
        viewModel.refreshModels(serverUrl)
        viewModel.refreshSpeakers(ttsServerUrl, ttsApiKey)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("接続設定", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // LLM Server Settings
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "LLMサーバー設定",
                    color = Color.Gray,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("IPアドレスとポート", color = Color.Gray) },
                    placeholder = { Text("192.168.10.106:1234", color = Color.DarkGray) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.Gray
                    )
                )
            }

            // Model Selection Settings
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "モデル選択",
                        color = Color.Gray,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { viewModel.refreshModels(urlInput) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Models", tint = Color.Gray)
                    }
                }
                
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = modelInput,
                        onValueChange = { modelInput = it },
                        readOnly = true,
                        label = { Text("使用するモデル", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        trailingIcon = {
                            IconButton(onClick = { isModelMenuExpanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Expand", tint = Color.White)
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.Gray
                        )
                    )
                    
                    DropdownMenu(
                        expanded = isModelMenuExpanded,
                        onDismissRequest = { isModelMenuExpanded = false },
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .background(Color.DarkGray)
                    ) {
                        availableModels.forEach { modelName ->
                            DropdownMenuItem(
                                text = { Text(modelName, color = Color.White) },
                                onClick = {
                                    modelInput = modelName
                                    isModelMenuExpanded = false
                                }
                            )
                        }
                        if (availableModels.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("モデルが見つかりません", color = Color.Gray) },
                                onClick = { isModelMenuExpanded = false }
                            )
                        }
                    }
                }
            }

            // AI Agent Settings
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "AIアシスタント設定",
                    color = Color.Gray,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = promptInput,
                    onValueChange = { promptInput = it },
                    label = { Text("システムプロンプト（キャラ設定）", color = Color.Gray) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.Gray
                    )
                )
            }

            // Auto Analysis Settings
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "自動解析設定",
                    color = Color.Gray,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("定期的な自動解析を有効にする", color = Color.White, fontSize = 14.sp)
                    Switch(
                        checked = autoAnalysisEnabledInput,
                        onCheckedChange = { autoAnalysisEnabledInput = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color.Gray)
                    )
                }
                OutlinedTextField(
                    value = autoAnalysisIntervalInput,
                    onValueChange = { autoAnalysisIntervalInput = it },
                    label = { Text("解析間隔（秒）", color = Color.Gray) },
                    placeholder = { Text("10", color = Color.DarkGray) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    enabled = autoAnalysisEnabledInput,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.Gray,
                        disabledBorderColor = Color.DarkGray
                    )
                )
            }

            // Voice Trigger Settings
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "音声トリガー設定",
                    color = Color.Gray,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = voiceModeInput == "ALWAYS",
                            onClick = { voiceModeInput = "ALWAYS" },
                            colors = RadioButtonDefaults.colors(selectedColor = Color.White, unselectedColor = Color.Gray)
                        )
                        Text("常時待機", color = Color.White, fontSize = 14.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = voiceModeInput == "TAP",
                            onClick = { voiceModeInput = "TAP" },
                            colors = RadioButtonDefaults.colors(selectedColor = Color.White, unselectedColor = Color.Gray)
                        )
                        Text("タップ", color = Color.White, fontSize = 14.sp)
                    }
                }

                OutlinedTextField(
                    value = voiceKeywordsInput,
                    onValueChange = { voiceKeywordsInput = it },
                    label = { Text("キーワード (カンマ区切り)", color = Color.Gray) },
                    placeholder = { Text("これ何,教えて,解析", color = Color.DarkGray) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.Gray
                    )
                )
            }

            // Speech Synthesis Settings
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "音声合成設定 (VOICEVOX)",
                    color = Color.Gray,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = ttsServerUrlInput,
                    onValueChange = { ttsServerUrlInput = it },
                    label = { Text("IPアドレスとポート", color = Color.Gray) },
                    placeholder = { Text("192.168.10.106:8080", color = Color.DarkGray) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.Gray
                    )
                )
                OutlinedTextField(
                    value = ttsApiKeyInput,
                    onValueChange = { ttsApiKeyInput = it },
                    label = { Text("API Key", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.Gray
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "話者選択",
                        color = Color.Gray,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { viewModel.refreshSpeakers(ttsServerUrlInput, ttsApiKeyInput) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Speakers", tint = Color.Gray)
                    }
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = ttsSpeakerInput,
                        onValueChange = { ttsSpeakerInput = it },
                        readOnly = true,
                        label = { Text("話者（キャラクター）", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        trailingIcon = {
                            IconButton(onClick = { isSpeakerMenuExpanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Expand", tint = Color.White)
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.Gray
                        )
                    )
                    
                    DropdownMenu(
                        expanded = isSpeakerMenuExpanded,
                        onDismissRequest = { isSpeakerMenuExpanded = false },
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .background(Color.DarkGray)
                    ) {
                        availableSpeakers.forEach { speakerName ->
                            DropdownMenuItem(
                                text = { Text(speakerName, color = Color.White) },
                                onClick = {
                                    ttsSpeakerInput = speakerName
                                    isSpeakerMenuExpanded = false
                                }
                            )
                        }
                        if (availableSpeakers.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("話者が見つかりません", color = Color.Gray) },
                                onClick = { isSpeakerMenuExpanded = false }
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = { 
                        viewModel.testVoice("こんにちは。私は${ttsSpeakerInput}です。よろしくお願いします。", ttsSpeakerInput)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray)
                ) {
                    Text("声をテストする", fontSize = 14.sp)
                }
            }


            Spacer(modifier = Modifier.height(24.dp))

            val coroutineScope = rememberCoroutineScope()
            var isSaving by remember { mutableStateOf(false) }

            Button(
                onClick = {
                    if (isSaving) return@Button
                    isSaving = true
                    coroutineScope.launch {
                        viewModel.saveAllSettings(
                            urlInput, promptInput, modelInput,
                            voiceModeInput, voiceKeywordsInput,
                            ttsServerUrlInput, ttsApiKeyInput, ttsSpeakerInput,
                            autoAnalysisEnabledInput, 
                            autoAnalysisIntervalInput.toIntOrNull() ?: 10
                        )
                        isSaving = false
                        onBack()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black)
                } else {
                    Text("設定を保存", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
