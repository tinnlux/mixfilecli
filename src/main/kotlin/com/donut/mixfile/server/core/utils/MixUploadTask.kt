package com.donut.mixfile.server.core.utils

import com.donut.mixfile.server.core.objects.MixShareInfo

interface MixUploadTask {
    var error: Throwable?
    var stopped: Boolean
    suspend fun complete(shareInfo: MixShareInfo)
    val onStop: MutableList<suspend () -> Unit>
    suspend fun updateProgress(size: Long, total: Long)
}