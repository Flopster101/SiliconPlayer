package com.flopster101.siliconplayer

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Database for storing per-song volume adjustments.
 * Each song is identified by its file path and has an associated volume in dB.
 */
class VolumeDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_SONG_VOLUMES (
                $COLUMN_FILE_PATH TEXT PRIMARY KEY,
                $COLUMN_VOLUME_DB REAL NOT NULL,
                $COLUMN_LAST_MODIFIED INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX idx_song_volumes_path ON $TABLE_SONG_VOLUMES($COLUMN_FILE_PATH)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Future migrations can be added here
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SONG_VOLUMES")
        onCreate(db)
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
            put(COLUMN_FILE_PATH, filePath)
            put(COLUMN_VOLUME_DB, volumeDb)
            put(COLUMN_LAST_MODIFIED, System.currentTimeMillis())
        }

        db.insertWithOnConflict(
            TABLE_SONG_VOLUMES,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
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
        private const val DATABASE_VERSION = 1

        private const val TABLE_SONG_VOLUMES = "song_volumes"
        private const val COLUMN_FILE_PATH = "file_path"
        private const val COLUMN_VOLUME_DB = "volume_db"
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
