package com.example.magneticclock.data

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken

@OptIn(UnstableApi::class)
object MusicPlayerManager {
    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val httpClient = OkHttpClient()
    private val gson = Gson()
    
    // Playback state
    var isPlaying by mutableStateOf(false)
    var currentTrack by mutableStateOf<MusicTrack?>(null)
    var playbackPosition by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)
    var currentTrackIndex by mutableStateOf(0) 
    
    val playlist = mutableStateListOf<MusicTrack>()
    private var isInitialized = false
    private var isFetching = false

    fun init(context: Context) {
        if (isInitialized) return
        AppLogger.i("MusicPlayerManager: Ініціалізація (v4 - Persistent Cache)...")
        
        player = ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    this@MusicPlayerManager.isPlaying = playing
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    val uri = mediaItem?.localConfiguration?.uri.toString()
                    currentTrack = playlist.find { it.url == uri }
                    currentTrackIndex = currentMediaItemIndex + 1
                }

                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        this@MusicPlayerManager.duration = duration.coerceAtLeast(0)
                        currentTrackIndex = currentMediaItemIndex + 1
                    }
                }
            })
        }

        mediaSession = MediaSession.Builder(context, player!!).build()

        // Позиція оновлення
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
        
        // Завантажуємо кешований плейлист при старті
        val cached = MusicCacheManager.loadPlaylist(context)
        if (cached.isNotEmpty()) {
            playlist.clear()
            playlist.addAll(cached)
            updatePlayerPlaylist()
        }
        
        isInitialized = true
    }

    fun fetchPlaylist(context: Context, token: String, channelId: String) {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty() || isFetching) return
        
        isFetching = true
        scope.launch(Dispatchers.IO) {
            try {
                AppLogger.i("Оновлення плейлиста. Пошук нових треків...")
                
                // 1. Отримуємо нові оновлення від Telegram
                val updatesReq = Request.Builder()
                    .url("https://api.telegram.org/bot$cleanToken/getUpdates?limit=100")
                    .build()

                val updatesBody = httpClient.newCall(updatesReq).execute().body?.string() ?: ""
                val type = object : TypeToken<TelegramResponse<List<TelegramUpdate>>>() {}.type
                val updatesResponse = gson.fromJson<TelegramResponse<List<TelegramUpdate>>>(updatesBody, type)

                if (updatesResponse?.ok == true) {
                    val newTracks = updatesResponse.result
                        .mapNotNull { it.channel_post ?: it.message }
                        .filter { it.audio != null }
                        .map { msg ->
                            val audio = msg.audio!!
                            MusicTrack(
                                id = audio.fileUniqueId,
                                fileId = audio.fileId,
                                title = audio.title ?: audio.fileName ?: "Unknown",
                                artist = audio.performer ?: "Telegram",
                                url = "", // Буде оновлено нижче
                                durationMs = audio.duration * 1000L
                            )
                        }

                    // 2. Об'єднуємо з існуючим плейлистом (унікальні за id)
                    val currentList = playlist.toList()
                    val combinedList = (currentList + newTracks).distinctBy { it.id }
                    
                    AppLogger.d("Всього у списку: ${combinedList.size} треків. Оновлюємо посилання...")

                    // 3. Оновлюємо URL для ВСІХ треків (бо вони діють лише 1 годину)
                    val refreshedTracks = combinedList.map { track ->
                        val fileReq = Request.Builder()
                            .url("https://api.telegram.org/bot$cleanToken/getFile?file_id=${track.fileId}")
                            .build()
                        
                        val fileBody = httpClient.newCall(fileReq).execute().body?.string() ?: ""
                        val fileJson = gson.fromJson(fileBody, JsonObject::class.java)
                        val filePath = fileJson?.get("result")?.asJsonObject?.get("file_path")?.asString
                        
                        if (filePath != null) {
                            track.copy(url = "https://api.telegram.org/file/bot$cleanToken/$filePath")
                        } else track
                    }.filter { it.url.isNotEmpty() }

                    withContext(Dispatchers.Main) {
                        playlist.clear()
                        playlist.addAll(refreshedTracks)
                        MusicCacheManager.savePlaylist(context, refreshedTracks)
                        updatePlayerPlaylist()
                        AppLogger.i("Плейлист оновлено! Доступно треків: ${playlist.size}")
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("Помилка синхронізації з Telegram", e)
            } finally {
                isFetching = false
            }
        }
    }

    private fun updatePlayerPlaylist() {
        player?.let { p ->
            val wasPlaying = p.isPlaying
            val currentPos = p.currentPosition
            val currentIndex = p.currentMediaItemIndex
            
            p.clearMediaItems()
            playlist.forEach { track ->
                val mediaItem = MediaItem.Builder()
                    .setUri(track.url)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(track.title)
                            .setArtist(track.artist)
                            .build()
                    )
                    .build()
                p.addMediaItem(mediaItem)
            }
            p.prepare()
            
            // Відновлюємо стан якщо це можливо
            if (currentIndex < playlist.size && currentIndex >= 0) {
                p.seekTo(currentIndex, currentPos)
            }
            if (wasPlaying) p.play()
        }
    }

    fun playPause() {
        player?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun next() { player?.seekToNext() }
    fun previous() { player?.seekToPrevious() }
    fun seekTo(position: Long) { player?.seekTo(position) }

    fun release() {
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
        isInitialized = false
    }
}
