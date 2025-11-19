package com.example.remote_scroll.util

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log

object ScrollController {

    fun scrollDown(service: AccessibilityService) {
        // 아래로 스크롤
        val success = service.rootInActiveWindow?.performAction(
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        )
        Log.d("ScrollController", "Scroll Down: $success")
    }

    fun scrollUp(service: AccessibilityService) {
        // 위로 스크롤
        val success = service.rootInActiveWindow?.performAction(
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        )
        Log.d("ScrollController", "Scroll Up: $success")
    }
}