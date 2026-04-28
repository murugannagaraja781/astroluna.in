package com.astroluna.data.model

import com.google.gson.annotations.SerializedName

data class VideoItem(
    @SerializedName("title") val title: String,
    @SerializedName("youtubeUrl") val url: String,
    @SerializedName("category") val category: String
)
