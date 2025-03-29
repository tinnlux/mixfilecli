package com.donut.mixfile.server.core.routes

import com.alibaba.fastjson2.toJSONString
import com.donut.mixfile.server.core.MixFileServer
import com.donut.mixfile.server.core.utils.isNotNull
import com.donut.mixfile.server.core.utils.parseFileMimeType
import com.donut.mixfile.server.core.utils.resolveMixShareInfo
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.header
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.utils.io.jvm.javaio.toOutputStream

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
                fileStream.copyTo(this.toOutputStream())
            }
        }
        route("/api") {
            get("/download", getDownloadRoute())
            put("/upload", getUploadRoute())
            get("/upload_history") {
                if (call.request.header("origin").isNotNull()) {
                    return@get call.respondText("此接口禁止跨域", status = HttpStatusCode.Forbidden)
                }
                call.respond(getFileHistory())
            }
            get("/file_info") {
                val shareInfoStr = call.request.queryParameters["s"]
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

