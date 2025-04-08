package com.donut.mixfile.server.core.routes

import com.donut.mixfile.server.core.MixFileServer
import com.donut.mixfile.server.core.httpClient
import com.donut.mixfile.server.core.utils.SortedTask
import com.donut.mixfile.server.core.utils.bean.MixShareInfo
import com.donut.mixfile.server.core.utils.encodeURL
import com.donut.mixfile.server.core.utils.parseFileMimeType
import com.donut.mixfile.server.core.utils.resolveMixShareInfo
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.io.encoding.ExperimentalEncodingApi


@OptIn(ExperimentalEncodingApi::class)
fun MixFileServer.getDownloadRoute(): RoutingHandler {
    return route@{
        val param = call.parameters
        val shareInfoData = param["s"]
        if (shareInfoData == null) {
            call.respondText("分享信息为空", status = HttpStatusCode.InternalServerError)
            return@route
        }
        val shareInfo = resolveMixShareInfo(shareInfoData)
        if (shareInfo == null) {
            call.respondText("解析文件失败", status = HttpStatusCode.InternalServerError)
            return@route
        }
        val mixFile = shareInfo.fetchMixFile(httpClient)
        if (mixFile == null) {
            call.respondText(
                "解析文件索引失败",
                status = HttpStatusCode.InternalServerError
            )
            return@route
        }

        val referer =
            (param["referer"] ?: "").ifBlank { shareInfo.referer }
        val name =
            (param["name"] ?: "").ifBlank { shareInfo.fileName }

        var contentLength = shareInfo.fileSize
        val range: LongRange? = call.request.ranges()?.mergeToSingle(contentLength)
        call.response.apply {
            header(
                "Content-Disposition",
                "inline; filename=\"${name.encodeURL()}\""
            )
        }
        var fileList = mixFile.fileList.map { it to 0 }
        if (range != null) {
            fileList = mixFile.getFileListByStartRange(range.first)
            call.response.apply {
                header("Accept-Ranges", "bytes")
                status(HttpStatusCode.PartialContent)
                contentRange(range, mixFile.fileSize)
            }
            contentLength = mixFile.fileSize - range.first
        }
        responseDownloadFileStream(call, fileList, contentLength, shareInfo, referer, name)
    }
}

private suspend fun MixFileServer.responseDownloadFileStream(
    call: ApplicationCall,
    fileDataList: List<Pair<String, Int>>,
    contentLength: Long,
    shareInfo: MixShareInfo,
    referer: String = shareInfo.referer,
    name: String = shareInfo.fileName
) {
    coroutineScope {
        val fileList = fileDataList.toMutableList()
        call.respondBytesWriter(
            contentType = ContentType.parse(name.parseFileMimeType()).withCharset(Charsets.UTF_8),
            contentLength = contentLength
        ) {
            val sortedTask = SortedTask(downloadTaskCount)
            val tasks = mutableListOf<Deferred<Unit>>()
            while (!isClosedForWrite && fileList.isNotEmpty()) {
                val currentFile = fileList.removeAt(0)
                val taskOrder = -fileList.size
                sortedTask.prepareTask(taskOrder)
                tasks.add(async {
                    val (url, range) = currentFile
                    val dataBytes =
                        shareInfo.fetchFile(url, httpClient, referer)
                    sortedTask.addTask(taskOrder) {
                        val dataToWrite = when {
                            range == 0 -> dataBytes
                            range < 0 -> dataBytes.copyOfRange(0, -range) //一般无 < 0 的情况
                            else -> dataBytes.copyOfRange(range, dataBytes.size)
                        }
                        try {
                            writeFully(dataToWrite)
                            onDownloadData(dataToWrite)
                        } catch (e: Exception) {
                            close(e)
                        }
                    }
                    sortedTask.execute()
                })
            }
            tasks.awaitAll()
        }
    }
}