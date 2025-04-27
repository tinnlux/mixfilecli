package com.donut.mixfile.server.core.objects

import com.alibaba.fastjson2.toJSONString
import com.donut.mixfile.server.core.utils.compressGzip

data class FileDataLog(
    val shareInfoData: String,
    val name: String,
    val size: Long,
    val time: Long = System.currentTimeMillis(),
    val category: String = "默认",
) {

    fun isSimilar(other: FileDataLog): Boolean {
        return other.shareInfoData.contentEquals(shareInfoData)
    }


    override fun hashCode(): Int {
        var result = shareInfoData.hashCode()
        result = 31 * result + category.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (other !is FileDataLog) return false
        return isSimilar(other) && category.contentEquals(other.category)
    }
}

fun Collection<FileDataLog>.toByteArray(): ByteArray {
    val strData = this.toJSONString()
    val compressedData = compressGzip(strData)
    return compressedData
}