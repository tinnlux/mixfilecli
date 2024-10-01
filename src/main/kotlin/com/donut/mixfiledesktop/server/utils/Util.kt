package com.donut.mixfiledesktop.server.utils

import com.donut.mixfiledesktop.util.genRandomString
import com.donut.mixfiledesktop.util.generateRandomByteArray
import com.donut.mixfiledesktop.util.ignoreError
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.util.pipeline.PipelineContext
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList


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

fun getRandomEncKey() = generateRandomByteArray(256)

