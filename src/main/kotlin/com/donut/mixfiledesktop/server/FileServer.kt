package com.donut.mixfiledesktop.server

import com.donut.mixfilecli.config
import com.donut.mixfiledesktop.server.routes.getRoutes
import com.donut.mixfiledesktop.util.ignoreError
import com.donut.mixfiledesktop.util.isFalse
import com.donut.mixfiledesktop.util.showError
import io.ktor.http.*
import io.ktor.serialization.gson.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.IOException
import java.io.OutputStream
import java.io.PrintStream
import java.net.NetworkInterface
import java.net.ServerSocket

var serverPort = config.port

fun startServer() {
    serverPort = findAvailablePort(serverPort) ?: serverPort
    embeddedServer(Netty, port = serverPort, watchPaths = emptyList(), host = config.host) {
        routing(getRoutes())
        install(ContentNegotiation) {
            gson()
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
                if (cause is IOException) {
                    return@exception
                }
                when (cause.message) {
                    "服务器达到并发限制" -> Unit
                    else -> showError(cause)
                }
            }
        }
        println("MixFile已在 ${config.host}:${serverPort} 启动")
        System.setOut(PrintStream(OutputStream.nullOutputStream()))
    }.start(wait = true)
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

fun getIpAddressInLocalNetwork(): String {
    val networkInterfaces = NetworkInterface.getNetworkInterfaces().iterator().asSequence()
    val localAddresses = networkInterfaces.flatMap {
        it.inetAddresses.asSequence()
            .filter { inetAddress ->
                inetAddress.isSiteLocalAddress && inetAddress?.hostAddress?.contains(":")
                    .isFalse() &&
                        inetAddress.hostAddress != "127.0.0.1"
            }
            .map { inetAddress -> inetAddress.hostAddress }
    }
    return localAddresses.firstOrNull() ?: "127.0.0.1"
}