package com.example.foodmanager

import android.app.Application
import android.content.Context
import com.example.foodmanager.data.AppDatabase
import com.example.foodmanager.data.FoodRepository
import com.example.foodmanager.notify.ExpiryReminderWorker
import com.example.foodmanager.notify.FoodNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 应用入口：初始化本地数据库、仓库，并注册每日过期提醒任务。
 * 所有数据只保存在本机 SQLite，不联网、不上传。
 */
class FoodApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.get(this) }

    val repository: FoodRepository by lazy {
        FoodRepository(
            foodDao = database.foodDao(),
            settingsDao = database.settingsDao(),
            storageLocationDao = database.storageLocationDao(),
            commonFoodDao = database.commonFoodDao(),
            appContext = applicationContext,
        )
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        FoodNotifier.ensureChannels(this)

        appScope.launch {
            val settings = repository.loadSettings()
            repository.saveSettings(settings)
            if (settings.notifyEnabled) {
                ExpiryReminderWorker.schedule(this@FoodApp, settings.notifyHour)
                ExpiryReminderWorker.runNow(this@FoodApp)
            }
        }
    }

    companion object {
        fun from(context: Context): FoodApp = context.applicationContext as FoodApp
    }
}
