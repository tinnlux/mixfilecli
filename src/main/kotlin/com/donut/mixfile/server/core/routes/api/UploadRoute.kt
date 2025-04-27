package com.donut.mixfile.server.core.routes.api

import com.donut.mixfile.server.core.MixFileServer
import com.donut.mixfile.server.core.Uploader
import com.donut.mixfile.server.core.aes.generateRandomByteArray
import com.donut.mixfile.server.core.objects.MixFile
import com.donut.mixfile.server.core.objects.MixShareInfo
import com.donut.mixfile.server.core.utils.MixUploadTask
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.io.readByteArray
import kotlin.math.ceil


fun MixFileServer.getUploadRoute(): RoutingHandler {
    return route@{
        val name = call.parameters["name"]
        val add = call.parameters["add"] ?: "true"
        if (name.isNullOrEmpty()) {
            call.respondText("需要文件名称", status = HttpStatusCode.InternalServerError)
            return@route
        }

        val size = call.request.contentLength() ?: 0

        call.respondText(uploadFile(call.receiveChannel(), name, size, add.toBoolean()) {
            call.respondText("上传已取消", status = HttpStatusCode.InternalServerError)
        })
    }
}

suspend fun MixFileServer.uploadFile(
    channel: ByteReadChannel,
    name: String,
    size: Long,
    add: Boolean = true,
    key: ByteArray = generateRandomByteArray(32),
    onStop: suspend () -> Unit = {}
): String {
    val uploadTask = getUploadTask(name, size, add)
    uploadTask.onStop.add(onStop)
    currentCoroutineContext().job.invokeOnCompletion {
        uploadTask.error = it
        uploadTask.stopped = true
    }
    val uploader = getUploader()
    val head = uploader.genHead(httpClient) ?: genDefaultImage()
    val mixUrl =
        doUploadFile(channel, head, uploader, key, fileSize = size, uploadTask)
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
    return mixShareInfo.toString()
}

private suspend fun MixFileServer.doUploadFile(
    channel: ByteReadChannel,
    head: ByteArray,
    uploader: Uploader,
    secret: ByteArray,
    fileSize: Long,
    uploadTask: MixUploadTask,
): String {

    val semaphore = Semaphore(uploadTaskCount)
    return coroutineScope {
        uploadTask.onStop.add(0) {
            channel.cancel()
        }
        val chunkSize = uploader.chunkSize
        //固定大小string list
        val fileListLength = ceil(fileSize.toDouble() / chunkSize).toInt()
        val fileList = List(fileListLength) { "" }.toMutableList()
        var fileIndex = 0
        val tasks = mutableListOf<Deferred<Unit?>>()

        while (!channel.isClosedForRead) {
            semaphore.acquire()
            val fileData = channel.readRemaining(chunkSize.toLong()).readByteArray()
            val currentIndex = fileIndex
            fileIndex++
            tasks.add(async {
                try {
                    val url = uploader.upload(head, fileData, secret, this@doUploadFile)
                    fileList[currentIndex] = url
                    uploadTask.updateProgress(fileData.size.toLong(), fileSize)
                } finally {
                    semaphore.release()
                }
            })
        }
        tasks.awaitAll()
        if (fileList.any { it.isEmpty() }) {
            throw Exception("上传失败")
        }
        val mixFile =
            MixFile(chunkSize = chunkSize, version = 0, fileList = fileList, fileSize = fileSize)
        val mixFileData = mixFile.toBytes()
        val mixFileUrl =
            uploader.upload(head, mixFileData, secret, this@doUploadFile)
        return@coroutineScope mixFileUrl
    }
}