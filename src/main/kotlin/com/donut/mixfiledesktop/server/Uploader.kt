package com.donut.mixfiledesktop.server

import com.donut.mixfilecli.config
import com.donut.mixfiledesktop.server.uploaders.A3Uploader
import com.donut.mixfiledesktop.server.uploaders.CustomUploader
import com.donut.mixfiledesktop.server.uploaders.hidden.A1Uploader
import com.donut.mixfiledesktop.server.uploaders.hidden.A2Uploader
import com.donut.mixfiledesktop.util.encryptAES
import io.ktor.util.*

val UPLOADERS = listOf(A1Uploader, A2Uploader, A3Uploader, CustomUploader)


fun getCurrentUploader() = UPLOADERS.firstOrNull { it.name.contentEquals(config.uploader)} ?: A1Uploader

abstract class Uploader(val name: String) {

    open val referer = ""
    open val chunkSize = 1024L * 1024L

    abstract suspend fun doUpload(fileData: ByteArray): String

    companion object {
        val urlTransforms = mutableMapOf<String, (String) -> String>()
        val refererTransforms = mutableMapOf<String, (url: String, referer: String) -> String>()

        fun transformUrl(url: String): String {
            return urlTransforms.entries.fold(url) { acc, (name, transform) ->
                transform(acc)
            }
        }

        fun transformReferer(url: String, referer: String): String {
            return refererTransforms.entries.fold(referer) { acc, (name, transform) ->
                transform(url, acc)
            }
        }

        fun registerUrlTransform(name: String, transform: (String) -> String) {
            urlTransforms[name] = transform
        }

        fun registerRefererTransform(
            name: String,
            transform: (url: String, referer: String) -> String,
        ) {
            refererTransforms[name] = transform
        }
    }

    suspend fun upload(head: ByteArray, fileData: ByteArray, key: ByteArray): String {
        val encryptedData = encryptBytes(head, fileData, key)
        try {
            return doUpload(encryptedData)
        } finally {

        }
    }

    open suspend fun genHead() =
        "R0lGODlhAQABAIABAP///wAAACH5BAEKAAEALAAAAAABAAEAAAICTAEAOw==".decodeBase64Bytes()

    private fun encryptBytes(head: ByteArray, fileData: ByteArray, key: ByteArray): ByteArray {
        return head + (encryptAES(fileData, key))
    }

}