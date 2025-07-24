package com.donut.mixfilecli.utils

import com.donut.mixfile.server.core.uploaders.base.HttpUploader
import com.donut.mixfilecli.config

var CUSTOM_UPLOAD_URL = config.customUrl

@Volatile
var CUSTOM_REFERER = config.customReferer

object CustomUploader : HttpUploader("自定义") {

    override val referer: String
        get() = CUSTOM_REFERER


    override val reqUrl: String
        get() = CUSTOM_UPLOAD_URL

    override suspend fun setReferer(value: String) {
        CUSTOM_REFERER = value
    }

}