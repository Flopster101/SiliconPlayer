package com.flopster101.siliconplayer

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Database for storing per-song audio overrides.
 * Each song is identified by its file path and can store:
 * - Song volume in dB
 * - Whether core volume should be ignored for this song
 */
class VolumeDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_SONG_VOLUMES (
                $COLUMN_FILE_PATH TEXT PRIMARY KEY,
                $COLUMN_VOLUME_DB REAL NOT NULL,
                $COLUMN_IGNORE_CORE_VOLUME INTEGER NOT NULL DEFAULT 0,
                $COLUMN_LAST_MODIFIED INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX idx_song_volumes_path ON $TABLE_SONG_VOLUMES($COLUMN_FILE_PATH)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL(
                "ALTER TABLE $TABLE_SONG_VOLUMES ADD COLUMN $COLUMN_IGNORE_CORE_VOLUME INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    /**
     * Get the volume adjustment for a specific song.
     * @param filePath The absolute file path of the song
     * @return The volume in dB, or null if no adjustment is stored
     */
    fun getSongVolume(filePath: String): Float? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_SONG_VOLUMES,
            arrayOf(COLUMN_VOLUME_DB),
            "$COLUMN_FILE_PATH = ?",
            arrayOf(filePath),
            null,
            null,
            null
        )

        return cursor.use {
            if (it.moveToFirst()) {
                it.getFloat(it.getColumnIndexOrThrow(COLUMN_VOLUME_DB))
            } else {
                null
            }
        }
    }

    /**
     * Set the volume adjustment for a specific song.
     * @param filePath The absolute file path of the song
     * @param volumeDb The volume adjustment in dB
     */
    fun setSongVolume(filePath: String, volumeDb: Float) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_VOLUME_DB, volumeDb)
            put(COLUMN_LAST_MODIFIED, System.currentTimeMillis())
        }
        val updatedRows = db.update(
            TABLE_SONG_VOLUMES,
            values,
            "$COLUMN_FILE_PATH = ?",
            arrayOf(filePath)
        )
        if (updatedRows == 0) {
            values.put(COLUMN_FILE_PATH, filePath)
            values.put(COLUMN_IGNORE_CORE_VOLUME, 0)
            db.insert(TABLE_SONG_VOLUMES, null, values)
        }
    }

    /**
     * Get whether core volume should be ignored for a specific song.
     * @param filePath The absolute file path of the song
     * @return true if core volume should be ignored for this song
     */
    fun getSongIgnoreCoreVolume(filePath: String): Boolean {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_SONG_VOLUMES,
            arrayOf(COLUMN_IGNORE_CORE_VOLUME),
            "$COLUMN_FILE_PATH = ?",
            arrayOf(filePath),
            null,
            null,
            null
        )

        return cursor.use {
            if (it.moveToFirst()) {
                it.getInt(it.getColumnIndexOrThrow(COLUMN_IGNORE_CORE_VOLUME)) != 0
            } else {
                false
            }
        }
    }

    /**
     * Set whether core volume should be ignored for a specific song.
     * @param filePath The absolute file path of the song
     * @param ignoreCoreVolume true to ignore core volume for this song
     */
    fun setSongIgnoreCoreVolume(filePath: String, ignoreCoreVolume: Boolean) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_IGNORE_CORE_VOLUME, if (ignoreCoreVolume) 1 else 0)
            put(COLUMN_LAST_MODIFIED, System.currentTimeMillis())
        }
        val updatedRows = db.update(
            TABLE_SONG_VOLUMES,
            values,
            "$COLUMN_FILE_PATH = ?",
            arrayOf(filePath)
        )
        if (updatedRows == 0) {
            values.put(COLUMN_FILE_PATH, filePath)
            values.put(COLUMN_VOLUME_DB, 0f)
            db.insert(TABLE_SONG_VOLUMES, null, values)
        }
    }

    /**
     * Reset the volume adjustment for a specific song (remove from database).
     * @param filePath The absolute file path of the song
     */
    fun resetSongVolume(filePath: String) {
        val db = writableDatabase
        db.delete(
            TABLE_SONG_VOLUMES,
            "$COLUMN_FILE_PATH = ?",
            arrayOf(filePath)
        )
    }

    /**
     * Reset all song volume adjustments (clear the entire table).
     * This is useful for a "reset all" feature.
     */
    fun resetAllSongVolumes() {
        val db = writableDatabase
        db.delete(TABLE_SONG_VOLUMES, null, null)
    }

    /**
     * Get the count of songs with custom volume adjustments.
     * @return The number of songs in the database
     */
    fun getSongVolumeCount(): Int {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_SONG_VOLUMES", null)
        return cursor.use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    companion object {
        private const val DATABASE_NAME = "silicon_player_volumes.db"
        private const val DATABASE_VERSION = 2

        private const val TABLE_SONG_VOLUMES = "song_volumes"
        private const val COLUMN_FILE_PATH = "file_path"
        private const val COLUMN_VOLUME_DB = "volume_db"
        private const val COLUMN_IGNORE_CORE_VOLUME = "ignore_core_volume"
        private const val COLUMN_LAST_MODIFIED = "last_modified"

        @Volatile
        private var INSTANCE: VolumeDatabase? = null

        /**
         * Get the singleton instance of VolumeDatabase.
         * @param context Application context
         * @return The VolumeDatabase instance
         */
        fun getInstance(context: Context): VolumeDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VolumeDatabase(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
}
