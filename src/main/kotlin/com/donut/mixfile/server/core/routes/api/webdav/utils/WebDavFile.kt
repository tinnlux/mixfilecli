package com.donut.mixfile.server.core.routes.api.webdav.utils

import com.alibaba.fastjson2.annotation.JSONField
import com.donut.mixfile.server.core.utils.hashSHA256
import com.donut.mixfile.server.core.utils.parseFileMimeType
import com.donut.mixfile.server.core.utils.toHex
import java.text.SimpleDateFormat
import java.util.*

// WebDAV 文件类，包含额外属性
class WebDavFile(
    val name: String,
    val size: Long = 0,
    val shareInfoData: String = "",
    val isFolder: Boolean = false,
    var lastModified: Long = System.currentTimeMillis()
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WebDavFile) return false

        return name.contentEquals(other.name)
    }

    @JSONField(serialize = false)
    fun getLastModifiedFormatted(): String {
        val sdf = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("GMT")
        return sdf.format(Date(lastModified))
    }


    override fun hashCode(): Int = name.hashCode()
    fun toXML(path: String): String {
        if (isFolder) {
            return xml("D:response") {
                "D:href" {
                    -"/${normalizePath("$path/$name")}/"
                }
                "D:propstat" {
                    "D:prop" {
                        "D:displayname" {
                            -name.ifEmpty { "root" }
                        }
                        "D:resourcetype" {
                            "D:collection" {

                            }
                        }

                        "D:getlastmodified" {
                            -getLastModifiedFormatted()
                        }
                    }
                    "D:status" {
                        -"HTTP/1.1 200 OK"
                    }
                }
            }.toString()
        }
        return xml("D:response") {
            "D:href" {
                -"/${normalizePath(path)}/${name}"
            }
            "D:propstat" {
                "D:prop" {
                    "D:displayname" {
                        -name
                    }
                    "D:resourcetype" {

                    }
                    "D:getcontenttype" {
                        -name.parseFileMimeType().toString()
                    }
                    "D:getcontentlength" {
                        -size.toString()
                    }
                    "D:getetag" {
                        -shareInfoData.hashSHA256().toHex()
                    }
                    "D:getlastmodified" {
                        -getLastModifiedFormatted()
                    }
                }
                "D:status" {
                    -"HTTP/1.1 200 OK"
                }
            }
        }.toString()
    }

}