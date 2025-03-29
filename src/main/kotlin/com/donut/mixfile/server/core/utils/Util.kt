package com.donut.mixfile.server.core.utils


import com.donut.mixfile.server.core.aes.generateRandomByteArray
import com.github.amr.mimetypes.MimeTypes
import io.ktor.client.request.forms.FormBuilder
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.quote
import io.ktor.server.application.ApplicationCall
import io.ktor.util.pipeline.PipelineContext
import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

fun String.getFileExtension(): String {
    val index = this.lastIndexOf('.')
    return if (index == -1) "" else this.substring(index + 1).lowercase()
}

fun genRandomString(
    length: Int = 32,
    charPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
): String {
    return (1..length)
        .map { kotlin.random.Random.nextInt(0, charPool.size) }
        .map(charPool::get)
        .joinToString("")
}


fun fileFormHeaders(
    suffix: String = ".gif",
    mimeType: String = "image/gif",
): Headers {
    return Headers.build {
        append(HttpHeaders.ContentType, mimeType)
        append(
            HttpHeaders.ContentDisposition,
            "filename=\"${genRandomString(5)}${suffix}\""
        )
    }
}


fun concurrencyLimit(
    limit: Int,
    route: suspend PipelineContext<Unit, ApplicationCall>.(Unit) -> Unit,
): suspend PipelineContext<Unit, ApplicationCall>.(Unit) -> Unit {
    val tasks = CopyOnWriteArrayList<() -> Unit>()
    return route@{
        while (tasks.size > limit) {
            val remove = tasks.removeAt(0)
            ignoreError {
                remove()
            }
        }
        val cancel: () -> Unit = {
            launch {
                throw Throwable("服务器达到并发限制")
            }
        }
        tasks.add(cancel)
        route(Unit)
        tasks.remove(cancel)
    }
}

inline fun <T> ignoreError(block: () -> T): T? {
    try {
        return block()
    } catch (_: Exception) {

    }
    return null
}


fun getRandomEncKey() = generateRandomByteArray(256)

fun compressGzip(input: String): ByteArray {
    val byteArrayOutputStream = ByteArrayOutputStream()
    GZIPOutputStream(byteArrayOutputStream).use { gzip ->
        gzip.write(input.toByteArray())
    }
    return byteArrayOutputStream.toByteArray()
}

fun decompressGzip(compressed: ByteArray): String {
    val byteArrayInputStream = ByteArrayInputStream(compressed)
    GZIPInputStream(byteArrayInputStream).use { gzip ->
        return gzip.bufferedReader().use { it.readText() }
    }
}

fun String.encodeURL(): String? {
    return URLEncoder.encode(this, "UTF-8")
}

fun String.parseFileMimeType() = MimeTypes.getInstance()
    .getByExtension(this.getFileExtension())?.mimeType ?: "application/octet-stream"

@OptIn(InternalAPI::class)
fun FormBuilder.add(key: String, value: Any?, headers: Headers = Headers.Empty) {
    append(key.quote(), value ?: "", headers)
}