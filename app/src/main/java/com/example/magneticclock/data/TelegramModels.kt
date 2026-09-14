package com.example.magneticclock.data

import com.google.gson.annotations.SerializedName

data class TelegramResponse<T>(
    @SerializedName("ok") val ok: Boolean,
    @SerializedName("result") val result: T
)

data class TelegramMessage(
    @SerializedName("message_id") val messageId: Long,
    @SerializedName("audio") val audio: TelegramAudio?,
    @SerializedName("text") val text: String?,
    @SerializedName("date") val date: Long
)

data class TelegramAudio(
    @SerializedName("file_id") val fileId: String,
    @SerializedName("file_unique_id") val fileUniqueId: String,
    @SerializedName("duration") val duration: Int,
    @SerializedName("title") val title: String?,
    @SerializedName("performer") val performer: String?,
    @SerializedName("file_name") val fileName: String?,
    @SerializedName("mime_type") val mimeType: String?,
    @SerializedName("file_size") val fileSize: Long?
)

data class TelegramFile(
    @SerializedName("file_id") val fileId: String,
    @SerializedName("file_path") val filePath: String?
)

data class MusicTrack(
    val id: String,
    val title: String,
    val artist: String,
    val url: String,
    val durationMs: Long
)
