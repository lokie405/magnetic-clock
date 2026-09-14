package com.example.magneticclock.data

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@OptIn(UnstableApi::class)
object MusicPlayerManager {
    private var player: ExoPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Playback state
    var isPlaying by mutableStateOf(false)
    var currentTrack by mutableStateOf<MusicTrack?>(null)
    var playbackPosition by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)
    var currentTrackIndex by mutableStateOf(0) // Поточний індекс треку (1-based для UI)
    
    val playlist = mutableStateListOf<MusicTrack>()
    private var isInitialized = false
    private var isFetching = false

    private val api: TelegramApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.telegram.org/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TelegramApiService::class.java)
    }

    fun init(context: Context) {
        if (isInitialized) return
        AppLogger.i("MusicPlayerManager: Ініціалізація (v2 - OkHttp)...")
        
        player = ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL // Зациклення плейлиста: перший після останнього
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    this@MusicPlayerManager.isPlaying = playing
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    val uri = mediaItem?.localConfiguration?.uri.toString()
                    this@MusicPlayerManager.currentTrack = playlist.find { it.url == uri }
                    this@MusicPlayerManager.currentTrackIndex = currentMediaItemIndex + 1
                }

                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        this@MusicPlayerManager.duration = player?.duration?.coerceAtLeast(0) ?: 0L
                        this@MusicPlayerManager.currentTrackIndex = currentMediaItemIndex + 1
                    }
                }
            })
        }

        // Position update job
        scope.launch {
            while (isActive) {
                player?.let {
                    if (it.isPlaying) {
                        playbackPosition = it.currentPosition
                        duration = it.duration.coerceAtLeast(0)
                    }
                }
                delay(500)
            }
        }
        
        isInitialized = true
    }

    fun fetchPlaylist(token: String, channelId: String) {
        val cleanToken = token.trim()
        
        if (cleanToken.isEmpty()) {
            AppLogger.e("Помилка: Token порожній")
            return
        }

        if (isFetching) {
            AppLogger.d("Запит вже виконується, зачекайте...")
            return
        }
        
        isFetching = true
        scope.launch(Dispatchers.IO) {
            try {
                AppLogger.i("Спроба отримати плейлист. Токен: ${cleanToken.take(5)}...${cleanToken.takeLast(5)}")
                
                // Використовуємо OkHttp для прямого запиту, щоб уникнути будь-якого кодування URL від Retrofit
                val client = okhttp3.OkHttpClient()
                val request = okhttp3.Request.Builder()
                    .url("https://api.telegram.org/bot$cleanToken/getUpdates?limit=100")
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string()
                    if (!response.isSuccessful || body == null) {
                        AppLogger.e("Telegram за запитом getUpdates повернув помилку: ${response.code}")
                        return@launch
                    }

                    val gson = com.google.gson.Gson()
                    val type = object : com.google.gson.reflect.TypeToken<TelegramResponse<List<TelegramUpdate>>>() {}.type
                    val telegramResponse = gson.fromJson<TelegramResponse<List<TelegramUpdate>>>(body, type)

                    if (!telegramResponse.ok) {
                        AppLogger.e("Telegram API повернув ok=false. Перевірте токен.")
                        return@launch
                    }

                    val allUpdates = telegramResponse.result
                    AppLogger.d("Отримано ${allUpdates.size} сирих подій від Telegram")

                    if (allUpdates.isEmpty()) {
                        // Спробуємо перевірити самого бота
                        val meRequest = okhttp3.Request.Builder()
                            .url("https://api.telegram.org/bot$cleanToken/getMe")
                            .build()
                        client.newCall(meRequest).execute().use { meRes ->
                            AppLogger.d("Перевірка бота (getMe): ${meRes.body?.string()}")
                        }
                    }

                    val tracks = allUpdates
                        .mapNotNull { it.channel_post ?: it.message } // Підтримка і каналів, і груп
                        .filter { post ->
                            val hasAudio = post.audio != null
                            // Логуємо будь-який текст, щоб зрозуміти чи бот бачить канал
                            if (post.text != null) {
                                AppLogger.d("Бот бачить повідомлення (текст): ${post.text.take(15)}...")
                            }
                            hasAudio
                        }
                        .mapNotNull { msg ->
                            val audio = msg.audio!!
                            AppLogger.i("Знайдено аудіо: ${audio.title ?: audio.fileName}")
                            
                            val fileReq = okhttp3.Request.Builder()
                                .url("https://api.telegram.org/bot$cleanToken/getFile?file_id=${audio.fileId}")
                                .build()
                            
                            val fileBody = client.newCall(fileReq).execute().body?.string()
                            val fileResponseJson = gson.fromJson(fileBody, com.google.gson.JsonObject::class.java)
                            
                            val ok = fileResponseJson.get("ok")?.asBoolean ?: false
                            val result = fileResponseJson.get("result")?.asJsonObject
                            val filePath = result?.get("file_path")?.asString
                            
                            if (ok && filePath != null) {
                                val downloadUrl = "https://api.telegram.org/file/bot$cleanToken/$filePath"
                                MusicTrack(
                                    id = audio.fileUniqueId,
                                    title = audio.title ?: audio.fileName ?: "Unknown",
                                    artist = audio.performer ?: "Telegram",
                                    url = downloadUrl,
                                    durationMs = audio.duration * 1000L
                                )
                            } else {
                                AppLogger.e("Помилка getFile для ${audio.fileId}: $fileBody")
                                null
                            }
                        }

                    withContext(Dispatchers.Main) {
                        if (tracks.isNotEmpty()) {
                            playlist.clear()
                            playlist.addAll(tracks)
                            updatePlayerPlaylist()
                            AppLogger.i("Успішно додано ${tracks.size} треків!")
                        } else {
                            AppLogger.w("Музичних файлів не знайдено. Надішліть MP3 у канал!")
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("Помилка при прямій роботі з Telegram API", e)
            } finally {
                isFetching = false
            }
        }
    }

    private fun updatePlayerPlaylist() {
        player?.let { p ->
            val wasPlaying = p.isPlaying
            p.clearMediaItems()
            playlist.forEach { track ->
                p.addMediaItem(MediaItem.fromUri(track.url))
            }
            p.prepare()
            if (wasPlaying) p.play()
        }
    }

    fun playPause() {
        player?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    fun next() {
        player?.seekToNext()
    }

    fun previous() {
        player?.seekToPrevious()
    }

    fun seekTo(position: Long) {
        player?.seekTo(position)
    }

    fun release() {
        player?.release()
        player = null
        isInitialized = false
    }
}
