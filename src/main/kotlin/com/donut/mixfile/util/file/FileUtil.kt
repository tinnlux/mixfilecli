package com.donut.mixfile.util.file


import com.donut.mixfile.server.core.objects.FileDataLog
import com.donut.mixfile.server.core.objects.MixShareInfo


var uploadLogs = listOf<FileDataLog>()

fun addUploadLog(shareInfo: MixShareInfo) {
    val uploadLog = shareInfo.toDataLog()
    if (uploadLogs.size > 1000) {
        uploadLogs = uploadLogs.drop(1)
    }
    uploadLogs = uploadLogs + uploadLog
}




