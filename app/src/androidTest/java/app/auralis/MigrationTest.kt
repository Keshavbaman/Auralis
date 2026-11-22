package app.auralis

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import app.auralis.data.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AuralisDatabase::class.java,
        )

    @Test
    fun migrationPreservesExistingBooksAndDefaultsResetTime() {
        helper.createDatabase("migration-test", 1).use { db ->
            db.execSQL(
                "INSERT INTO books (id,title,author,genre,narrator,artwork,addedAt,favourite,manuallyCompleted) VALUES ('test','A story','Author','','',NULL,1,0,0)"
            )
        }
        helper
            .runMigrationsAndValidate(
                "migration-test",
                5,
                true,
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
            )
            .use { db ->
                db.query(
                        "SELECT title,progressResetAt,seriesName,seriesNumber,edition,seriesInferenceDisabled FROM books WHERE id='test'"
                    )
                    .use { c ->
                        assertTrue(c.moveToFirst())
                        assertEquals("A story", c.getString(0))
                        assertEquals(0, c.getLong(1))
                        assertEquals("", c.getString(2))
                        assertTrue(c.isNull(3))
                        assertEquals("", c.getString(4))
                        assertEquals(0, c.getInt(5))
                    }
            }
    }

    @Test
    fun versionThreeUpgradePreservesPlaybackHistoryAndRelationships() {
        helper.createDatabase("migration-3-4", 3).use { db ->
            db.execSQL(
                "INSERT INTO books VALUES ('b','Book (Full-Cast Edition)','Author','Fantasy','Narrator',NULL,123,1,0,0)"
            )
            db.execSQL("INSERT INTO files VALUES ('f','b','content://original','book.m4b',90000)")
            db.execSQL("INSERT INTO chapters VALUES ('c','b','f','Chapter V',0,0,90000)")
            db.execSQL("INSERT INTO progress VALUES ('b',0,65000,123456,1.5)")
            db.execSQL("INSERT INTO sessions VALUES ('s','b',1000,2000)")
            db.execSQL(
                "INSERT INTO segments VALUES ('r','s','b','c',1000,2000,'1970-01-01','UTC',1000,1500,0,1500,1.5)"
            )
            db.execSQL("INSERT INTO bookmarks VALUES ('m','b',0,64000,123,'Moment','My note')")
            db.execSQL("INSERT INTO achievements VALUES ('early',456)")
            db.execSQL("INSERT INTO collections VALUES ('shelf','Shelf')")
            db.execSQL("INSERT INTO collection_books VALUES ('shelf','b')")
        }
        helper.runMigrationsAndValidate("migration-3-4", 4, true, MIGRATION_3_4).use { db ->
            db.query("SELECT positionMs,speed FROM progress WHERE bookId='b'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(65000L, c.getLong(0))
                assertEquals(1.5, c.getDouble(1), .001)
            }
            db.query("SELECT note FROM bookmarks WHERE id='m'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("My note", c.getString(0))
            }
            db.query("SELECT edition FROM books WHERE id='b'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Full-cast", c.getString(0))
            }
            db.query("SELECT elapsedMs,contentMs FROM segments WHERE id='r'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1000L, c.getLong(0))
                assertEquals(1500L, c.getLong(1))
            }
            db.query("SELECT unlockedAt FROM achievements WHERE `key`='early'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(456L, c.getLong(0))
            }
            db.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        }
    }
}
