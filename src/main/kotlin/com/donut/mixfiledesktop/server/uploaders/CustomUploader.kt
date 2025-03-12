package com.donut.mixfiledesktop.server.uploaders

import com.donut.mixfilecli.config
import com.donut.mixfiledesktop.server.Uploader
import com.donut.mixfiledesktop.server.uploadClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

var CUSTOM_UPLOAD_URL = config.customUrl

var CUSTOM_REFERER = config.customReferer

object CustomUploader : Uploader("自定义") {

    override suspend fun genHead(): ByteArray {
        return uploadClient.get {
            url(CUSTOM_UPLOAD_URL)
        }.also {
            val referer = it.headers["referer"]
            if (!referer.isNullOrEmpty()) {
                withContext(Dispatchers.Default) {
                    CUSTOM_REFERER = referer
                }
            }
        }.readBytes()
    }

    override val referer: String
        get() = CUSTOM_REFERER

    override suspend fun doUpload(fileData: ByteArray): String {
        val response = uploadClient.put {
            url(CUSTOM_UPLOAD_URL)
            setBody(fileData)
        }
        val resText = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw Exception(resText)
        }
        return resText
    }

}