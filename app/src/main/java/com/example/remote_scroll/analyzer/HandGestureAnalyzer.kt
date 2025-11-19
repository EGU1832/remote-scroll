package com.example.remote_scroll.analyzer

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.remote_scroll.ml.KeypointClassifier
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.util.ArrayDeque
import kotlin.math.abs

class HandGestureAnalyzer(context: Context) {

    private lateinit var handLandmarker: HandLandmarker
    private val keypointClassifier = KeypointClassifier(context)
    private val appContext = context.applicationContext

    // ★ 5프레임 smoothing
    private val tipHistory: ArrayDeque<Float> = ArrayDeque()
    private val SMOOTHING_WINDOW = 5

    // ★ 3프레임 motion consistency
    private val motionHistory: ArrayDeque<String> = ArrayDeque()
    private val MOTION_CONFIRM_WINDOW = 3

    private var lastSmoothedTipY: Float? = null

    // ★ Pointer 모드 안정화 & 방향 락 관련
    private var pointerStableStart: Long = 0L
    private val POINTER_STABLE_DURATION = 300L // ms

    private var directionLock: String? = null   // "Up" / "Down" / null
    private var stopCount: Int = 0
    private val STOP_RESET_COUNT = 5

    var lastLandmarks: List<NormalizedLandmark>? = null
        private set

    fun initialize() {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("hand_landmarker.task")
            .build()

        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.IMAGE)
            .setNumHands(1)
            .build()

        handLandmarker = HandLandmarker.createFromOptions(appContext, options)
    }

    fun analyzeFrame(bitmap: Bitmap): String? {
        val mpImage = BitmapImageBuilder(bitmap).build()
        val result: HandLandmarkerResult = handLandmarker.detect(mpImage)

        if (result.landmarks().isEmpty()) {
            // 손이 사라지면 상태 리셋
            resetState()
            return null
        }

        val landmarks = result.landmarks()[0]
        lastLandmarks = landmarks

        // 1. Keypoint classification
        val processed = preProcessLandmarks(landmarks)
        val keyLabel = keypointClassifier.classify(processed)

        val tipY = landmarks[8].y()

        // 2. Up/Down smoothing & consistency & stabilization & direction lock
        val finalMotion = detectMotion(keyLabel, tipY)

        val finalLabel = "$keyLabel / $finalMotion"

        // 3. Broadcast
        val intent = Intent("GESTURE_DETECTED")
        intent.putExtra("gesture", finalLabel)
        LocalBroadcastManager.getInstance(appContext).sendBroadcast(intent)

        Log.d("HandGestureAnalyzer", "Detected Gesture: $finalLabel")
        return finalLabel
    }

    // -------------------------------------------
    // ★ UP/DOWN 인식 부드럽고 안정적인 알고리즘
    // -------------------------------------------

    private fun detectMotion(keyLabel: String?, tipY: Float): String {
        // Pointer가 아닌 상태에서는 모두 리셋 + Stop
        if (keyLabel != "Pointer") {
            resetState()
            return "Stop"
        }

        val now = System.currentTimeMillis()

        // Pointer 모드로 처음 진입했을 때
        if (pointerStableStart == 0L) {
            pointerStableStart = now
            // 안정화 시작 시점에서는 히스토리 초기화
            tipHistory.clear()
            motionHistory.clear()
            lastSmoothedTipY = null
            directionLock = null
            stopCount = 0
        }

        // (1) smoothing
        tipHistory.addLast(tipY)
        if (tipHistory.size > SMOOTHING_WINDOW) tipHistory.removeFirst()
        val smoothedTipY = tipHistory.average().toFloat()

        val prev = lastSmoothedTipY
        lastSmoothedTipY = smoothedTipY

        // Pointer 안정화 구간: 처음 300ms + prev가 없는 초기 프레임은 무조건 Stop
        if (now - pointerStableStart < POINTER_STABLE_DURATION || prev == null) {
            Log.d("HandGestureAnalyzer", "Stabilizing Pointer... no motion yet")
            return "Stop"
        }

        // (2) delta Y 계산
        val dy = smoothedTipY - prev
        Log.d(
            "HandGestureAnalyzer",
            "ΔY_smooth = $dy (curSmooth=$smoothedTipY, prevSmooth=$prev)"
        )

        // (3) threshold 기반 raw motion 판단
        val downThreshold = 0.02f
        val upThreshold = 0.012f
        val rawMotion = when {
            dy > downThreshold -> "Down"
            dy < -upThreshold -> "Up"
            else -> "Stop"
        }

        // (4) 일관성 체크 (3프레임 중 2개 이상 동일할 때만 인정)
        motionHistory.addLast(rawMotion)
        if (motionHistory.size > MOTION_CONFIRM_WINDOW) motionHistory.removeFirst()

        val confirmedMotion = when {
            motionHistory.count { it == "Up" } >= 2 -> "Up"
            motionHistory.count { it == "Down" } >= 2 -> "Down"
            else -> "Stop"
        }

        // (5) 방향 락 & Stop 기반 리셋 로직
        // Stop이 연속으로 나오면 일정 횟수 이후 방향 락 해제
        if (confirmedMotion == "Stop") {
            stopCount++
            if (stopCount >= STOP_RESET_COUNT) {
                directionLock = null
            }
            return "Stop"
        }

        // 여기까지 왔다는 것은 Up or Down
        stopCount = 0

        // 아직 방향 락이 없으면 현재 방향으로 락을 건다 (한 번만 방출)
        if (directionLock == null) {
            directionLock = confirmedMotion
            return confirmedMotion
        }

        // 이미 이 방향으로 락이 걸려 있으면 더 이상 같은 방향은 내보내지 않는다 → Stop 처리
        if (directionLock == confirmedMotion) {
            return "Stop"
        }

        // 다른 방향이 들어왔고, 이전 Stop 누적으로 락이 풀렸다면 방향 전환
        directionLock = confirmedMotion
        return confirmedMotion
    }

    // -------------------------------------------
    // 상태 리셋 (손이 사라지거나 Pointer에서 벗어날 때)
    // -------------------------------------------
    private fun resetState() {
        pointerStableStart = 0L
        tipHistory.clear()
        motionHistory.clear()
        lastSmoothedTipY = null
        directionLock = null
        stopCount = 0
    }

    // -------------------------------------------
    // Preprocessing
    // -------------------------------------------

    private fun preProcessLandmarks(landmarks: List<NormalizedLandmark>): FloatArray {
        val baseX = landmarks[0].x()
        val baseY = landmarks[0].y()

        val relative = landmarks.map {
            listOf(it.x() - baseX, it.y() - baseY)
        }.flatten()

        val maxVal = relative.maxOf { abs(it) }.takeIf { it > 0 } ?: 1f
        return relative.map { it / maxVal }.toFloatArray()
    }
}
