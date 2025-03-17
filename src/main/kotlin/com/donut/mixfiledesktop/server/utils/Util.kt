package com.donut.mixfiledesktop.server.utils

import com.donut.mixfiledesktop.util.genRandomString
import com.donut.mixfiledesktop.util.generateRandomByteArray
import com.donut.mixfiledesktop.util.ignoreError
import io.ktor.http.*
import io.ktor.server.routing.*
import kotlinx.coroutines.coroutineScope
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
    route: RoutingHandler,
): RoutingHandler {
    val tasks = CopyOnWriteArrayList<() -> Unit>()
    return route@{
        coroutineScope {
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
            route()
            tasks.remove(cancel)
        }
    }
}

fun getRandomEncKey() = generateRandomByteArray(256)

