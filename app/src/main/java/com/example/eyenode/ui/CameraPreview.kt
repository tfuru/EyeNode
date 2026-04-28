package com.example.eyenode.ui

import android.content.Context
import android.content.ContextWrapper
import android.hardware.usb.UsbDevice
import android.util.Log
import android.view.SurfaceHolder
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.jiangdg.ausbc.CameraClient
import com.jiangdg.ausbc.callback.IDeviceConnectCallBack
import com.jiangdg.ausbc.camera.CameraUvcStrategy
import com.jiangdg.ausbc.widget.AspectRatioTextureView
import com.serenegiant.usb.USBMonitor
import com.example.eyenode.ai.HandGestureDetector
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean

class CameraContextWrapper(base: Context, private val lifecycleOwner: LifecycleOwner) : 
    ContextWrapper(base), LifecycleOwner {
    override val lifecycle: Lifecycle get() = lifecycleOwner.lifecycle
}

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    captureRequested: Boolean = false,
    onCaptureCompleted: () -> Unit = {},
    onCameraStateChanged: (Boolean) -> Unit,
    onResolutionChanged: (Int, Int) -> Unit = { _, _ -> },
    onPoint: (android.graphics.Bitmap, HandGestureDetector.FingerPosition?, HandGestureDetector.Gesture) -> Unit = { _, _, _ -> },
    onHandDetected: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val activity = remember(context) { context as ComponentActivity }
    val cameraContext = remember(activity) { CameraContextWrapper(activity, activity) }
    
    val textureView = remember { AspectRatioTextureView(cameraContext) }

    // 音声トリガーなどによる外部からのキャプチャ要求
    LaunchedEffect(captureRequested) {
        if (captureRequested) {
            textureView.getBitmap()?.let { bitmap ->
                onPoint(bitmap, null, HandGestureDetector.Gesture.NONE) // 音声の場合は位置情報なし
            }
            onCaptureCompleted()
        }
    }
    val isTextureAvailable = remember { AtomicBoolean(false) }
    
    var videoWidth by remember { mutableStateOf(0) }
    var videoHeight by remember { mutableStateOf(0) }

    val cameraClient: CameraClient = remember(cameraContext) {
        CameraClient.newBuilder(cameraContext)
            .setCameraStrategy(CameraUvcStrategy(cameraContext))
            .openDebug(true)
            .build()
    }

    DisposableEffect(cameraClient) {
        val callback = object : IDeviceConnectCallBack {
            override fun onAttachDev(device: UsbDevice?) {
                onCameraStateChanged(true)
                if (isTextureAvailable.get()) {
                    cameraClient.openCamera(textureView)
                }
            }

            override fun onDetachDec(device: UsbDevice?) {
                onCameraStateChanged(false)
            }

            override fun onConnectDev(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
                onCameraStateChanged(true)
                try {
                    val getPreviewSizeMethod = cameraClient.javaClass.methods.find { 
                        it.name == "getPreviewSize" || it.name == "getResolution" 
                    }
                    val size = getPreviewSizeMethod?.invoke(cameraClient)
                    if (size != null) {
                        videoWidth = size.javaClass.getField("width").getInt(size)
                        videoHeight = size.javaClass.getField("height").getInt(size)
                        onResolutionChanged(videoWidth, videoHeight)
                    }
                } catch (e: Exception) {
                    Log.e("CameraPreview", "Failed to get resolution", e)
                }
            }

            override fun onDisConnectDec(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
                onCameraStateChanged(false)
            }

            override fun onCancelDev(device: UsbDevice?) {
                Log.d("CameraPreview", "USB Connection Cancelled")
            }
        }

        try {
            val getStrategyMethod = cameraClient.javaClass.getMethod("getCameraStrategy")
            val strategy = getStrategyMethod.invoke(cameraClient)
            val setListenerMethod = strategy.javaClass.getMethod("setDeviceConnectStatusListener", IDeviceConnectCallBack::class.java)
            setListenerMethod.invoke(strategy, callback)
        } catch (e: Exception) {
            Log.e("CameraPreview", "Failed to set listener", e)
        }

        onDispose {
            cameraClient.closeCamera()
        }
    }

    LaunchedEffect(cameraClient, isTextureAvailable.get(), videoWidth, videoHeight) {
        val detector = HandGestureDetector(context)
        var pointingFrames = 0
        
        try {
            while (true) {
                if (isTextureAvailable.get() && textureView.width > 0 && textureView.height > 0) {
                    val bitmap = textureView.getBitmap(textureView.width, textureView.height)
                    if (bitmap != null) {
                        val (gesture, fingerPos) = detector.processFrame(bitmap)
                        
                        onHandDetected(gesture != HandGestureDetector.Gesture.NONE)
                        
                        if (gesture == HandGestureDetector.Gesture.POINTING || gesture == HandGestureDetector.Gesture.PHONE_CALL) {
                            Log.i("CameraPreview", "Gesture $gesture detected!")
                            onPoint(bitmap, fingerPos, gesture)
                            // 連続発火を防ぐために少し待つ
                            kotlinx.coroutines.delay(2000) 
                        }
                    } else {
                        onHandDetected(false)
                    }
                }
                kotlinx.coroutines.delay(500) // 2FPS程度で解析
            }
        } finally {
            detector.close()
        }
    }

    LaunchedEffect(videoWidth, videoHeight) {
        if (videoWidth > 0 && videoHeight > 0) {
            textureView.setAspectRatio(videoWidth, videoHeight)
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize().clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        val containerWidth = maxWidth
        val containerHeight = maxHeight
        
        val targetModifier = if (videoWidth > 0 && videoHeight > 0) {
            val videoAspectRatio = videoWidth.toFloat() / videoHeight
            val containerAspectRatio = containerWidth.value / containerHeight.value
            
            if (videoAspectRatio > containerAspectRatio) {
                Modifier.size(width = containerHeight * videoAspectRatio, height = containerHeight)
            } else {
                Modifier.size(width = containerWidth, height = containerWidth / videoAspectRatio)
            }
        } else {
            Modifier.fillMaxSize()
        }

        AndroidView(
            modifier = targetModifier,
            factory = {
                textureView.apply {
                    surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {
                            isTextureAvailable.set(true)
                            post {
                                cameraClient.openCamera(this@apply)
                            }
                        }
                        override fun onSurfaceTextureSizeChanged(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {}
                        override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture): Boolean {
                            isTextureAvailable.set(false)
                            cameraClient.closeCamera()
                            return true
                        }
                        override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) {}
                    }
                }
            }
        )
    }
}
