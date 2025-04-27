package com.donut.mixfile.server.core.objects


import com.alibaba.fastjson2.annotation.JSONField
import com.alibaba.fastjson2.to
import com.alibaba.fastjson2.toJSONString
import com.donut.mixfile.server.core.utils.compressGzip
import com.donut.mixfile.server.core.utils.decompressGzip
import com.donut.mixfile.server.core.utils.hashSHA256


fun ByteArray.hashMixSHA256() = MixShareInfo.ENCODER.encode(hashSHA256())


data class MixFile(
    @JSONField(name = "chunk_size") val chunkSize: Int,
    @JSONField(name = "file_size") val fileSize: Long,
    @JSONField(name = "version") val version: Long,
    @JSONField(name = "file_list") val fileList: List<String>,
) {

    companion object {
        fun fromBytes(data: ByteArray): MixFile =
            decompressGzip(data).to()
    }

    fun getFileListByStartRange(startRange: Long): List<Pair<String, Int>> {
        val start = startRange / chunkSize
        val rangeFileList = fileList.subList(start.toInt(), fileList.size)
        val startOffset = startRange % chunkSize
        val result = mutableListOf<Pair<String, Int>>()
        for (i in rangeFileList.indices) {
            val file = rangeFileList[i]
            val offset = if (i == 0) startOffset else 0
            result.add(Pair(file, offset.toInt()))
        }
        return result
    }


    fun toBytes() = compressGzip(this.toJSONString())

}