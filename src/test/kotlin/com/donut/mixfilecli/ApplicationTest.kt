package com.donut.mixfilecli


import com.donut.mixfile.server.core.routes.api.webdav.utils.normalizePath
import kotlin.test.Test

class ApplicationTest {


    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun testRoot() {
        println(normalizePath("/api/webdav?accessKey=147"))
    }


}
