package com.example.foodmanager.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** 保留旧食品的主键、草稿幂等令牌、日期、设置和常用项，不清库。 */
object HouseholdMigration : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS managed_items (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, entryToken TEXT, kind TEXT NOT NULL, name TEXT NOT NULL, category TEXT NOT NULL, quantity REAL NOT NULL, quantitySpecified INTEGER NOT NULL DEFAULT 0, unit TEXT NOT NULL, purchaseDate INTEGER NOT NULL, location TEXT NOT NULL, note TEXT NOT NULL, brand TEXT NOT NULL, specification TEXT NOT NULL, price REAL, source TEXT NOT NULL, archived INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_managed_items_entryToken ON managed_items (entryToken)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_managed_items_kind_archived ON managed_items (kind, archived)")
        db.execSQL("CREATE TABLE IF NOT EXISTS food_details (itemId INTEGER NOT NULL PRIMARY KEY, productionDate INTEGER, expiryDate INTEGER, dateKind TEXT NOT NULL, shelfLifeAmount INTEGER, shelfLifeUnit TEXT NOT NULL, FOREIGN KEY(itemId) REFERENCES managed_items(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE TABLE IF NOT EXISTS photo_attachments (`key` TEXT NOT NULL PRIMARY KEY, itemId INTEGER NOT NULL, position INTEGER NOT NULL, FOREIGN KEY(itemId) REFERENCES managed_items(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_photo_attachments_itemId ON photo_attachments (itemId)")
        val common = "id, entryToken, name, category, quantity, quantitySpecified, unit, purchaseDate, location, note, brand, specification, price, source, archived, createdAt, updatedAt"
        db.execSQL("INSERT INTO managed_items ($common, kind) SELECT $common, 'food' FROM food_items")
        db.execSQL("INSERT INTO food_details SELECT id, productionDate, expiryDate, dateKind, shelfLifeAmount, shelfLifeUnit FROM food_items")
        db.execSQL("DROP TABLE food_items")
        db.execSQL("CREATE VIEW `food_items` AS $FOOD_VIEW")
    }
}
