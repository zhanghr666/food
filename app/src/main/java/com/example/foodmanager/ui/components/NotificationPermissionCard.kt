package com.example.foodmanager.ui.components

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.foodmanager.notify.FoodNotifier

@Composable
fun NotificationPermissionCard() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    fun status(): String {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return "系统通知未授权或已关闭，无法在通知栏提醒"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val blocked = listOf(FoodNotifier.CHANNEL_EXPIRING, FoodNotifier.CHANNEL_EXPIRED).filter {
                manager?.getNotificationChannel(it)?.importance == NotificationManager.IMPORTANCE_NONE
            }
            if (blocked.isNotEmpty()) return "部分提醒渠道已关闭，请到系统通知设置中开启"
        }
        return "系统允许通知；实际提醒时间可能受省电策略影响"
    }
    var permissionStatus by remember { mutableStateOf(status()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionStatus = status()
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionStatus = status()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    Text(permissionStatus)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        !NotificationManagerCompat.from(context).areNotificationsEnabled()) {
        TextButton(onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
            Text("允许通知提醒")
        }
    }
    TextButton(onClick = {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))
        }
        context.startActivity(intent)
    }) { Text("打开系统通知设置") }
}
