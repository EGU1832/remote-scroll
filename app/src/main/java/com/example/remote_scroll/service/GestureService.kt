// app/src/main/java/com/example/remote_scroll/GestureService.kt
package com.example.remote_scroll.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class GestureService : AccessibilityService() {

    private var gestureReceiver: BroadcastReceiver? = null
    private var screenWidth = 0
    private var screenHeight = 0

    private var lastActionTime = 0L
    private val DEBOUNCE_MS = 200L

    private fun tryScroll(action: () -> Unit) {
        val now = System.currentTimeMillis()
        if (now - lastActionTime > DEBOUNCE_MS) {
            lastActionTime = now
            action()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("GestureService", "Accessibility service connected")

        updateScreenSize()

        val filter = IntentFilter("GESTURE_DETECTED")
        gestureReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val gesture = intent?.getStringExtra("gesture") ?: return
                Log.d("GestureService", "Received gesture: $gesture")

                // gesture 형식 예: "Pointer / Down"
                val parts = gesture.split("/")
                if (parts.size < 2) return

                val motion = parts[1].trim()    // Up / Down / Stop

                when (motion) {
                    "Up" -> tryScroll { performScrollDown() }
                    "Down" -> tryScroll { performScrollUp() }
                }
            }
        }
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(gestureReceiver!!, filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        gestureReceiver?.let {
            LocalBroadcastManager.getInstance(this)
                .unregisterReceiver(it)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 필요시 포커스 변화/앱 변화에 따라 다른 행동 가능
    }

    override fun onInterrupt() {
        // Nothing
    }

    private fun updateScreenSize() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
    }

    private fun performScrollUp() {
        val path = Path().apply {
            moveTo(screenWidth / 2f, screenHeight * 0.80f)
            lineTo(screenWidth / 2f, screenHeight * 0.20f)
        }

        val stroke = GestureDescription.StrokeDescription(path, 0, 700)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    private fun performScrollDown() {
        val path = Path().apply {
            moveTo(screenWidth / 2f, screenHeight * 0.20f)
            lineTo(screenWidth / 2f, screenHeight * 0.80f)
        }

        val stroke = GestureDescription.StrokeDescription(path, 0, 700)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

}
