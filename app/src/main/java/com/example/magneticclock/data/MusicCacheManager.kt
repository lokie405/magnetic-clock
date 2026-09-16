package com.example.magneticclock.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

object MusicCacheManager {
    private const val CACHE_FILE = "music_playlist_cache.json"
    private val gson = Gson()

    fun savePlaylist(context: Context, playlist: List<MusicTrack>) {
        try {
            val file = File(context.filesDir, CACHE_FILE)
            // Зберігаємо метадані (URL будуть оновлені при завантаженні/оновленні)
            val json = gson.toJson(playlist)
            file.writeText(json)
            AppLogger.d("Плейлист збережено в кеш: ${playlist.size} треків")
        } catch (e: Exception) {
            AppLogger.e("Помилка збереження кешу музики", e)
        }
    }

    fun loadPlaylist(context: Context): List<MusicTrack> {
        return try {
            val file = File(context.filesDir, CACHE_FILE)
            if (!file.exists()) return emptyList()
            val json = file.readText()
            val type = object : TypeToken<List<MusicTrack>>() {}.type
            val list = gson.fromJson<List<MusicTrack>>(json, type) ?: emptyList()
            AppLogger.i("Завантажено ${list.size} треків із локального кешу")
            list
        } catch (e: Exception) {
            AppLogger.e("Помилка завантаження кешу музики", e)
            emptyList()
        }
    }
}
