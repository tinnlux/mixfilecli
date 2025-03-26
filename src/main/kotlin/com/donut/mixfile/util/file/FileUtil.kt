package com.donut.mixfile.util.file


import com.donut.mixfile.server.core.utils.bean.MixShareInfo
import com.donut.mixfile.util.FileDataLog
import com.github.amr.mimetypes.MimeTypes

fun String.getFileExtension(): String {
    val index = this.lastIndexOf('.')
    return if (index == -1) "" else this.substring(index + 1).lowercase()
}

fun String.parseFileMimeType() = MimeTypes.getInstance()
    .getByExtension(this.getFileExtension())?.mimeType ?: "application/octet-stream"

fun MixShareInfo.toDataLog(): FileDataLog {
    return FileDataLog(
        shareInfoData = this.toString(),
        name = this.fileName,
        size = this.fileSize
    )
}

var uploadLogs = listOf<FileDataLog>()
fun addUploadLog(shareInfo: MixShareInfo) {
    val uploadLog = shareInfo.toDataLog()
    if (uploadLogs.size > 1000) {
        uploadLogs = uploadLogs.drop(1)
    }
    uploadLogs = uploadLogs + uploadLog
}




