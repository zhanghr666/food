package com.example.foodmanager

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.foodmanager.ui.FoodsApp
import com.example.foodmanager.ui.theme.FoodManagerTheme

class MainActivity : ComponentActivity() {

    /** 支持从通知点击后直接跳到 App（首页会显示提醒清单） */
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestStartupPermissions()

        setContent {
            FoodManagerTheme {
                FoodsApp()
            }
        }
    }

    /** App 启动时申请必要权限：麦克风（语音录入）与通知（过期提醒） */
    private fun requestStartupPermissions() {
        val wanted = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            wanted += Manifest.permission.RECORD_AUDIO
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            wanted += Manifest.permission.POST_NOTIFICATIONS
        }
        if (wanted.isNotEmpty()) {
            runCatching { permissionLauncher.launch(wanted.toTypedArray()) }
        }
    }

    companion object {
        const val EXTRA_OPEN_ALERTS = "open_alerts"
    }
}
