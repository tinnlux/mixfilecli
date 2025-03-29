package com.donut.mixfile.server.core.routes

import com.donut.mixfile.server.core.MixFileServer
import com.donut.mixfile.server.core.Uploader
import com.donut.mixfile.server.core.aes.generateRandomByteArray
import com.donut.mixfile.server.core.httpClient
import com.donut.mixfile.server.core.utils.MixUploadTask
import com.donut.mixfile.server.core.utils.bean.MixFile
import com.donut.mixfile.server.core.utils.bean.MixShareInfo
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.contentLength
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingHandler
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Semaphore
import kotlinx.io.readByteArray
import kotlin.math.ceil


fun MixFileServer.getUploadRoute(): RoutingHandler {
    return route@{
        val key = generateRandomByteArray(32)
        val name = call.request.queryParameters["name"]
        val add = call.request.queryParameters["add"] ?: "true"
        if (name.isNullOrEmpty()) {
            call.respondText("需要文件名称", status = HttpStatusCode.InternalServerError)
            return@route
        }
        val size = call.request.contentLength() ?: 0
        if (size <= 0L) {
            call.respondText("文件大小不合法", status = HttpStatusCode.InternalServerError)
            return@route
        }
        val uploadTask = getUploadTask(call, name, size, add.toBoolean())
        currentCoroutineContext().job.invokeOnCompletion {
            uploadTask.error = it
            uploadTask.stopped = true
        }
        val uploader = getUploader()
        val head = uploader.genHead(httpClient) ?: genDefaultImage()
        val mixUrl =
            uploadFile(call.receiveChannel(), head, uploader, key, fileSize = size, uploadTask)
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
        uploadTask.complete(mixShareInfo)
        call.respondText(mixShareInfo.toString())
    }
}

suspend fun MixFileServer.uploadFile(
    channel: ByteReadChannel,
    head: ByteArray,
    uploader: Uploader,
    secret: ByteArray,
    fileSize: Long,
    uploadTask: MixUploadTask,
): String? {
    val semaphore = Semaphore(uploadTaskCount)
    return coroutineScope {
        val context = currentCoroutineContext()
        uploadTask.onStop = {
            context.cancel()
        }
        val chunkSize = uploader.chunkSize
        //固定大小string list
        val fileListLength = ceil(fileSize.toDouble() / chunkSize).toInt()
        val fileList = List(fileListLength) { "" }.toMutableList()
        var fileIndex = 0
        val tasks = mutableListOf<Deferred<Unit?>>()

        while (!channel.isClosedForRead) {
            semaphore.acquire()
            val fileData = channel.readRemaining(chunkSize).readByteArray()
            val currentIndex = fileIndex
            fileIndex++
            tasks.add(async {
                try {
                    val url = uploader.upload(head, fileData, secret, this@uploadFile)
                    fileList[currentIndex] = url
                    uploadTask.updateProgress(fileData.size.toLong(), fileSize)
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
        val mixFileData = mixFile.toBytes()
        val mixFileUrl =
            uploader.upload(head, mixFileData, secret, this@uploadFile)
        return@coroutineScope mixFileUrl
    }
}