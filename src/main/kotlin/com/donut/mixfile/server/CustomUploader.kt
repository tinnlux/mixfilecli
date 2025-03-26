package com.donut.mixfile.server

import com.donut.mixfile.server.core.Uploader
import com.donut.mixfilecli.config
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

var CUSTOM_UPLOAD_URL = config.customUrl

var CUSTOM_REFERER = config.customReferer

object CustomUploader : Uploader("自定义") {

    override suspend fun genHead(client: HttpClient): ByteArray {
        return client.get {
            url(CUSTOM_UPLOAD_URL)
        }.also {
            val referer = it.headers["referer"]
            if (!referer.isNullOrEmpty()) {
                withContext(Dispatchers.Default) {
                    CUSTOM_REFERER = referer
                }
            }
        }.readRawBytes()
    }

    override val referer: String
        get() = CUSTOM_REFERER

    override suspend fun doUpload(fileData: ByteArray, client: HttpClient): String {
        val response = client.put {
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