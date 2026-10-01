package com.example.foodmanager.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 数据库正式迁移（**替代破坏式迁移，保证升级不丢用户数据**）。
 *
 * 历史版本差异：
 * - v1 → v2：`app_settings` 增加 `customLocations`（常用存放位置）
 * - v2 → v3：`app_settings` 增加 `customFoods`（常用食品）、`lastEntryMode`（上次录入方式）
 * - `food_items` 表在三版之间**没有任何变化**
 *
 * 新增列都带 DEFAULT，因此老数据可直接保留，无需重建表。
 */
object DatabaseMigrations {

    /** v1 → v2：新增自定义存放位置 */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `app_settings` ADD COLUMN `customLocations` TEXT NOT NULL DEFAULT ''")
        }
    }

    /** v2 → v3：新增常用食品与上次录入方式 */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `app_settings` ADD COLUMN `customFoods` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `app_settings` ADD COLUMN `lastEntryMode` TEXT NOT NULL DEFAULT 'manual'")
        }
    }

    val ALL = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
