package com.example.foodmanager

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.foodmanager.ui.FoodsApp
import com.example.foodmanager.ui.theme.FoodManagerTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    /**
     * 「需要用通知打开提醒清单」的信号。
     * 用 StateFlow 而不是把值传进 setContent 的闭包：onNewIntent 时也能把新信号推给 Compose。
     */
    private val openAlerts = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()


        consumeOpenAlerts(intent)

        setContent {
            val shouldOpenAlerts by openAlerts.collectAsStateWithLifecycle()
            FoodManagerTheme {
                FoodsApp(
                    openAlertsOnStart = shouldOpenAlerts,
                    onOpenAlertsHandled = { openAlerts.value = false },
                )
            }
        }
    }

    /**
     * 通知点击时 Activity 可能已在运行（不会重新走 onCreate），
     * 所以这里也要处理一次，保证"点通知 -> 看到提醒清单"始终成立。
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeOpenAlerts(intent)
    }

    private fun consumeOpenAlerts(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_ALERTS, false) == true) {
            openAlerts.value = true
            intent.removeExtra(EXTRA_OPEN_ALERTS)
        }
    }

    companion object {
        const val EXTRA_OPEN_ALERTS = "open_alerts"
    }
}
