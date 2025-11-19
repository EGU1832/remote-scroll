// app/src/main/java/com/example/remote_scroll/service/CameraForegroundService.kt
package com.example.remote_scroll.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.remote_scroll.R
import com.example.remote_scroll.analyzer.HandGestureAnalyzer
import java.util.concurrent.Executors

class CameraForegroundService : LifecycleService() {

    companion object {
        private const val CHANNEL_ID = "camera_gesture_service"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, CameraForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, CameraForegroundService::class.java)
            context.stopService(intent)
        }
    }

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private lateinit var handGestureAnalyzer: HandGestureAnalyzer

    override fun onCreate() {
        super.onCreate()

        handGestureAnalyzer = HandGestureAnalyzer(this)
        handGestureAnalyzer.initialize()

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        startCamera()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun onBind(intent: Intent): IBinder? {
        // 포그라운드 서비스로만 사용할 거면 super 호출만 하면 됨
        return super.onBind(intent)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build()

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                processImageProxy(imageProxy)
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, // LifecycleOwner (LifecycleService)
                    cameraSelector,
                    imageAnalysis
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImageProxy(imageProxy: ImageProxy) {
        val bitmap = imageProxy.toBitmap()
        if (bitmap != null) {
            handGestureAnalyzer.analyzeFrame(bitmap)
        }
        imageProxy.close()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Hand Gesture Camera Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Hand gesture detection")
            .setContentText("Camera is running to detect hand gestures.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
    }
}
