package com.example.foodmanager.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        FoodItem::class,
        AppSettings::class,
        StorageLocation::class,
        CommonFood::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun foodDao(): FoodDao
    abstract fun settingsDao(): SettingsDao
    abstract fun storageLocationDao(): StorageLocationDao
    abstract fun commonFoodDao(): CommonFoodDao

    companion object {
        private const val DB_NAME = "food_manager_next.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME,
                )
                    .build()
                    .also { instance = it }
            }
    }
}
