package app.auralis.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE books ADD COLUMN progressResetAt INTEGER NOT NULL DEFAULT 0")
        }
    }

val MIGRATION_2_3 =
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS challenges (id TEXT NOT NULL, title TEXT NOT NULL, metric TEXT NOT NULL, target INTEGER NOT NULL, startsOn TEXT NOT NULL, endsOn TEXT NOT NULL, PRIMARY KEY(id))"
            )
        }
    }

val MIGRATION_3_4 =
    object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE books ADD COLUMN seriesName TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE books ADD COLUMN seriesNumber REAL")
            db.execSQL("ALTER TABLE books ADD COLUMN edition TEXT NOT NULL DEFAULT ''")
            db.execSQL(
                "UPDATE books SET edition = 'Full-cast' WHERE LOWER(title) LIKE '%full-cast%'"
            )
        }
    }

val MIGRATION_4_5 =
    object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE books ADD COLUMN seriesInferenceDisabled INTEGER NOT NULL DEFAULT 0")
        }
    }

val MIGRATION_5_6 =
    object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE books ADD COLUMN companionPdfUri TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE books ADD COLUMN companionPdfPage INTEGER NOT NULL DEFAULT 0")
        }
    }
