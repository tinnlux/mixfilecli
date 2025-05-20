package com.donut.mixfile.server.core.routes.api.webdav.objects

import com.alibaba.fastjson2.into
import com.alibaba.fastjson2.toJSONString
import com.donut.mixfile.server.core.utils.compressGzip
import com.donut.mixfile.server.core.utils.decompressGzip
import com.donut.mixfile.server.core.utils.resolveMixShareInfo
import java.util.concurrent.ConcurrentHashMap


open class WebDavManager {

    var WEBDAV_DATA = ConcurrentHashMap<String, MutableSet<WebDavFile>>()
    var loaded = true

    fun dataToBytes(data: ConcurrentHashMap<String, MutableSet<WebDavFile>> = WEBDAV_DATA) =
        compressGzip(data.toJSONString())

    fun loadDataFromBytes(data: ByteArray) {
        WEBDAV_DATA = parseDataFromBytes(data)
    }

    fun parseDataFromBytes(data: ByteArray): ConcurrentHashMap<String, MutableSet<WebDavFile>> =
        decompressGzip(data).into()

    suspend fun saveData() {
        saveWebDavData(dataToBytes())
    }

    open suspend fun saveWebDavData(data: ByteArray) {}

    // 添加文件或目录到指定路径
    open fun addFileNode(path: String, file: WebDavFile) {
        val normalizedPath = normalizePath(path)
        val fileList = WEBDAV_DATA.getOrPut(normalizedPath) { HashSet() }
        synchronized(fileList) {
            fileList.remove(file)
            fileList.add(file)
            WEBDAV_DATA[normalizedPath] = fileList
            if (file.isFolder) {
                val collectionPath = normalizePath("${normalizedPath}/${file.name}")
                WEBDAV_DATA.getOrPut(collectionPath) { HashSet() }
            }
        }
    }

    open fun copyFile(
        path: String,
        dest: String,
        overwrite: Boolean,
        keep: Boolean = true
    ): Boolean {
        val srcFile = getFile(path) ?: return false
        val destFile = getFile(dest)
        if (!overwrite && destFile != null) {
            return false
        }
        getFile(dest.substringBeforeLast('/', "")) ?: return false
        if (!keep) {
            removeFileNode(path, false)
        }
        val destName = normalizePath(dest).substringAfterLast('/')
        addFileNode(
            dest.substringBeforeLast('/', ""),
            WebDavFile(
                destName,
                size = srcFile.size,
                shareInfoData = srcFile.shareInfoData.let {
                    val shareInfo = resolveMixShareInfo(srcFile.shareInfoData)
                    shareInfo?.copy(fileName = destName)?.toString() ?: it
                },
                isFolder = srcFile.isFolder,
                lastModified = srcFile.lastModified
            )
        )
        if (srcFile.isFolder) {
            val fileList = listFiles(path)
            fileList?.forEach {
                val filePath = "${normalizePath(path)}/${it.name}"
                copyFile(filePath, "${normalizePath(dest)}/${it.name}", overwrite, keep)
            }
        }
        return true
    }

    // 删除指定路径的文件或目录
    open fun removeFileNode(path: String, removeFolder: Boolean = true) {
        val normalizedPath = normalizePath(path)
        val parentPath = normalizedPath.substringBeforeLast('/', "")
        val name = normalizedPath.substringAfterLast('/')
        val fileList = WEBDAV_DATA[parentPath] ?: return
        synchronized(fileList) {
            val node = fileList.firstOrNull { it.name.contentEquals(name) }
            if (node != null) {
                node.lastModified = System.currentTimeMillis()
                fileList.remove(node)
                // 如果是目录，递归删除其内容
                if (node.isFolder && removeFolder) {
                    val childPath = if (parentPath.isEmpty()) name else "$parentPath/$name"
                    removeCollectionContents(childPath)
                }
                WEBDAV_DATA[parentPath] = fileList
            }
        }
    }

    open fun getFile(path: String): WebDavFile? {
        val normalizedPath = normalizePath(path)
        val parentPath = normalizedPath.substringBeforeLast('/', "")
        val name = normalizedPath.substringAfterLast('/')
        return getFile(parentPath, name)
    }

    open fun getFile(path: String, name: String): WebDavFile? {
        val normalizedPath = normalizePath(path)
        val files = WEBDAV_DATA.getOrPut(normalizedPath) { HashSet() }
        if (path.isEmpty() && name.isEmpty()) {
            return WebDavFile(name = "root", isFolder = true)
        }
        synchronized(files) {
            return files.firstOrNull { it.name.contentEquals(name) }
        }
    }

    // 列出指定路径下的文件和目录
    open fun listFiles(path: String): List<WebDavFile>? {
        val normalizedPath = normalizePath(path)
        val result = WEBDAV_DATA[normalizedPath]
        if (result == null && path.isEmpty()) {
            return emptyList()
        }
        return result?.toList()
    }

    open fun listFilesRecursive(path: String): ConcurrentHashMap<String, MutableSet<WebDavFile>> {
        val normalizedPath = normalizePath(path)
        val result = ConcurrentHashMap<String, MutableSet<WebDavFile>>()
        fun addFolder(path: String) {
            val folderList =
                WEBDAV_DATA.getOrDefault(normalizePath("$normalizedPath/$path"), mutableSetOf())
            result[path] = folderList
            folderList.forEach {
                if (it.isFolder) {
                    addFolder(normalizePath("$path/${it.name}"))
                }
            }
        }
        addFolder("")
        return result
    }


    // 辅助方法：递归删除目录及其内容
    private fun removeCollectionContents(collectionPath: String) {
        val normalizedPath = normalizePath(collectionPath)
        val fileList = WEBDAV_DATA[normalizedPath]?.toList() ?: return
        fileList.forEach { node ->
            node.lastModified = System.currentTimeMillis()
            val childPath = normalizePath("$normalizedPath/${node.name}")
            if (node.isFolder && node.name.isNotEmpty()) {
                removeCollectionContents(childPath) // 递归删除子目录
            }
            removeFileNode(childPath) // 删除子文件或目录
        }
        WEBDAV_DATA.remove(normalizedPath) // 删除空目录
    }
}

fun String?.normalPath() = normalizePath(this ?: "")


fun normalizePath(path: String): String {
    if (path.isBlank()) return ""
    return path.trim('/').replace(Regex("/+"), "/")
}


