package com.donut.mixfiledesktop.util

import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.utils.io.*
import java.awt.Desktop
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.*
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.log10
import kotlin.math.pow
import kotlin.random.Random

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"

    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt()

    return String.format(
        Locale.US,
        "%.1f %s",
        bytes / 1024.0.pow(digitGroups.toDouble()),
        units[digitGroups]
    )
}


tailrec fun String.hashToMD5String(round: Int = 1): String {
    val digest = hashMD5()
    if (round > 1) {
        return digest.toHex().hashToMD5String(round - 1)
    }
    return digest.toHex()
}

fun ByteArray.toHex(): String {
    val sb = StringBuilder()
    for (b in this) {
        sb.append(String.format("%02x", b))
    }
    return sb.toString()
}

fun String.hashMD5() = hashToHexString("MD5")

fun String.hashSHA256() = hashToHexString("SHA-256")

fun String.hashToHexString(algorithm: String): ByteArray {
    val md = MessageDigest.getInstance(algorithm)
    md.update(this.toByteArray())
    return md.digest()
}

fun ByteArray.hashToHexString(algorithm: String): String {
    return calcHash(algorithm).toHex()
}

fun ByteArray.calcHash(algorithm: String): ByteArray {
    val md = MessageDigest.getInstance(algorithm)
    md.update(this)
    return md.digest()
}

fun ByteArray.hashSHA256() = calcHash("SHA-256")

fun ByteArray.hashSHA256String() = hashToHexString("SHA-256")


fun String.decodeHex(): ByteArray {
    check(length % 2 == 0) { "Must have an even length" }

    return chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}

fun String.encodeURL(): String? {
    return URLEncoder.encode(this, "UTF-8")
}

fun String.truncate(maxLength: Int): String {
    return if (this.length > maxLength) {
        this.substring(0, maxLength) + "..."
    } else {
        this
    }
}

@OptIn(ExperimentalEncodingApi::class)
fun ByteArray.encodeToBase64() = Base64.encode(this)

@OptIn(ExperimentalEncodingApi::class)
fun String.decodeBase64() = Base64.decode(this)

@OptIn(ExperimentalEncodingApi::class)
fun String.decodeBase64String() = Base64.decode(this).decodeToString()

fun String.encodeToBase64() = this.toByteArray().encodeToBase64()

fun <T> List<T>.at(index: Long): T {
    var fixedIndex = index % this.size
    if (fixedIndex < 0) {
        fixedIndex += this.size
    }
    return this[fixedIndex.toInt()]
}

fun <T> List<T>.at(index: Int): T {
    return this.at(index.toLong())
}

inline fun <T> ignoreError(block: () -> T): T? {
    try {
        return block()
    } catch (_: Exception) {

    }
    return null
}

infix fun <T> List<T>.elementEquals(other: List<T>): Boolean {
    if (this.size != other.size) return false

    val tracker = BooleanArray(this.size)
    var counter = 0

    root@ for (value in this) {
        destination@ for ((i, o) in other.withIndex()) {
            if (tracker[i]) {
                continue@destination
            } else if (value?.equals(o) == true) {
                counter++
                tracker[i] = true
                continue@root
            }
        }
    }

    return counter == this.size
}


fun openInBrowser(uri: URI) {
    val osName by lazy(LazyThreadSafetyMode.NONE) {
        System.getProperty("os.name").lowercase(Locale.getDefault())
    }
    val desktop = Desktop.getDesktop()
    when {
        Desktop.isDesktopSupported() && desktop.isSupported(Desktop.Action.BROWSE) -> desktop.browse(
            uri
        )

        "mac" in osName -> Runtime.getRuntime().exec("open $uri")
        "nix" in osName || "nux" in osName -> Runtime.getRuntime().exec("xdg-open $uri")
    }
}

fun genRandomString(
    length: Int = 32,
    chars: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9'),
): String {
    return (1..length)
        .map { Random.nextInt(0, chars.size) }
        .map(chars::get)
        .joinToString("")
}

fun genRandomHexString(length: Int = 32) = genRandomString(length, ('0'..'9') + ('a'..'f'))


fun showError(e: Throwable) {
    e.printStackTrace()
}

@OptIn(InternalAPI::class)
fun FormBuilder.add(key: String, value: Any?, headers: Headers = Headers.Empty) {
    append(key.quote(), value ?: "", headers)
}

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