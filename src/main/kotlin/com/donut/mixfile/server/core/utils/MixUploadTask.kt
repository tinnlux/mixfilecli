package com.donut.mixfile.server.core.utils

import com.donut.mixfile.server.core.utils.bean.MixShareInfo

interface MixUploadTask {
    var error: Throwable?
    var stopped: Boolean
    suspend fun complete(shareInfo: MixShareInfo)
    var onStop: () -> Unit
    suspend fun updateProgress(size: Long, total: Long)
}