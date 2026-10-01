package com.example.foodmanager.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 数据库正式迁移（**替代破坏式迁移，保证升级不丢用户数据**）。
 *
 * 版本历史：
 * - v1 → v2：`app_settings` 增加 `customLocations`（常用存放位置）
 * - v2 → v3：`app_settings` 增加 `customFoods`（常用食品）、`lastEntryMode`（上次录入方式）
 * - v3 → v4：`app_settings` 增加 `ocrSingleItem`（拍照识别是否一张照片只出一条）
 * - v4 → v5：`app_settings` 增加 `behaviorNormalized`（一次性行为归一化标记）
 * - v5 → v6：**删除** `behaviorNormalized`
 * - v6 → v7：**删除** `reuseLastLocation`
 * - v7 → v8：**拆表** —— `app_settings.customLocations` / `customFoods`
 *   搬进独立的 `storage_locations` / `common_foods` 两张表，并删除这两个旧列
 * - `food_items` 表在所有版本之间**没有任何变化**
 *
 * v6 与 v7 都是在"抹平历史"：
 * `behaviorNormalized` 是一次性纠正用的临时列，纠正逻辑已随数据统一而移除；
 * `reuseLastLocation` 这个开关已按需求取消（位置永远不自动套用）。
 * 两列都只是**多余列**，删除它们不影响任何业务数据。
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

    /**
     * v3 → v4：新增「一张照片只识别一件商品」开关。
     *
     * 默认 **1（开启）**：单件商品的包装文案按行拆分会被切碎成多条，
     * 这是明确的错误行为，所以**升级用户也直接采用新行为**，不需要手动去设置里打开。
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `app_settings` ADD COLUMN `ocrSingleItem` INTEGER NOT NULL DEFAULT 1")
        }
    }

    /**
     * v4 → v5：曾经新增过「行为归一化」标记列。
     *
     * 保留这一步是为了让"已经升到 v5 的库"能继续往下走；
     * 该列会在 v5 → v6 里被删掉（纠正逻辑已移除）。
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `app_settings` ADD COLUMN `behaviorNormalized` INTEGER NOT NULL DEFAULT 0")
        }
    }

    /** v5 → v6：删除已无用途的 `behaviorNormalized` */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.dropColumnIfSupported("behaviorNormalized")
        }
    }

    /** v6 → v7：删除已取消的 `reuseLastLocation` 开关 */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.dropColumnIfSupported("reuseLastLocation")
        }
    }

    /**
     * v7 → v8：把「竖线分隔字符串」拆成**独立表**。
     *
     * - 新建 `storage_locations`、`common_foods` 两张表
     * - 把 `app_settings.customLocations` / `customFoods` 的内容逐条搬进去
     *   （`sortOrder` 按原字符串里的先后顺序赋值，保持用户看到的顺序不变）
     * - 再删掉这两个旧列
     *
     * 重复的名字用 `INSERT OR IGNORE` 跳过（表上有 name 唯一索引）。
     * **不会**把 `food_items` 里"用过但不在常用列表"的位置补进来 —— 按需求如此。
     */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `storage_locations` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `alias` TEXT NOT NULL DEFAULT '',
                    `sortOrder` INTEGER NOT NULL DEFAULT 0,
                    `createdAt` INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_storage_locations_name` " +
                    "ON `storage_locations` (`name`)",
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `common_foods` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `alias` TEXT NOT NULL DEFAULT '',
                    `sortOrder` INTEGER NOT NULL DEFAULT 0,
                    `createdAt` INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_common_foods_name` ON `common_foods` (`name`)",
            )

            // 搬迁旧数据：读两列 → 拆 `|` → 按顺序插入
            migratePipedColumn(db, column = "customLocations", table = "storage_locations")
            migratePipedColumn(db, column = "customFoods", table = "common_foods")

            // 标记"位置表已经初始化过"：此后表空 = 用户主动删光，不再补种默认值
            runCatching {
                db.execSQL(
                    "ALTER TABLE `app_settings` ADD COLUMN `locationsInitialized` " +
                        "INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL("UPDATE `app_settings` SET `locationsInitialized` = 1")
            }

            // 旧列已完成使命
            db.dropColumnIfSupported("customLocations")
            db.dropColumnIfSupported("customFoods")
        }
    }

    /**
     * 把 `app_settings` 里某个"竖线分隔"的列搬迁到独立表。
     *
     * 只搬该列自己的数据，不碰其它表；`sortOrder` 用原来的下标，
     * 这样用户看到的顺序与升级前完全一致。
     */
    private fun migratePipedColumn(db: SupportSQLiteDatabase, column: String, table: String) {
        val names = mutableListOf<String>()
        runCatching {
            db.query("SELECT `$column` FROM `app_settings` WHERE `id` = 1").use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) {
                    cursor.getString(0)
                        .split('|')
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .forEach { names += it }
                }
            }
        }

        names.forEachIndexed { index, name ->
            db.execSQL(
                "INSERT OR IGNORE INTO `$table` (`name`, `alias`, `sortOrder`, `createdAt`) " +
                    "VALUES (?, '', ?, ?)",
                arrayOf<Any>(name, index, System.currentTimeMillis()),
            )
        }
    }

    /**
     * 尽力删除一列。
     *
     * `ALTER TABLE ... DROP COLUMN` 需要 SQLite ≥ 3.35（Android 14+ 才自带）。
     * 在不支持的老系统上会抛异常 —— 那种情况下**跳过删除**：
     * 多出来的列 Room 会忽略（实体里已无该字段），
     * 比让整个迁移失败、应用起不来要好得多。
     */
    private fun SupportSQLiteDatabase.dropColumnIfSupported(column: String) {
        runCatching {
            execSQL("ALTER TABLE `app_settings` DROP COLUMN `$column`")
        }
    }

    val ALL = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
    )
}
