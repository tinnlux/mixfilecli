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
        val result = uploadClient.submitFormWithBinaryData("https://pic.2xb.cn/uppic.php?type=qq",
            formData {
                add("file", fileData, fileFormHeaders())
            }) {
        }.body<JsonObject>()
        val code = result.get("code").asInt
        if (code != 200) {
            throw Exception("上传失败: $code")
        }

        return result.get("url").asString
    }
}
