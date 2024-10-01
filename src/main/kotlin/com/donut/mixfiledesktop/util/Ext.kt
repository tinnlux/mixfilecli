package com.donut.mixfiledesktop.util

import com.google.gson.Gson
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.Url
import io.ktor.http.content.OutgoingContent
import io.ktor.http.defaultForFile
import io.ktor.util.cio.readChannel
import io.ktor.utils.io.ByteReadChannel
import java.io.File
import java.net.URLEncoder

typealias UnitBlock = () -> Unit


inline fun <T> T?.isNull(block: UnitBlock = {}): Boolean {
    if (this == null) {
        block()
    }
    return this == null
}

inline fun <T> T?.isNotNull(block: (T) -> Unit = {}): Boolean {
    if (this != null) {
        block(this)
    }
    return this != null
}

inline fun <T> T?.isNotNullAnd(condition: Boolean, block: (T) -> Unit = {}): Boolean {
    if (this != null && condition) {
        block(this)
    }
    return this != null && condition
}

inline fun <T> T?.isNullAnd(condition: Boolean, block: UnitBlock = {}): Boolean {
    if (this == null && condition) {
        block()
    }
    return this == null && condition
}

inline fun <T> T?.isNullOr(condition: Boolean, block: UnitBlock = {}): Boolean {
    if (this == null || condition) {
        block()
    }
    return this == null || condition
}

inline fun <T> T.isEqual(other: Any?, block: (T) -> Unit = {}): Boolean {
    if (this == other) {
        block(this)
    }
    return this == other
}

inline fun Boolean?.isTrue(block: UnitBlock = {}): Boolean {
    if (this == true) {
        block()
    }
    return this == true
}

inline fun Boolean?.isNotTrue(block: UnitBlock = {}): Boolean {
    if (this != true) {
        block()
    }
    return this != true
}

inline fun Boolean?.isNotFalse(block: UnitBlock = {}): Boolean {
    if (this != false) {
        block()
    }
    return this != false
}

fun Boolean?.toInt(): Int {
    isTrue {
        return 1
    }
    return 0
}

fun Int.negative(): Int {
    return -this
}

fun Long.negativeIf(condition: Boolean): Long {
    if (condition) {
        return -this
    }
    return this
}

fun Int.negativeIf(condition: Boolean): Int {
    if (condition) {
        return -this
    }
    return this
}

tailrec fun Int.pow(exp: Int, acc: Int = 1): Int =
    if (exp == 0) acc else this.pow(exp - 1, acc * this)

tailrec fun Long.pow(exp: Int, acc: Long = 1): Long =
    if (exp == 0) acc else this.pow(exp - 1, acc * this)

inline fun Boolean?.isTrueAnd(condition: Boolean, block: UnitBlock = {}): Boolean {
    if (isTrue() && condition) {
        block()
    }
    return isTrue() && condition
}

inline fun Boolean?.isFalse(block: UnitBlock = {}): Boolean {
    if (this == false) {
        block()
    }
    return this == false
}

inline fun Boolean?.isFalseAnd(condition: Boolean, block: UnitBlock): Boolean {
    if (isFalse() && condition) {
        block()
    }
    return isFalse() && condition
}

fun Url.noParamUrl() = "https://${host}${this.encodedPath}"


class StreamContent(private val file: File) : OutgoingContent.ReadChannelContent() {
    override fun readFrom(): ByteReadChannel = file.readChannel()
    override val contentType: ContentType = file.contentType()
    override val contentLength: Long = file.length()
    override val headers: Headers
        get() = Headers.build {
            append(
                "content-disposition",
                """inline;filename="${
                    URLEncoder.encode(
                        file.name,
                        Charsets.UTF_8
                    )
                }" """.trim()
            )
        }
}

fun <T> T.toJsonString(): String = Gson().toJson(this)

fun File.asBody() = StreamContent(this)

fun File.contentType() = ContentType.defaultForFile(this)