package com.example.foodmanager.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 历史版本直接升级到 v8，不依赖设备支持 ALTER TABLE DROP COLUMN。
 * 早期安装包的设置列可能早于 schema 版本出现，因此以实际列为准复制。
 * Room 在事务内执行迁移；任何失败向上传递并回滚，不能把失败当作成功。
 */
object DatabaseMigrations {
    private val settingsColumns = linkedMapOf(
        "id" to "INTEGER NOT NULL PRIMARY KEY",
        "warnDays" to "INTEGER NOT NULL DEFAULT 3",
        "notifyEnabled" to "INTEGER NOT NULL DEFAULT 1",
        "homeBannerEnabled" to "INTEGER NOT NULL DEFAULT 1",
        "notifyHour" to "INTEGER NOT NULL DEFAULT 9",
        "ocrSingleItem" to "INTEGER NOT NULL DEFAULT 1",
        "lastLocation" to "TEXT NOT NULL DEFAULT ''",
        "lastUnit" to "TEXT NOT NULL DEFAULT '个'",
        "lastEntryMode" to "TEXT NOT NULL DEFAULT 'manual'",
        "locationsInitialized" to "INTEGER NOT NULL DEFAULT 0",
    )

    val ALL: Array<Migration> = (1..7).map { version ->
        object : Migration(version, 8) {
            override fun migrate(db: SupportSQLiteDatabase) = upgradeTo8(db)
        }
    }.plus(object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE food_items ADD COLUMN quantitySpecified INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE food_items SET quantitySpecified = CASE WHEN quantity > 0 THEN 1 ELSE 0 END")
        }
    }).toTypedArray()

    private fun upgradeTo8(db: SupportSQLiteDatabase) {
        val existing = mutableSetOf<String>()
        db.query("PRAGMA table_info(`app_settings`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) existing += cursor.getString(nameIndex)
        }
        check("id" in existing) { "app_settings 表结构不完整，无法安全迁移" }

        for (table in listOf("storage_locations", "common_foods")) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `$table` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `alias` TEXT NOT NULL DEFAULT '',
                    `sortOrder` INTEGER NOT NULL DEFAULT 0,
                    `createdAt` INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_${table}_name` ON `$table` (`name`)")
        }

        // 旧列存在但为空表示用户删空，不能重新补种默认值。
        migrateNames(db, existing, "customLocations", "storage_locations", Locations.DEFAULTS)
        migrateNames(db, existing, "customFoods", "common_foods", CommonFoods.DEFAULTS)

        val definition = settingsColumns.entries.joinToString { "`${it.key}` ${it.value}" }
        db.execSQL("CREATE TABLE `app_settings_v8` ($definition)")
        val copied = settingsColumns.keys.filter { it in existing && it != "locationsInitialized" }
        val names = copied.joinToString { "`$it`" }
        db.execSQL("INSERT INTO `app_settings_v8` ($names, `locationsInitialized`) SELECT $names, 1 FROM `app_settings`")
        db.execSQL("DROP TABLE `app_settings`")
        db.execSQL("ALTER TABLE `app_settings_v8` RENAME TO `app_settings`")
    }

    private fun migrateNames(
        db: SupportSQLiteDatabase,
        existing: Set<String>,
        column: String,
        table: String,
        defaults: List<String>,
    ) {
        val names = if (column in existing) {
            db.query("SELECT `$column` FROM `app_settings` WHERE `id` = 1").use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) {
                    cursor.getString(0).split('|').map(String::trim).filter(String::isNotBlank).distinct()
                } else emptyList()
            }
        } else defaults
        names.forEachIndexed { index, name ->
            db.execSQL(
                "INSERT OR IGNORE INTO `$table` (`name`, `alias`, `sortOrder`, `createdAt`) VALUES (?, '', ?, ?)",
                arrayOf<Any>(name, index, System.currentTimeMillis()),
            )
        }
    }
}
