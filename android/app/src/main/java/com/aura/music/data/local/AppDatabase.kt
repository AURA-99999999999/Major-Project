package com.aura.music.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [RecentlyPlayedEntity::class, DownloadedSong::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recentlyPlayedDao(): RecentlyPlayedDao
    abstract fun downloadedSongDao(): DownloadedSongDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create a temporary table with the new schema
                database.execSQL(
                    """CREATE TABLE recently_played_new (
                    userId TEXT NOT NULL,
                    id TEXT NOT NULL,
                    title TEXT NOT NULL,
                    artist TEXT,
                    artworkUrl TEXT,
                    audioUrl TEXT,
                    playedAt INTEGER NOT NULL,
                    PRIMARY KEY (userId, id)
                    )"""
                )
                // Copy data from old table (default userId to empty string for existing data)
                database.execSQL(
                    """INSERT INTO recently_played_new (userId, id, title, artist, artworkUrl, audioUrl, playedAt)
                    SELECT '', id, title, artist, artworkUrl, audioUrl, playedAt FROM recently_played"""
                )
                // Drop old table
                database.execSQL("DROP TABLE recently_played")
                // Rename new table
                database.execSQL("ALTER TABLE recently_played_new RENAME TO recently_played")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Older builds created downloaded_songs with an incompatible schema.
                // Recreate it to exactly match the DownloadedSong entity expected by Room.
                database.execSQL("DROP TABLE IF EXISTS downloaded_songs")
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS downloaded_songs (
                    videoId TEXT NOT NULL PRIMARY KEY,
                    title TEXT NOT NULL,
                    artist TEXT NOT NULL,
                    album TEXT,
                    thumbnail TEXT,
                    duration TEXT,
                    filePath TEXT NOT NULL,
                    downloadedAt INTEGER NOT NULL,
                    fileSize INTEGER NOT NULL DEFAULT 0
                    )"""
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE downloaded_songs ADD COLUMN fileSize INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "aura_app.db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .fallbackToDestructiveMigrationOnDowngrade()
                .build().also { INSTANCE = it }
            }
        }
    }
}
