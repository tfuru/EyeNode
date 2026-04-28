package com.example.eyenode.ui.main

import android.Manifest
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BackHand
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.eyenode.ai.HandGestureDetector
import com.example.eyenode.ai.VoiceTriggerHandler
import com.example.eyenode.data.DefaultDataRepository
import com.example.eyenode.theme.MyApplicationTheme
import com.example.eyenode.ui.CameraPreview

@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    viewModel: MainScreenViewModel = viewModel { MainScreenViewModel(DefaultDataRepository()) },
    modifier: Modifier = Modifier
) {
  var hasCameraPermission by remember { mutableStateOf(false) }
  val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    hasCameraPermission = isGranted
  }

  LaunchedEffect(Unit) {
    if (!hasCameraPermission) {
      permissionLauncher.launch(Manifest.permission.CAMERA)
    }
  }

  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val logs by viewModel.logs.collectAsStateWithLifecycle()
  val voiceTriggerMode by viewModel.voiceTriggerMode.collectAsStateWithLifecycle()
  val voiceKeywords by viewModel.dataRepository.voiceKeywords.collectAsStateWithLifecycle(initialValue = "")
  val audioLevel by viewModel.audioLevel.collectAsStateWithLifecycle()
  val captureRequested by viewModel.captureRequested.collectAsStateWithLifecycle()
  val lastAnalyzedImage by viewModel.lastAnalyzedImage.collectAsStateWithLifecycle()
  val isTriggerLocked by viewModel.isTriggerLocked.collectAsStateWithLifecycle()
  val isAnalyzing by viewModel.isAnalyzing.collectAsStateWithLifecycle()
  val isAiSpeaking by viewModel.isAiSpeaking.collectAsStateWithLifecycle()
  val isContinuousDialogue by viewModel.isContinuousDialogue.collectAsStateWithLifecycle()

  val context = LocalContext.current
  val voiceHandler = remember {
      VoiceTriggerHandler(
          context = context,
          onTrigger = { text, isDialogue ->
              viewModel.addLog(if (isDialogue) "対話リクエスト: $text" else "音声トリガー検知: $text")
              viewModel.requestCapture(text, isDialogue)
          },
          onCancelDialogue = { viewModel.unlockTriggers() },
          onLevelChanged = { viewModel.updateAudioLevel(it) },
          onLog = { viewModel.addLog(it) }
      )
  }

  LaunchedEffect(voiceTriggerMode, voiceKeywords) {
      voiceHandler.updateSettings(voiceTriggerMode, voiceKeywords)
  }

  // AI発話中のマイク制御
  LaunchedEffect(isAiSpeaking) {
      if (isAiSpeaking) {
          voiceHandler.stopListening()
      } else if (voiceTriggerMode == "ALWAYS") {
          voiceHandler.startListening()
      }
  }

  // 対話モード終了時の状態同期
  LaunchedEffect(isContinuousDialogue) {
      if (!isContinuousDialogue) {
          voiceHandler.stopDialogueMode()
      }
  }

  DisposableEffect(Unit) {
      onDispose {
          voiceHandler.release()
      }
  }

  LaunchedEffect(Unit) {
      viewModel.dialogueRestartRequested.collect {
          voiceHandler.startDialogue()
      }
  }

  when (state) {
    MainScreenUiState.Loading -> {
      // Blank
    }
    is MainScreenUiState.Success -> {
      if (hasCameraPermission) {
        MainScreen(
            data = (state as MainScreenUiState.Success).data,
            logs = logs,
            voiceTriggerMode = voiceTriggerMode,
            audioLevel = audioLevel,
            captureRequested = captureRequested,
            lastAnalyzedImage = lastAnalyzedImage,
            isTriggerLocked = isTriggerLocked,
            isAnalyzing = isAnalyzing,
            isContinuousDialogue = isContinuousDialogue,
            onLog = { viewModel.addLog(it) },
            onAnalyze = { bitmap, pos -> viewModel.analyzeImage(bitmap, pos) },
            onFist = { viewModel.stopContinuousDialogue() },
            onThumbsUp = { 
                voiceHandler.startDialogue()
                viewModel.lockTriggers(300000L, true) // 対話モードとしてロック (5分)
            },
            onCaptureCompleted = { viewModel.onCaptureCompleted() },
            onSettingsClick = { onItemClick(com.example.eyenode.Settings) },
            modifier = modifier
        )
      } else {
        Text("カメラ権限が必要です", modifier = modifier)
      }
    }
    is MainScreenUiState.Error -> {
      Text("Error loading data: ${(state as MainScreenUiState.Error).throwable.message}")
    }
  }
}

@Composable
internal fun MainScreen(
    data: List<String>,
    logs: List<String>,
    voiceTriggerMode: String,
    audioLevel: Float,
    captureRequested: Boolean,
    lastAnalyzedImage: Bitmap?,
    isTriggerLocked: Boolean,
    isAnalyzing: Boolean,
    isContinuousDialogue: Boolean,
    onLog: (String) -> Unit,
    onAnalyze: (Bitmap, com.example.eyenode.ai.HandGestureDetector.FingerPosition?) -> Unit,
    onFist: () -> Unit,
    onThumbsUp: () -> Unit,
    onCaptureCompleted: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isCameraConnected by remember { mutableStateOf(false) }
    var isHandDetected by remember { mutableStateOf(false) }
    var cameraResolution by remember { mutableStateOf<String?>(null) }
    var showImagePopup by remember { mutableStateOf(false) }

    // 対話モード中のパルスアニメーション
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha"
    )

    Column(modifier = modifier.fillMaxSize().background(Color.Black)) {
        // カメラプレビューエリア
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color.DarkGray)
        ) {
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                captureRequested = captureRequested,
                onCaptureCompleted = onCaptureCompleted,
                onCameraStateChanged = { connected ->
                    isCameraConnected = connected
                    if (!connected) cameraResolution = null
                    onLog(if (connected) "Camera connected" else "Camera disconnected")
                },
                onResolutionChanged = { w, h ->
                    cameraResolution = "$w x $h"
                },
                onPoint = { bitmap, pos, gesture -> 
                    // グーはロックに関わらず最優先
                    if (gesture == HandGestureDetector.Gesture.FIST) {
                        onFist()
                        return@CameraPreview
                    }

                    if (isTriggerLocked && gesture != HandGestureDetector.Gesture.NONE) {
                        // ロック中はジェスチャーを無視
                        return@CameraPreview
                    }
                    
                    if (gesture == HandGestureDetector.Gesture.PHONE_CALL) {
                        onThumbsUp()
                    } else {
                        onAnalyze(bitmap, pos)
                    }
                },
                onHandDetected = { isHandDetected = it }
            )

            // サムネイル表示（左下）
            lastAnalyzedImage?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Last Analyzed Image",
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .size(100.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(2.dp, Color.White, RoundedCornerShape(8.dp))
                        .clickable { showImagePopup = true },
                    contentScale = ContentScale.Crop
                )
            }

            // 拡大ポップアップ
            if (showImagePopup && lastAnalyzedImage != null) {
                Dialog(onDismissRequest = { showImagePopup = false }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.95f)
                            .fillMaxHeight(0.8f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black)
                            .border(1.dp, Color.DarkGray, RoundedCornerShape(16.dp))
                            .clickable { showImagePopup = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = lastAnalyzedImage.asImageBitmap(),
                            contentDescription = "Full Image",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }

            // 左上のインジケーター群
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 解像度の表示
                if (isCameraConnected && cameraResolution != null) {
                    Box(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "RES: $cameraResolution",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 10.sp
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 手の検知アイコン
                    Box(contentAlignment = Alignment.Center) {
                        // 対話モード中のパルスエフェクト
                        if (isContinuousDialogue) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .graphicsLayer {
                                        scaleX = pulseScale
                                        scaleY = pulseScale
                                    }
                                    .background(Color(0xFFFF9800).copy(alpha = pulseAlpha), CircleShape)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(
                                    if (isTriggerLocked) Color(0xFFFF9800).copy(alpha = 0.8f)
                                    else if (isHandDetected) Color.Green.copy(alpha = 0.6f) 
                                    else Color.Gray.copy(alpha = 0.6f), 
                                    RoundedCornerShape(16.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isContinuousDialogue) Icons.Default.Chat else Icons.Default.BackHand,
                                contentDescription = if (isContinuousDialogue) "Dialogue Mode" else "Hand Detected",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // 音声トリガーモードアイコン
                    val micScale by animateFloatAsState(
                        targetValue = 1f + (audioLevel * 0.4f),
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "MicScale"
                    )
                    val micColor by animateColorAsState(
                        targetValue = if (voiceTriggerMode == "ALWAYS") {
                            if (audioLevel > 0.2f) Color(0xFF42A5F5) else Color.Blue.copy(alpha = 0.6f)
                        } else Color.Gray.copy(alpha = 0.6f),
                        label = "MicColor"
                    )

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .graphicsLayer {
                                scaleX = micScale
                                scaleY = micScale
                            }
                            .background(micColor, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (voiceTriggerMode == "ALWAYS") Icons.Default.Mic else Icons.Default.MicNone,
                            contentDescription = "Voice Mode",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // 設定ボタン（歯車アイコン）
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Color.White
                )
            }

            if (!isCameraConnected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "USBカメラを接続してください",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        // ログ表示エリア（全体を占めるように拡大）
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.5f) // 下部半分をログに割り当て
                .padding(8.dp)
                .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.3f))
                .padding(12.dp)
        ) {
            Text(
                text = "SYSTEM LOG",
                color = Color.Gray,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                reverseLayout = true
            ) {
                items(logs.reversed()) { log ->
                    Text(
                        text = log,
                        color = Color.White,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }

        // プログレスバー (解析中)
        if (isAnalyzing) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = Color.Green,
                trackColor = Color.Green.copy(alpha = 0.2f)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
  MyApplicationTheme { 
    MainScreen(
        data = listOf("Android"), 
        logs = listOf("Test log 1", "Test log 2"),
        voiceTriggerMode = "ALWAYS",
        audioLevel = 0.5f,
        captureRequested = false,
        lastAnalyzedImage = null,
        isTriggerLocked = false,
        isAnalyzing = false,
        isContinuousDialogue = false,
        onLog = {},
        onAnalyze = { bitmap: android.graphics.Bitmap, pos: com.example.eyenode.ai.HandGestureDetector.FingerPosition? -> },
        onFist = {},
        onThumbsUp = {},
        onCaptureCompleted = {},
        onSettingsClick = {}
    ) 
  }
}

@Preview(showBackground = true, widthDp = 340)
@Composable
fun MainScreenPortraitPreview() {
  MyApplicationTheme { 
    MainScreen(
        data = listOf("Android"), 
        logs = listOf("Test log 1", "Test log 2"),
        voiceTriggerMode = "TAP",
        audioLevel = 0.1f,
        captureRequested = false,
        lastAnalyzedImage = null,
        isTriggerLocked = false,
        isAnalyzing = false,
        isContinuousDialogue = false,
        onLog = {},
        onAnalyze = { bitmap: android.graphics.Bitmap, pos: com.example.eyenode.ai.HandGestureDetector.FingerPosition? -> },
        onFist = {},
        onThumbsUp = {},
        onCaptureCompleted = {},
        onSettingsClick = {}
    ) 
  }
}
