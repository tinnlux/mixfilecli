package com.donut.mixfiledesktop.server.routes


import com.donut.mixfiledesktop.util.file.resolveMixShareInfo
import com.donut.mixfiledesktop.util.file.uploadLogs
import com.donut.mixfiledesktop.util.isNotNull
import com.donut.mixfiledesktop.util.toJsonString
import com.google.gson.JsonObject
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.request.header
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun getRoutes(): Routing.() -> Unit {

    return {
        staticResources("/", "files", "index.html")
        route("/api") {
            get("/download", getDownloadRoute())
            put("/upload", getUploadRoute())

            get("/upload_history") {
                if (call.request.header("origin").isNotNull()){
                    return@get call.respondText("此接口禁止跨域", status = HttpStatusCode.Forbidden)
                }
                call.respond(uploadLogs.toJsonString())
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
                call.respondText(JsonObject().apply {
                    addProperty("name", shareInfo.fileName)
                    addProperty("size", shareInfo.fileSize)
                }.toString())
            }
        }
    }
}

