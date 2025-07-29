package com.donut.mixfilecli

import com.charleskorn.kaml.Yaml
import com.donut.mixfile.server.core.MixFileServer
import com.donut.mixfile.server.core.Uploader
import com.donut.mixfile.server.core.objects.MixShareInfo
import com.donut.mixfile.server.core.routes.api.webdav.objects.WebDavManager
import com.donut.mixfile.server.core.uploaders.A1Uploader
import com.donut.mixfile.server.core.uploaders.A2Uploader
import com.donut.mixfile.server.core.uploaders.A3Uploader
import com.donut.mixfile.server.core.uploaders.base.js.JSUploader
import com.donut.mixfile.server.core.utils.*
import com.donut.mixfile.server.core.utils.extensions.kb
import com.donut.mixfilecli.utils.CustomUploader
import com.donut.mixfilecli.utils.addUploadLog
import com.donut.mixfilecli.utils.formatFileSize
import com.donut.mixfilecli.utils.uploadLogs
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import org.slf4j.LoggerFactory
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.*
import java.nio.charset.StandardCharsets
import java.util.*
import javax.imageio.ImageIO
import javax.imageio.stream.ImageOutputStream
import javax.imageio.stream.MemoryCacheImageOutputStream

val defaultUploader = A2Uploader

@Serializable
data class Config(
    val uploader: String = defaultUploader.name,
    @SerialName("upload_task")
    val uploadTask: Int = 10,
    @SerialName("download_task")
    val downloadTask: Int = 5,
    @SerialName("upload_retry")
    val uploadRetry: Int = 10,
    @SerialName("chunk_size")
    val chunkSize: Int = 1024,
    val port: Int = 4719,
    @SerialName("custom_url")
    val customUrl: String = "",
    @SerialName("custom_referer")
    val customReferer: String = "",
    val host: String = "0.0.0.0",
    val password: String = "",
    @SerialName("webdav_path")
    val webdavPath: String = "data.mix_dav",
    val history: String = "history.mix_list"
)

var config: Config = Config()

fun createRandomGifByteArray(): ByteArray {
    val random = Random()

    // 随机生成GIF的宽度和高度（50-150像素之间）
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

val appScope = CoroutineScope(Dispatchers.Default + Job())

val UPLOADERS = listOf(A1Uploader, A2Uploader, A3Uploader, CustomUploader)


fun main(args: Array<String>) {
    checkConfig()
    // 替换 System.out，使其用 UTF-8 编码
    System.setOut(PrintStream(System.out, true, StandardCharsets.UTF_8.name()))
    config = Yaml.default.decodeFromString(File("config.yml").readText(Charsets.UTF_8))
    println("========== 已加载配置 ==========")
    println(Yaml.default.encodeToString(Config.serializer(), config))
    println("===================================")
    var currentUploader = UPLOADERS.firstOrNull { it.name.contentEquals(config.uploader) } ?: defaultUploader

    if (config.uploader.lowercase().endsWith(".js")) {
        val jsFile = File(config.uploader)
        if (jsFile.exists()) {
            val code = jsFile.readText(Charsets.UTF_8)
            currentUploader = object : JSUploader("JSUploader") {
                override val scriptCode: String
                    get() = code
            }
        }
    }


    val saveMutex = Mutex()

    suspend fun saveFileData(path: String, data: ByteArray) {
        saveMutex.withLock {
            val file = File(path)
            file.parentFile?.mkdirs()
            file.writeBytes(data)
        }
    }

    val webDavManager = object : WebDavManager() {
        override suspend fun saveWebDavData(data: ByteArray) {
            saveFileData(config.webdavPath, data)
        }
    }


    val webDavFile = File(config.webdavPath)
    val historyFile = File(config.history)

    if (webDavFile.exists()) {
        try {
            webDavManager.loadDataFromBytes(webDavFile.readBytes())
        } catch (e: Exception) {
            e.printStackTrace()
            throw Error("载入WebDAV存档失败: " + e.message)
        }
    }

    if (historyFile.exists()) {
        try {
            uploadLogs = decompressGzip(historyFile.readBytes()).parseJsonObject()
        } catch (e: Exception) {
            e.printStackTrace()
            throw Error("载入上传历史文件失败: " + e.message)
        }
    }

    val logger = LoggerFactory.getLogger("io.ktor.server.Application")

    val server = object : MixFileServer(
        serverPort = config.port,
        host = config.host
    ) {
        override val downloadTaskCount: Int
            get() = config.downloadTask

        override val uploadTaskCount: Int
            get() = config.uploadTask

        override val uploadRetryCount
            get() = config.uploadRetry

        override val password: String
            get() = config.password

        override val chunkSize: Int
            get() = config.chunkSize * 1.kb

        override val webDav: WebDavManager
            get() = webDavManager

        override fun onError(error: Throwable) {
            when (error) {
                is ClosedWriteChannelException -> return
                is ClosedByteChannelException -> return
                is ChannelWriteException -> return
            }

            logger.error(error.stackTraceToString())
        }

        override fun getUploader(): Uploader {
            return currentUploader
        }


        override suspend fun genDefaultImage(): ByteArray {
            return createRandomGifByteArray()
        }

        override suspend fun getFileHistory(): String {
            return uploadLogs.asReversed().toJsonString()
        }


        override fun getUploadTask(name: String, size: Long, add: Boolean): MixUploadTask {
            return object : MixUploadTask {
                override var error: Throwable? = null
                override var stopped: Boolean = false

                override suspend fun complete(shareInfo: MixShareInfo) {
                    logger.info("文件上传成功: ${shareInfo.fileName} ${formatFileSize(shareInfo.fileSize)}")
                    if (add) {
                        addUploadLog(shareInfo)
                        saveFileData(config.history, compressGzip(uploadLogs.toJsonString()))
                    }
                }

                override val stopFunc: MutableList<suspend () -> Unit> = mutableListOf()


                override suspend fun updateProgress(size: Long, total: Long) {

                }
            }
        }
    }
    val startPort = findAvailablePort(server.serverPort) ?: server.serverPort
    logger.info("MixFile已在 ${config.host}:${startPort} 启动  线路: ${server.getUploader().name}")
    server.start(true)
}


fun checkConfig() {
    val currentDir = System.getProperty("user.dir")
    val inputStream: InputStream? = object {}.javaClass.getResourceAsStream("/config.yml")
    val outputFile = File(currentDir, "config.yml")
    if (!outputFile.exists()) {
        FileOutputStream(outputFile).use { outputStream ->
            inputStream.use {
                it?.copyTo(outputStream)
            }
        }
    }
}