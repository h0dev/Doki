package org.dokiteam.doki.core.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration27To28 : Migration(27, 28) {

	override fun migrate(db: SupportSQLiteDatabase) {
		db.execSQL("ALTER TABLE favourites ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
		db.execSQL("ALTER TABLE favourite_categories ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
		db.execSQL("ALTER TABLE bookmarks ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
	}
}
