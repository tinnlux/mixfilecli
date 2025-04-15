package com.donut.mixfile.server.core.routes.api.webdav.utils

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

    fun getLastModifiedFormatted(): String {
        val sdf = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("GMT")
        return sdf.format(Date(lastModified))
    }


    override fun hashCode(): Int = name.hashCode()
    fun toXML(path: String): String {
        if (isFolder) {
            return xml("d:response") {
                "d:href" {
                    -"${normalizePath(path)}/${name}"
                }
                "d:propstat" {
                    "d:prop" {
                        "d:displayname" {
                            -name
                        }
                        "d:resourcetype" {
                            "d:collection" {

                            }
                        }
                        "d:getcontenttype" {

                        }
                        "d:getlastmodified" {
                            -getLastModifiedFormatted()
                        }
                    }
                    "d:status" {
                        -"HTTP/1.1 200 OK"
                    }
                }
            }.toString()
        }
        return xml("d:response") {
            "d:href" {
                -"${normalizePath(path)}/${name}"
            }
            "d:propstat" {
                "d:prop" {
                    "d:displayname" {
                        -name
                    }
                    "d:resourcetype" {

                    }
                    "d:getcontenttype" {
                        -name.parseFileMimeType()
                    }
                    "d:getcontentlength" {
                        -size.toString()
                    }
                    "d:getetag" {
                        -shareInfoData.hashSHA256().toHex()
                    }
                    "d:getlastmodified" {
                        -getLastModifiedFormatted()
                    }
                }
                "d:status" {
                    -"HTTP/1.1 200 OK"
                }
            }
        }.toString()
    }

}