package com.example.remote_scroll

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.remote_scroll.service.CameraForegroundService
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val cameraGranted = result[Manifest.permission.CAMERA] == true

        val notiGranted = if (Build.VERSION.SDK_INT >= 33) {
            result[Manifest.permission.POST_NOTIFICATIONS] == true
        } else true

        if (cameraGranted && notiGranted) {
            startCameraService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // PowerToys 스타일 토글 스위치
        val switchGesture = findViewById<SwitchMaterial>(R.id.switch_gesture)

        switchGesture.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // 서비스 시작
                ensurePermissionsAndStart()
            } else {
                // 서비스 종료
                CameraForegroundService.stop(this)
            }
        }

        // 접근성 설정 카드 클릭
        val cardAccessibility = findViewById<MaterialCardView>(R.id.card_accessibility)
        cardAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        }
    }

    private fun ensurePermissionsAndStart() {
        val needed = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            needed += Manifest.permission.CAMERA
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }

        if (needed.isEmpty()) {
            startCameraService()
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun startCameraService() {
        CameraForegroundService.start(this)
    }
}
