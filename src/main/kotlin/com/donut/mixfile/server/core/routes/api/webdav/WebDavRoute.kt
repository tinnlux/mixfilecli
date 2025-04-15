package com.donut.mixfile.server.core.routes.api.webdav

import com.donut.mixfile.server.core.MixFileServer
import com.donut.mixfile.server.core.routes.api.respondMixFile
import com.donut.mixfile.server.core.routes.api.uploadFile
import com.donut.mixfile.server.core.routes.api.webdav.utils.WebDavFile
import com.donut.mixfile.server.core.routes.api.webdav.utils.WebDavManager
import com.donut.mixfile.server.core.routes.api.webdav.utils.normalizePath
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
    val overwrite = call.request.header("overwrite").contentEquals("T")
    val destination = call.request.header("destination")?.decodeURLQueryComponent().let {
        it?.substringAfter("/api/webdav/")
    }
    if (destination.isNullOrBlank()) {
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
            val fileList = webDav.listFiles(davPath)
            if (fileList == null) {
                call.respond(HttpStatusCode.NotFound)
                return@propfind
            }
            val xmlFileList = fileList.toMutableList().apply {
                add(0, WebDavFile(davParentPath.substringAfterLast("/"), isFolder = true))
            }.joinToString(separator = "\n") {
                it.toXML(davPath)
            }

            call.respondText(
                contentType = ContentType.Application.Xml,
                status = HttpStatusCode.MultiStatus,
                text = """
                  <?xml version="1.0" encoding="utf-8"?>
                  <d:multistatus xmlns:d="DAV:">
                     $xmlFileList
                  </d:multistatus>
                """.trimIndent()
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

