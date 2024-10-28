package com.donut.mixfiledesktop.server.routes

import com.donut.mixfilecli.config
import com.donut.mixfiledesktop.server.Uploader
import com.donut.mixfiledesktop.server.getCurrentUploader
import com.donut.mixfiledesktop.server.utils.bean.MixFile
import com.donut.mixfiledesktop.server.utils.bean.MixShareInfo
import com.donut.mixfiledesktop.util.file.addUploadLog
import com.donut.mixfiledesktop.util.generateRandomByteArray
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlin.math.ceil

var UPLOAD_TASK_COUNT = config.uploadTask


fun getUploadRoute(): suspend PipelineContext<Unit, ApplicationCall>.(Unit) -> Unit {
    return route@{
        val key = generateRandomByteArray(16)
        val name = call.request.queryParameters["name"]
        if (name.isNullOrEmpty()) {
            call.respondText("需要文件名称", status = HttpStatusCode.InternalServerError)
            return@route
        }
        val uploader = getCurrentUploader()
        val head = uploader.genHead()
        val size = call.request.contentLength() ?: 0
        if (size <= 0L) {
            call.respondText("文件大小不合法", status = HttpStatusCode.InternalServerError)
            return@route
        }
        val mixUrl = uploadFile(call.receiveChannel(), head, uploader, key, fileSize = size)
        if (mixUrl == null) {
            call.respondText("上传失败", status = HttpStatusCode.InternalServerError)
            return@route
        }
        val mixShareInfo =
            MixShareInfo(
                fileName = name,
                fileSize = size,
                headSize = head.size,
                url = mixUrl,
                key = MixShareInfo.ENCODER.encode(key),
                referer = uploader.referer
            )
        call.respondText(mixShareInfo.toString())
        addUploadLog(mixShareInfo)
    }
}

suspend fun uploadFile(
    channel: ByteReadChannel,
    head: ByteArray,
    uploader: Uploader,
    secret: ByteArray,
    fileSize: Long,
): String? {
    val semaphore = Semaphore(UPLOAD_TASK_COUNT)

    return coroutineScope {
        val chunkSize = uploader.chunkSize
        //固定大小string list
        val fileListLength = ceil(fileSize.toDouble() / chunkSize).toInt()
        val fileList = List(fileListLength) { "" }.toMutableList()
        var fileIndex = 0
        val tasks = mutableListOf<Deferred<Unit?>>()

        while (!channel.isClosedForRead) {
            semaphore.acquire()
            val fileData = channel.readRemaining(chunkSize).readBytes()
            val currentIndex = fileIndex
            fileIndex++
            tasks.add(async {
                try {
                    val url = uploader.upload(head, fileData, secret) ?: return@async null
                    fileList[currentIndex] = url
                } finally {
                    semaphore.release()
                }
            })
        }
        tasks.awaitAll()
        if (fileList.any { it.isEmpty() }) {
            return@coroutineScope null
        }
        val mixFile =
            MixFile(chunkSize = chunkSize, version = 0, fileList = fileList, fileSize = fileSize)
        val mixFileUrl =
            uploader.upload(head, mixFile.toBytes(), secret)
                ?: return@coroutineScope null
        return@coroutineScope mixFileUrl
    }
}