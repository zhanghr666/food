package com.example.foodmanager.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.foodmanager.FoodApp
import com.example.foodmanager.core.DateUtils
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * 后台定时检查临期 / 过期食物并发出通知。
 * 使用 WorkManager 定期检查；系统省电策略可能推迟执行，不保证整点送达。
 */
class ExpiryReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? FoodApp ?: return Result.success()
        val repository = app.repository
        FoodNotifier.ensureChannels(applicationContext)

        val settings = repository.loadSettings()
        if (!settings.notifyEnabled) {
            FoodNotifier.cancelAll(applicationContext)
            return Result.success()
        }

        val alerts = repository.loadAlertItems(settings.warnDays)
        if (alerts.isEmpty()) {
            FoodNotifier.cancelAll(applicationContext)
            return Result.success()
        }

        val sent = FoodNotifier.notifyReminders(applicationContext, alerts, settings.warnDays)
        return Result.success(androidx.work.workDataOf("sent" to sent, "checked" to alerts.size))
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "food_expiry_reminder_periodic"
        private const val IMMEDIATE_WORK_NAME = "food_expiry_reminder_now"

        /** 注册每日周期检查（按设置里的提醒时间）。 */
        fun schedule(context: Context, hour: Int = 9) {
            val request = PeriodicWorkRequestBuilder<ExpiryReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delayUntilHour(hour), TimeUnit.MILLISECONDS)
                .addTag(PERIODIC_WORK_NAME)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
        }

        /** 立即检查一次（例如刚新增食物或刚打开 App 时）。 */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<ExpiryReminderWorker>()
                .addTag(IMMEDIATE_WORK_NAME)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        /** 距离下一个 hour 点还有多少毫秒 */
        private fun delayUntilHour(hour: Int): Long {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (target.timeInMillis <= now.timeInMillis) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }
            return target.timeInMillis - now.timeInMillis
        }

        /** 供调试：检查一次并同步返回结果说明 */
        suspend fun checkNow(context: Context): String {
            val repo = FoodApp.from(context).repository
            repo.refreshToday()
            val settings = repo.loadSettings()
            val alerts = repo.loadAlertItems(settings.warnDays)
            val today = DateUtils.today()
            val expired = alerts.count { it.dateKind == "expiry" && (it.daysToExpiry(today) ?: Long.MAX_VALUE) < 0 }
            val soon = alerts.count { it.dateKind == "expiry" && (it.daysToExpiry(today) ?: Long.MAX_VALUE) in 0..settings.warnDays.toLong() }
            val plans = alerts.count { it.dateKind == "reminder" && (it.daysToExpiry(today) ?: Long.MAX_VALUE) <= 0 }
            return "已过期 $expired 件，即将过期 $soon 件，食用计划 $plans 件"
        }
    }
}
