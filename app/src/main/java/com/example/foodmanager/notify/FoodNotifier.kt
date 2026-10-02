package com.example.foodmanager.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.foodmanager.MainActivity
import com.example.foodmanager.R
import com.example.foodmanager.core.DateUtils
import com.example.foodmanager.data.FoodItem

/**
 * 提醒通知。分两个渠道：即将过期 / 已过期，方便用户单独关闭其中一类。
 */
object FoodNotifier {

    const val CHANNEL_EXPIRING = "food_expiring_soon"
    const val CHANNEL_EXPIRED = "food_expired"

    const val NOTIFICATION_ID_EXPIRING = 1001
    const val NOTIFICATION_ID_EXPIRED = 1002

    private const val GROUP_KEY = "com.example.foodmanager.REMINDERS"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_EXPIRING,
                "临期提醒",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "食物快过期时提前提醒"
                enableVibration(true)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_EXPIRED,
                "过期提醒",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "食物已经过期时提醒"
                enableVibration(true)
            }
        )
    }

    /** 发送提醒。返回实际发出的通知条数，便于 Worker 打印日志。 */
    fun notifyReminders(context: Context, items: List<FoodItem>, warnDays: Int): Int {
        if (items.isEmpty()) { cancelAll(context); return 0 }
        ensureChannels(context)

        val today = DateUtils.today()
        val expired = items.filter { (it.daysToExpiry(today) ?: Long.MAX_VALUE) < 0 }
        val expiring = items.filter {
            val d = it.daysToExpiry(today) ?: return@filter false
            d in 0..warnDays.toLong()
        }

        var sent = 0
        val manager = NotificationManagerCompat.from(context)
        if (expired.isEmpty()) manager.cancel(NOTIFICATION_ID_EXPIRED)
        if (expiring.isEmpty()) manager.cancel(NOTIFICATION_ID_EXPIRING)
        if (expired.isNotEmpty()) {
            sent += post(
                context = context,
                channelId = CHANNEL_EXPIRED,
                notificationId = NOTIFICATION_ID_EXPIRED,
                title = "${expired.size} 件食物已过期",
                text = summarize(expired, today),
                bigText = expired.joinToString("\n") { "· ${it.name}${unitText(it)}（${DateUtils.describeRemaining(it.daysToExpiry(today))}）" },
                colorRes = 0xFFB3261E.toInt(),
            )
        }
        if (expiring.isNotEmpty()) {
            sent += post(
                context = context,
                channelId = CHANNEL_EXPIRING,
                notificationId = NOTIFICATION_ID_EXPIRING,
                title = "${expiring.size} 件食物即将过期",
                text = summarize(expiring, today),
                bigText = expiring.joinToString("\n") { "· ${it.name}${unitText(it)}（${DateUtils.describeRemaining(it.daysToExpiry(today))}）" },
                colorRes = 0xFFE8A33D.toInt(),
            )
        }
        return sent
    }

    fun cancelAll(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        manager.cancel(NOTIFICATION_ID_EXPIRING)
        manager.cancel(NOTIFICATION_ID_EXPIRED)
    }

    private fun unitText(item: FoodItem): String =
        if (item.quantitySpecified) " × ${formatQty(item.quantity)}${item.unit}" else ""

    private fun formatQty(value: Double): String =
        if (value.isFinite()) java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString() else "未填写"

    private fun summarize(items: List<FoodItem>, today: Long): String {
        val head = items.take(3).joinToString("、") { it.name }
        val more = if (items.size > 3) " 等" else ""
        val first = items.first()
        return "$head$more；最早：${DateUtils.describeRemaining(first.daysToExpiry(today))}"
    }

    private fun post(
        context: Context,
        channelId: String,
        notificationId: Int,
        title: String,
        text: String,
        bigText: String,
        colorRes: Int,
    ): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return 0
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_ALERTS, true)
        }
        val pending = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, channelId)
            // 用自己的应用图标（单色小图标），而不是系统通用的感叹号
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setColor(colorRes)
            .setGroup(GROUP_KEY)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            1
        } catch (_: SecurityException) {
            0
        }
    }
}
