package com.donut.mixfile.server.core.routes.api.webdav

import com.donut.mixfile.server.core.MixFileServer
import com.donut.mixfile.server.core.interceptCall
import com.donut.mixfile.server.core.routes.api.respondMixFile
import com.donut.mixfile.server.core.routes.api.uploadFile
import com.donut.mixfile.server.core.routes.api.webdav.utils.WebDavFile
import com.donut.mixfile.server.core.routes.api.webdav.utils.WebDavManager
import com.donut.mixfile.server.core.routes.api.webdav.utils.normalizePath
import com.donut.mixfile.server.core.routes.api.webdav.utils.toDavPath
import com.donut.mixfile.server.core.utils.getHeader
import com.donut.mixfile.server.core.utils.resolveMixShareInfo
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.RoutingHandler
import io.ktor.server.routing.method
import io.ktor.server.routing.route


val RoutingContext.davPath: String
    get() = normalizePath(
        (call.parameters.getAll("path") ?: emptyList()).joinToString("/")
    )

val RoutingContext.davParentPath: String
    get() = davPath.substringBeforeLast("/", "")

val RoutingContext.davFileName: String
    get() = davPath.substringAfterLast("/")


suspend fun RoutingContext.handleCopy(keep: Boolean, webDavManager: WebDavManager) {
    val overwrite = getHeader("overwrite").contentEquals("T")
    val destination = getHeader("destination")?.decodeURLQueryComponent().let {
        it?.substringAfter("/api/webdav/")
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
            val fileList = webDav.listFiles(davParentPath)
            if (fileList == null) {
                call.respond(HttpStatusCode.Conflict)
                return@webdav
            }
            val fileSize = call.request.contentLength() ?: 0
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
                ${file.toXML(call.request.uri)}
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
                add(0, WebDavFile(davParentPath.substringAfterLast("/"), isFolder = true))
            }.joinToString(separator = "") {
                it.toXML(call.request.uri)
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

