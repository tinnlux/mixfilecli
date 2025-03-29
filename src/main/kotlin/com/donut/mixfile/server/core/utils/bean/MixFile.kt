package com.donut.mixfile.server.core.utils.bean


import com.alibaba.fastjson2.annotation.JSONField
import com.alibaba.fastjson2.to
import com.alibaba.fastjson2.toJSONString
import com.donut.mixfile.server.core.Uploader
import com.donut.mixfile.server.core.aes.decryptAES
import com.donut.mixfile.server.core.aes.encryptAES
import com.donut.mixfile.server.core.utils.basen.Alphabet
import com.donut.mixfile.server.core.utils.basen.BigIntBaseN
import com.donut.mixfile.server.core.utils.compressGzip
import com.donut.mixfile.server.core.utils.decompressGzip
import com.donut.mixfile.server.core.utils.hashMD5
import com.donut.mixfile.server.core.utils.hashSHA256
import com.donut.mixfile.server.core.utils.parseFileMimeType
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.discard


fun ByteArray.hashMixSHA256() = MixShareInfo.ENCODER.encode(hashSHA256())

data class MixShareInfo(
    @JSONField(name = "f") var fileName: String,
    @JSONField(name = "s") val fileSize: Long,
    @JSONField(name = "h") val headSize: Int,
    @JSONField(name = "u") val url: String,
    @JSONField(name = "k") val key: String,
    @JSONField(name = "r") val referer: String,
) {

    companion object {

        val ENCODER =
            BigIntBaseN(Alphabet.fromString("0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"))

        fun fromString(string: String) = fromJson(dec(string))

        fun tryFromString(string: String) = try {
            fromString(string)
        } catch (e: Exception) {
            null
        }

        private fun fromJson(json: String): MixShareInfo =
            json.to()

        private fun enc(input: String): String {
            val bytes = input.encodeToByteArray()
            val result = encryptAES(bytes, "123".hashMD5(), iv = "123".hashMD5().copyOf(12))
            return ENCODER.encode(result)
        }

        private fun dec(input: String): String {
            val bytes = ENCODER.decode(input)
            val result = decryptAES(bytes, "123".hashMD5())
            return result!!.decodeToString()
        }

    }

    override fun toString(): String {
        return enc(toJson())
    }

    private fun toJson(): String = this.toJSONString()

    suspend fun fetchFile(
        url: String,
        client: HttpClient,
        referer: String = this.referer,
    ): ByteArray? {
        val transformedUrl = Uploader.transformUrl(url)
        val transformedReferer = Uploader.transformReferer(url, referer)
        val result: ByteArray? = client.prepareGet(transformedUrl) {
            if (transformedReferer.trim().isNotEmpty()) {
                header("Referer", transformedReferer)
            }
        }.execute {
            val channel = it.bodyAsChannel()
            channel.discard(headSize.toLong())
            decryptAES(channel, ENCODER.decode(key))
        }
        if (result != null) {
            val hash = url.split("#").getOrNull(1)
            if (hash != null) {
                val currentHash = result.hashMixSHA256()
                if (!currentHash.contentEquals(hash)) {
                    throw Exception("文件遭到篡改")
                }
            }
        }
        return result
    }

    override fun hashCode(): Int {
        return url.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other is MixShareInfo) {
            return url == other.url
        }
        return false
    }

    fun contentType(): String = fileName.parseFileMimeType()

    suspend fun fetchMixFile(client: HttpClient): MixFile? {
        val decryptedBytes = fetchFile(url, client = client) ?: return null
        return MixFile.fromBytes(decryptedBytes)
    }

}


data class MixFile(
    @JSONField(name = "chunk_size") val chunkSize: Long,
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