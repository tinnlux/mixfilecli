package com.donut.mixfile.server.core.routes.api

import com.alibaba.fastjson2.toJSONString
import com.donut.mixfile.server.core.MixFileServer
import com.donut.mixfile.server.core.interceptCall
import com.donut.mixfile.server.core.routes.api.webdav.getWebDAVRoute
import com.donut.mixfile.server.core.utils.isNotNull
import com.donut.mixfile.server.core.utils.resolveMixShareInfo
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun MixFileServer.getAPIRoute(): Route.() -> Unit {
    return {
        route("/webdav") {
            interceptCall({
                if (!webDav.loaded) {
                    it.respond(HttpStatusCode.ServiceUnavailable, "WebDav is Loading")
                }
            }, getWebDAVRoute())
        }
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