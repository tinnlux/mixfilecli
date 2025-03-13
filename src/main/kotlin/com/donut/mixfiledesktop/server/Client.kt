package com.donut.mixfiledesktop.server

import com.donut.mixfilecli.config
import com.google.gson.GsonBuilder
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.gson.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import okhttp3.Dispatcher
import java.io.InputStream

var UPLOAD_RETRY_TIMES = config.uploadRetry

val uploadClient = HttpClient(OkHttp) {
    engine {
        config {
            val dispatcher = Dispatcher()
            dispatcher.maxRequestsPerHost = 100
            dispatcher(dispatcher)
        }
    }
    install(ContentNegotiation) {
        gson()
        register(ContentType.Any, GsonConverter(GsonBuilder().create()))
    }
    install(HttpRequestRetry) {
        maxRetries = UPLOAD_RETRY_TIMES.toInt()
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


class StreamContent(private val stream: InputStream, val length: Long = 0) :
    OutgoingContent.WriteChannelContent() {
    override suspend fun writeTo(channel: ByteWriteChannel) {
        stream.copyTo(channel.toOutputStream())
    }

    override val contentLength: Long
        get() = length

}