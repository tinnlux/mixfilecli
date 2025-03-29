package com.donut.mixfile.server.core

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.content.OutgoingContent
import io.ktor.http.userAgent
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.jvm.javaio.toOutputStream
import okhttp3.Dispatcher
import java.io.InputStream


val MixFileServer.httpClient
    get() = HttpClient(OkHttp) {
        engine {
            config {
                dispatcher(Dispatcher().apply {
                    maxRequestsPerHost = Int.MAX_VALUE
                    maxRequests = Int.MAX_VALUE
                })
            }
        }
        install(ContentNegotiation) {

        }
        install(HttpRequestRetry) {
            maxRetries = uploadTaskCount
            retryOnException(retryOnTimeout = true)
            retryOnServerErrors()
            delayMillis { retry ->
                retry * 100L
            }
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 1000 * 120
        }
        install(DefaultRequest) {
            userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36")
        }
    }

val localClient = HttpClient(OkHttp).config {
    install(HttpTimeout) {
        requestTimeoutMillis = 1000 * 60 * 60 * 24 * 30L
        socketTimeoutMillis = 1000 * 60 * 60
        connectTimeoutMillis = 1000 * 60 * 60
    }
}

class StreamContent(private val stream: InputStream, val length: Long = 0) :
    OutgoingContent.WriteChannelContent() {
    override suspend fun writeTo(channel: ByteWriteChannel) {
        stream.copyTo(channel.toOutputStream())
    }

    override val contentLength: Long
        get() = length

}