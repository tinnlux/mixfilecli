package com.donut.mixfile.server.core


import com.donut.mixfile.server.core.routes.getRoutes
import com.donut.mixfile.server.core.utils.MixUploadTask
import com.donut.mixfile.server.core.utils.bean.MixShareInfo
import com.donut.mixfile.server.core.utils.genRandomString
import com.donut.mixfile.server.core.utils.ignoreError
import com.donut.mixfile.server.core.utils.registerJson
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondText
import io.ktor.server.routing.routing
import java.io.InputStream
import java.net.ServerSocket


abstract class MixFileServer(
    var serverPort: Int = 4719,
) {

    init {
        registerJson()
    }

    abstract val downloadTaskCount: Int
    abstract val uploadTaskCount: Int
    abstract val requestRetryCount: Int
    open val enableAccessKey: Boolean = false
    open val accessKey: String = genRandomString(32)
    open val accessKeyTip: String = "Require Access Key"

    abstract fun onError(error: Throwable)

    abstract fun getUploader(): Uploader

    abstract fun getStaticFile(path: String): InputStream?

    abstract fun genDefaultImage(): ByteArray

    abstract fun getFileHistory(): String

    open fun getUploadTask(
        call: ApplicationCall,
        name: String,
        size: Long,
        add: Boolean
    ): MixUploadTask = object : MixUploadTask {
        override var error: Throwable? = null

        override var stopped: Boolean = false

        override suspend fun complete(shareInfo: MixShareInfo) {
        }

        override var onStop: () -> Unit = {}
        override suspend fun updateProgress(size: Long, total: Long) {
        }

    }

    open fun onDownloadData(data: ByteArray) {

    }

    open fun onUploadData(data: ByteArray) {

    }


    fun start(wait: Boolean) {
        serverPort = findAvailablePort(serverPort) ?: serverPort
        embeddedServer(Netty, port = serverPort, watchPaths = emptyList()) {
            intercept(ApplicationCallPipeline.Call) {
                val key = call.request.queryParameters["accessKey"]
                if (enableAccessKey && !key.contentEquals(accessKey)) {
                    call.respondText(accessKeyTip)
                    finish()
                }
            }
            install(ContentNegotiation) {

            }
            install(CORS) {
                allowOrigins { true }
                anyHost()
                allowMethod(HttpMethod.Options)
                allowMethod(HttpMethod.Post)
                allowMethod(HttpMethod.Get)
                allowMethod(HttpMethod.Put)
                allowHeader(HttpHeaders.AccessControlAllowOrigin)
                allowHeader(HttpHeaders.AccessControlAllowMethods)
                allowHeader(HttpHeaders.ContentType)
            }
            install(StatusPages) {
                exception<Throwable> { call, cause ->
                    call.respondText(
                        "发生错误: ${cause.message} ${cause.stackTraceToString()}",
                        status = HttpStatusCode.InternalServerError
                    )
                    onError(cause)
                }
            }
            routing(getRoutes())
        }.start(wait = wait)
    }
}

fun findAvailablePort(startPort: Int = 9527, endPort: Int = 65535): Int? {
    for (port in startPort..endPort) {
        ignoreError {
            // 尝试绑定到指定端口
            ServerSocket(port).use { serverSocket ->
                // 成功绑定，返回该端口
                return serverSocket.localPort
            }
        }
    }
    return null
}