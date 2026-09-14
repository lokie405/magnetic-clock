package com.example.magneticclock.data

import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

interface TelegramApiService {
    
    @GET
    suspend fun getUpdates(
        @Url url: String,
        @Query("offset") offset: Long? = null,
        @Query("limit") limit: Int? = null
    ): TelegramResponse<List<TelegramUpdate>>

    @GET
    suspend fun getFile(
        @Url url: String,
        @Query("file_id") fileId: String
    ): TelegramResponse<TelegramFile>
}

data class TelegramUpdate(
    val update_id: Long,
    val channel_post: TelegramMessage?,
    val message: TelegramMessage?
)
