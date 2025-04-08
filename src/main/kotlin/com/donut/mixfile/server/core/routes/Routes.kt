package com.donut.mixfile.server.core.routes

import com.alibaba.fastjson2.toJSONString
import com.donut.mixfile.server.core.MixFileServer
import com.donut.mixfile.server.core.utils.isNotNull
import com.donut.mixfile.server.core.utils.parseFileMimeType
import com.donut.mixfile.server.core.utils.resolveMixShareInfo
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*

fun MixFileServer.getRoutes(): Routing.() -> Unit {

    return {
        get("{param...}") {
            val file = call.request.path().substring(1).ifEmpty {
                "index.html"
            }
            val fileStream = getStaticFile(file) ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respondBytesWriter(
                contentType = ContentType.parse(file.parseFileMimeType())
            ) {
                fileStream.toByteReadChannel().copyTo(this)
            }
        }
        route("/api") {
            get("/download/{name?}", getDownloadRoute())
            put("/upload/{name?}", getUploadRoute())
            get("/upload_history") {
                if (call.request.header("origin").isNotNull()) {
                    return@get call.respondText("此接口禁止跨域", status = HttpStatusCode.Forbidden)
                }
                call.respond(getFileHistory())
            }
            get("/file_info") {
                val shareInfoStr = call.parameters["s"]
                if (shareInfoStr == null) {
                    call.respondText("分享信息为空", status = HttpStatusCode.InternalServerError)
                    return@get
                }
                val shareInfo = resolveMixShareInfo(shareInfoStr)
                if (shareInfo == null) {
                    call.respondText(
                        "分享信息解析失败",
                        status = HttpStatusCode.InternalServerError
                    )
                    return@get
                }
                call.respondText(object {
                    val name = shareInfo.fileName
                    val size = shareInfo.fileSize
                }.toJSONString())
            }
        }
    }
}

