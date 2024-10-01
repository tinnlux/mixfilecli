package com.donut.mixfiledesktop.server.routes


import com.donut.mixfiledesktop.server.utils.concurrencyLimit
import com.donut.mixfiledesktop.util.file.resolveMixShareInfo
import com.donut.mixfiledesktop.util.file.uploadLogs
import com.donut.mixfiledesktop.util.toJsonString
import com.google.gson.JsonObject
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.http.content.staticResources
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun getRoutes(): Routing.() -> Unit {

    return {
        staticResources("/", "files", "index.html")
        route("/api") {
            get("/download", concurrencyLimit(DOWNLOAD_TASK_COUNT, getDownloadRoute()))
            put("/upload", getUploadRoute())
            get("/upload_history") {
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

