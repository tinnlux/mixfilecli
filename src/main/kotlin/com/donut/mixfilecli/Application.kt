package com.donut.mixfilecli

import com.alibaba.fastjson2.toJSONString
import com.donut.mixfile.server.CustomUploader
import com.donut.mixfile.server.core.MixFileServer
import com.donut.mixfile.server.core.Uploader
import com.donut.mixfile.server.core.uploaders.A3Uploader
import com.donut.mixfile.server.core.uploaders.hidden.A1Uploader
import com.donut.mixfile.server.core.uploaders.hidden.A2Uploader
import com.donut.mixfile.server.core.utils.MixUploadTask
import com.donut.mixfile.server.core.utils.bean.MixShareInfo
import com.donut.mixfile.server.core.utils.registerJson
import com.donut.mixfile.util.file.toDataLog
import com.donut.mixfile.util.file.uploadLogs
import com.donut.mixfile.util.showError
import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.ExperimentalHoplite
import com.sksamuel.hoplite.addFileSource
import io.ktor.server.application.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.*
import java.util.*
import javax.imageio.ImageIO
import javax.imageio.stream.ImageOutputStream
import javax.imageio.stream.MemoryCacheImageOutputStream

data class Config(
    val uploader: String = "A1",
    val uploadTask: Int = 10,
    val downloadTask: Int = 5,
    val port: Int = 4719,
    val uploadRetry: Int = 10,
    val customUrl: String = "",
    val customReferer: String = "",
    val host: String = "0.0.0.0",
)

var config: Config = Config()

fun createRandomGifByteArray(): ByteArray {
    val random = Random()

    // 随机生成GIF的宽度和高度（50-200像素之间）
    val width = random.nextInt(101) + 50
    val height = random.nextInt(101) + 50

    val byteArrayOutputStream = ByteArrayOutputStream()
    val outputStream: ImageOutputStream = MemoryCacheImageOutputStream(byteArrayOutputStream)

    // 创建GIF写入器
    val writer = ImageIO.getImageWritersByFormatName("gif").next()
    writer.output = outputStream

    // 开始写入GIF
    writer.prepareWriteSequence(null)
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()

    // 随机生成颜色
    val randomColor = Color(
        random.nextInt(256),  // R
        random.nextInt(256),  // G
        random.nextInt(256)   // B
    )

    // 填充背景色
    graphics.color = randomColor
    graphics.fillRect(0, 0, width, height)

    // 清理graphics
    graphics.dispose()
    ImageIO.write(image, "gif", outputStream)

    // 结束写入
    writer.endWriteSequence()
    outputStream.close()

    return byteArrayOutputStream.toByteArray()
}

@OptIn(ExperimentalHoplite::class, ExperimentalCoroutinesApi::class)
fun main(args: Array<String>) {
    checkConfig()
    registerJson()
    config = ConfigLoaderBuilder.default()
        .addFileSource("config.yml")
        .withExplicitSealedTypes()
        .build()
        .loadConfigOrThrow<Config>()
    println(config)
    val UPLOADERS = listOf(A1Uploader, A2Uploader, A3Uploader, CustomUploader)

    fun getCurrentUploader() = UPLOADERS.firstOrNull { it.name.contentEquals(config.uploader) } ?: A1Uploader

    val server = object : MixFileServer(
        serverPort = config.port,
    ) {
        override val downloadTaskCount: Int
            get() = config.downloadTask
        override val uploadTaskCount: Int
            get() = config.uploadTask
        override val requestRetryCount
            get() = config.uploadRetry

        override fun onError(error: Throwable) {
            showError(error)
        }

        override fun getUploader(): Uploader {
            return getCurrentUploader()
        }

        override fun getStaticFile(path: String): InputStream? {
            val classLoader = object {}.javaClass.classLoader
            // 加载资源文件，路径相对于 resources 目录
            return classLoader?.getResourceAsStream("files/${path}")
        }

        override fun genDefaultImage(): ByteArray {
            return createRandomGifByteArray()
        }

        override fun getFileHistory(): String {
            return uploadLogs.toJSONString()
        }

        override fun getUploadTask(call: ApplicationCall, name: String, size: Long, add: Boolean): MixUploadTask {
            return object : MixUploadTask {
                override var error: Throwable? = null
                override var stopped: Boolean = false

                override suspend fun complete(shareInfo: MixShareInfo) {
                    if (add) {
                        uploadLogs += shareInfo.toDataLog()
                    }
                }

                override var onStop: () -> Unit = {}

                override suspend fun updateProgress(size: Long, total: Long) {

                }
            }
        }
    }
    println("MixFile已在 ${config.host}:${server.serverPort} 启动")
    System.setOut(PrintStream(OutputStream.nullOutputStream()))
    server.start(true)
}


fun checkConfig() {
    val currentDir = System.getProperty("user.dir")
    val inputStream: InputStream? = object {}.javaClass.getResourceAsStream("/config.yml")
    val outputFile = File(currentDir, "config.yml")
    if (!outputFile.exists()) {
        FileOutputStream(outputFile).use { outputStream ->
            inputStream?.copyTo(outputStream)
        }
    }
}