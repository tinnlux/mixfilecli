package com.donut.mixfile.server.core.routes.api.webdav

import com.alibaba.fastjson2.into
import com.donut.mixfile.server.core.MixFileServer
import com.donut.mixfile.server.core.interceptCall
import com.donut.mixfile.server.core.objects.FileDataLog
import com.donut.mixfile.server.core.objects.MixShareInfo
import com.donut.mixfile.server.core.routes.api.respondMixFile
import com.donut.mixfile.server.core.routes.api.uploadFile
import com.donut.mixfile.server.core.routes.api.webdav.objects.WebDavFile
import com.donut.mixfile.server.core.routes.api.webdav.objects.WebDavManager
import com.donut.mixfile.server.core.routes.api.webdav.objects.normalizePath
import com.donut.mixfile.server.core.routes.api.webdav.objects.toDavPath
import com.donut.mixfile.server.core.utils.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.RoutingHandler
import io.ktor.server.routing.method
import io.ktor.server.routing.route
import io.ktor.utils.io.*
import kotlinx.io.readByteArray
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

const val API_PATH = "/api/webdav"

val RoutingContext.decodedPath: String get() = call.request.path().decodeURLQueryComponent()

val RoutingContext.davPath: String
    get() = normalizePath(decodedPath.substringAfter(API_PATH))

val RoutingContext.davParentPath: String
    get() = davPath.substringBeforeLast("/", "")

val RoutingContext.davFileName: String
    get() = davPath.substringAfterLast("/").sanitizeFileName()

suspend fun RoutingContext.receiveBytes(limit: Int) =
    call.receiveChannel().readRemaining(limit.toLong()).readByteArray()

val RoutingContext.davShareInfo: MixShareInfo?
    get() = resolveMixShareInfo(
        davPath.substringAfterLast(
            "/"
        )
    )


suspend fun RoutingContext.handleCopy(keep: Boolean, webDavManager: WebDavManager) {
    val overwrite = getHeader("overwrite").contentEquals("T")
    val destination = getHeader("destination")?.decodeURLQueryComponent().let {
        it?.substringAfter(API_PATH)
    }.toDavPath()
    if (destination.isBlank()) {
        call.respond(HttpStatusCode.BadRequest)
        return
    }
    val moved = webDavManager.copyFile(davPath, destination, overwrite, keep)
    if (!moved) {
        call.respond(HttpStatusCode.PreconditionFailed)
        return
    }
    call.respond(HttpStatusCode.Created)
    webDavManager.saveData()
}

fun MixFileServer.getWebDAVRoute(): Route.() -> Unit {
    return {
        interceptCall({
            if (!webDav.loaded) {
                it.respond(HttpStatusCode.ServiceUnavailable, "WebDav is Loading")
            }
        })
        webdav("OPTIONS") {
            call.response.apply {
                header("Allow", "OPTIONS, DELETE, COPY, MOVE, PROPFIND")
                header("Dav", "1")
                header("Ms-Author-Via", "DAV")
            }
            call.respond(HttpStatusCode.OK)
        }
        webdav("GET") {
            if (davFileName.contentEquals("当前目录存档.mix_dav")) {
                val fileList = webDav.listFilesRecursive(davParentPath)
                val data = webDav.dataToBytes(fileList)
                val fileName = "${davParentPath.ifEmpty { "root" }}.mix_dav".encodeURLParameter()
                call.response.apply {
                    header("Cache-Control", "no-cache, no-store, must-revalidate")
                    header("Pragma", "no-cache")
                    header("Expires", "0")
                    header(
                        "Content-Disposition",
                        "attachment;filename=\"$fileName\""
                    )
                }
                call.respondBytes(data, ContentType.Application.OctetStream)
                return@webdav
            }
            val fileNode = webDav.getFile(davPath)
            if (fileNode == null) {
                call.respond(HttpStatusCode.NotFound)
                return@webdav
            }
            val shareInfo = resolveMixShareInfo(fileNode.shareInfoData)
            if (shareInfo == null) {
                call.respond(HttpStatusCode.Conflict)
                return@webdav
            }
            respondMixFile(call, shareInfo)
        }
        webdav("MOVE") {
            handleCopy(false, webDav)
        }
        webdav("COPY") {
            handleCopy(true, webDav)
        }
        webdav("PUT") {
            val fileSize = call.request.contentLength() ?: 0
            if (fileSize < 50.mb && fileSize > 0) {
                if (davFileName.endsWith(".mix_dav")) {
                    val davFileList =
                        webDav.parseDataFromBytes(receiveBytes(50.mb))
                    davFileList.forEach { (s, webDavFiles) ->
                        val path = normalizePath(s)
                        val newPath = normalizePath("${davParentPath}/${path}")
                        val fileList = webDav.WEBDAV_DATA.getOrDefault(newPath, HashSet())
                        fileList.removeAll(webDavFiles)
                        fileList.addAll(webDavFiles)
                        webDav.WEBDAV_DATA[newPath] = fileList
                    }
                    call.respond(HttpStatusCode.Created)
                    webDav.saveData()
                    return@webdav
                }
                if (davFileName.endsWith(".mix_list")) {
                    val dataLogList = decompressGzip(
                        receiveBytes(50.mb)
                    ).into<List<FileDataLog>>()
                    dataLogList.forEach {
                        webDav.addFileNode(
                            davParentPath,
                            WebDavFile(
                                name = it.name,
                                shareInfoData = it.shareInfoData,
                                size = it.size
                            )
                        )
                    }
                    call.respond(HttpStatusCode.Created)
                    webDav.saveData()
                    return@webdav
                }
            }

            val fileList = webDav.listFiles(davParentPath)
            if (fileList == null) {
                call.respond(HttpStatusCode.Conflict)
                return@webdav
            }
            val shareInfo = uploadFile(call.receiveChannel(), davFileName, fileSize, add = false)
            val fileNode =
                WebDavFile(size = fileSize, shareInfoData = shareInfo, name = davFileName)
            webDav.addFileNode(davParentPath, fileNode)
            call.respond(HttpStatusCode.Created)
            webDav.saveData()
        }
        webdav("DELETE") {
            webDav.removeFileNode(davPath)
            call.respond(HttpStatusCode.NoContent)
            webDav.saveData()
        }
        webdav("MKCOL") {
            val fileList = webDav.listFiles(davParentPath)
            if (fileList == null) {
                call.respond(HttpStatusCode.Conflict)
                return@webdav
            }
            val shareInfo = davShareInfo
            if (shareInfo != null) {
                val node = WebDavFile(
                    name = shareInfo.fileName,
                    size = shareInfo.fileSize,
                    shareInfoData = shareInfo.toString()
                )
                webDav.addFileNode(davParentPath, node)
                call.respond(HttpStatusCode.Created)
                webDav.saveData()
                return@webdav
            }
            val node = WebDavFile(isFolder = true, name = davFileName)
            webDav.addFileNode(davParentPath, node)
            call.respond(HttpStatusCode.Created)
            webDav.saveData()
        }
        propfind {
            val depth = getHeader("depth")?.toInt() ?: 0
            if (depth == 0) {
                val file = webDav.getFile(davPath)
                if (file == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@propfind
                }
                val text = """
                <D:multistatus xmlns:D="DAV:">
                ${file.toXML(decodedPath)}
                </D:multistatus>
                """
                call.respondText(
                    contentType = ContentType.Application.Xml,
                    status = HttpStatusCode.MultiStatus,
                    text = """<?xml version="1.0" encoding="utf-8"?>${text}"""
                )
                return@propfind
            }
            val fileList = webDav.listFiles(davPath)
            if (fileList == null) {
                call.respond(HttpStatusCode.NotFound)
                return@propfind
            }
            val xmlFileList = fileList.toMutableList().apply {
                if (isNotEmpty()) {
                    add(WebDavFile("当前目录存档.mix_dav", isFolder = false))
                }
                add(0, WebDavFile(davParentPath.substringAfterLast("/"), isFolder = true))
            }.joinToString(separator = "") {
                it.toXML(decodedPath)
            }
            val text = """
                <D:multistatus xmlns:D="DAV:">
                $xmlFileList
                </D:multistatus>
                """
            call.respondText(
                contentType = ContentType.Application.Xml,
                status = HttpStatusCode.MultiStatus,
                text = """<?xml version="1.0" encoding="utf-8"?>${text}"""
            )
        }
    }
}

fun Route.webdav(method: String, handler: RoutingHandler) {
    route("/{path...}") {
        method(HttpMethod(method)) {
            handle(handler)
        }
    }
}

fun Route.propfind(handler: RoutingHandler) = webdav("PROPFIND", handler)

