package com.donut.mixfiledesktop.server.uploaders

import com.donut.mixfiledesktop.server.Uploader
import com.donut.mixfiledesktop.server.uploadClient
import com.donut.mixfiledesktop.server.utils.fileFormHeaders
import com.donut.mixfiledesktop.util.add
import com.google.gson.JsonObject
import io.ktor.client.call.body
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData

object A3Uploader : Uploader("线路A3") {

    override val referer: String
        get() = ""

    override suspend fun doUpload(fileData: ByteArray): String {
        val result =
            uploadClient.submitFormWithBinaryData("https://chatbot.weixin.qq.com/weixinh5/webapp/pfnYYEumBeFN7Yb3TAxwrabYVOa4R9/cos/upload",
                formData {
                    add("media", fileData, fileFormHeaders())
                }) {
            }.body<JsonObject>()

        return result.get("url").asString
    }
}