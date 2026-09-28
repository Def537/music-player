package com.def.musicplayer.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        LyricsEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class,
        FavoriteEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun lyricsDao(): LyricsDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun favoriteDao(): FavoriteDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "musicplayer.db"
                )
                    // Проєкт ще в активній розробці (немає користувачів зі старими даними),
                    // тож на цьому етапі простіше пересоздати БД при зміні схеми,
                    // ніж писати міграції для кожної ітерації. Повернемось до нормальних
                    // Migration-об'єктів, коли застосунок піде в реліз.
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
