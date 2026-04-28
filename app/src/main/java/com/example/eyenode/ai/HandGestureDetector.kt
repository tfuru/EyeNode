package com.example.eyenode.ai

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker

class HandGestureDetector(context: Context) {
    enum class Gesture {
        NONE,
        POINTING,
        PHONE_CALL,
        FIST,
        HAND_PRESENT
    }

    data class FingerPosition(val x: Float, val y: Float)

    private var handLandmarker: HandLandmarker? = null
    private var lastGestureFrameCount = 0
    private var lastGesture: Gesture = Gesture.NONE
    private val TRIGGER_THRESHOLD = 3 

    init {
        val baseOptionsBuilder = BaseOptions.builder()
            .setModelAssetPath("hand_landmarker.task")

        val optionsBuilder = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(baseOptionsBuilder.build())
            .setMinHandDetectionConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setMinHandPresenceConfidence(0.5f)
            .setNumHands(1)
            .setRunningMode(RunningMode.IMAGE)

        handLandmarker = HandLandmarker.createFromOptions(context, optionsBuilder.build())
    }

    fun processFrame(bitmap: Bitmap): Pair<Gesture, FingerPosition?> {
        val mpImage = BitmapImageBuilder(bitmap).build()
        val result = handLandmarker?.detect(mpImage)

        if (result == null || result.landmarks().isEmpty()) {
            lastGestureFrameCount = 0
            lastGesture = Gesture.NONE
            return Gesture.NONE to null
        }

        val landmarks = result.landmarks()[0]
        
        val wrist = landmarks[0]
        val thumbTip = landmarks[4]
        val thumbIp = landmarks[3]
        val indexTip = landmarks[8]
        val indexPip = landmarks[6]
        val middleTip = landmarks[12]
        val middlePip = landmarks[10]
        val ringTip = landmarks[16]
        val ringPip = landmarks[14]
        val pinkyTip = landmarks[20]
        val pinkyPip = landmarks[18]

        // --- 指差し判定 ---
        val isIndexExtended = indexTip.y() < indexPip.y()
        val isIndexMostExtended = indexTip.y() < middleTip.y() && 
                                 indexTip.y() < ringTip.y() && 
                                 indexTip.y() < pinkyTip.y()
        val isPointing = isIndexExtended && isIndexMostExtended

        // --- 電話ジェスチャー判定 ---
        // 親指と小指が立っている
        val isThumbExtended = thumbTip.y() < thumbIp.y()
        val isPinkyExtended = pinkyTip.y() < pinkyPip.y()
        
        // 人差し指、中指、薬指が曲がっている
        val areMiddleFingersFolded = indexTip.y() > indexPip.y() && 
                                    middleTip.y() > middlePip.y() && 
                                    ringTip.y() > ringPip.y()
        
        val isPhoneCall = isThumbExtended && isPinkyExtended && areMiddleFingersFolded
        
        // --- グー判定 ---
        val areAllFingersFolded = areMiddleFingersFolded && (pinkyTip.y() > pinkyPip.y())
        val isFist = areAllFingersFolded && !isThumbExtended

        val currentGesture = when {
            isPhoneCall -> Gesture.PHONE_CALL
            isFist -> Gesture.FIST
            isPointing -> Gesture.POINTING
            else -> Gesture.HAND_PRESENT
        }

        val fingerPos = if (isPointing) FingerPosition(indexTip.x(), indexTip.y()) 
                        else FingerPosition(thumbTip.x(), thumbTip.y())

        if (currentGesture == lastGesture && currentGesture != Gesture.HAND_PRESENT) {
            lastGestureFrameCount++
        } else {
            lastGesture = currentGesture
            lastGestureFrameCount = 0
        }

        return if (lastGestureFrameCount >= TRIGGER_THRESHOLD) {
            lastGesture to fingerPos
        } else {
            Gesture.HAND_PRESENT to fingerPos
        }
    }

    fun close() {
        handLandmarker?.close()
    }
}
