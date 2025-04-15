package com.donut.mixfile.server.core.routes.api.webdav.utils

import com.alibaba.fastjson2.toJSONString
import java.util.concurrent.ConcurrentHashMap


class XmlBuilder(private val name: String) {
    private val children = mutableListOf<XmlBuilder>()
    private val attributes = mutableMapOf<String, String>()
    private var text: String? = null
    var xmlns: String? = null
        set(value) {
            field = value
            attributes["xmlns"] = value ?: return
        }

    operator fun String.invoke(block: XmlBuilder.() -> Unit): XmlBuilder {
        val child = XmlBuilder(this).apply(block)
        children.add(child)
        return child
    }

    operator fun String.unaryMinus() {
        text = this
    }

    fun attribute(name: String, value: Any) {
        attributes[name] = value.toString()
    }

    private fun String.encodeXml(): String = this
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    override fun toString(): String = buildString {
        toString(this, 0)
    }

    private fun toString(sb: StringBuilder, level: Int) {
        sb.append("  ".repeat(level))
        sb.append("<$name")

        attributes.forEach { (k, v) ->
            sb.append(" $k=\"${v.encodeXml()}\"")
        }

        when {
            text != null && children.isEmpty() -> {
                sb.append(">${text?.encodeXml()}</$name>\n")
            }

            children.isNotEmpty() -> {
                sb.append(">\n")
                children.forEach { it.toString(sb, level + 1) }
                sb.append("  ".repeat(level))
                sb.append("</$name>\n")
            }

            else -> {
                sb.append("/>\n")
            }
        }
    }
}

fun xml(name: String, block: XmlBuilder.() -> Unit): XmlBuilder =
    XmlBuilder(name).apply(block)

// 使用示例
fun main() {

    """
                  <?xml version="1.0" encoding="utf-8"?>
                  <d:multistatus xmlns:d="DAV:">
                      <!-- 根目录 -->
                      <d:response>
                          <d:href>/path/to/directory/</d:href>
                          <d:propstat>
                              <d:prop>
                                  <d:displayname>directory</d:displayname>
                                  <d:resourcetype>
                                      <d:collection/>
                                  </d:resourcetype>
                                  <d:getcontentlength></d:getcontentlength>
                                  <d:getcontenttype></d:getcontenttype>
                                  <d:getetag>"dir123456"</d:getetag>
                                  <d:getlastmodified>Wed, 13 Apr 2025 10:00:00 GMT</d:getlastmodified>
                              </d:prop>
                              <d:status>HTTP/1.1 200 OK</d:status>
                          </d:propstat>
                      </d:response>
                      <!-- 目录中的文件 -->
                      <d:response>
                          <d:href>/path/to/directory/file.txt</d:href>
                          <d:propstat>
                              <d:prop>
                                  <d:displayname>file.txt</d:displayname>
                                  <d:resourcetype></d:resourcetype>
                                  <d:getcontentlength>12345</d:getcontentlength>
                                  <d:getcontenttype>text/plain</d:getcontenttype>
                                  <d:getetag>"file789012"</d:getetag>
                                  <d:getlastmodified>Wed, 13 Apr 2025 09:30:00 GMT</d:getlastmodified>
                              </d:prop>
                              <d:status>HTTP/1.1 200 OK</d:status>
                          </d:propstat>
                      </d:response>
                      <!-- 目录中的子文件夹 -->
                      <d:response>
                          <d:href>/path/to/directory/subfolder/</d:href>
                          <d:propstat>
                              <d:prop>
                                  <d:displayname>subfolder</d:displayname>
                                  <d:resourcetype>
                                      <d:collection/>
                                  </d:resourcetype>
                                  <d:getcontentlength>233</d:getcontentlength>
                                  <d:getcontenttype>text/xml</d:getcontenttype>
                                  <d:getetag>"subfolder345678"</d:getetag>
                                  <d:getlastmodified>Wed, 13 Apr 2025 08:00:00 GMT</d:getlastmodified>
                              </d:prop>
                              <d:status>HTTP/1.1 200 OK</d:status>
                          </d:propstat>
                      </d:response>
                  </d:multistatus>
                """.trimIndent()

    val people = xml("people") {
        xmlns = "DAV:"
        "person" {
            attribute("id", 1)
            "d:firstName" {
                attribute("1", "2")
                -"John"
            }
            "lastName" {
                -"Doe"
            }
            "phone" {
                -"555-555-5555"
            }
        }
    }
    """
        <d:response>
                          <d:href>/path/to/directory/file.txt</d:href>
                          <d:propstat>
                              <d:prop>
                                  <d:displayname>file.txt</d:displayname>
                                  <d:resourcetype></d:resourcetype>
                                  <d:getcontentlength>12345</d:getcontentlength>
                                  <d:getcontenttype>text/plain</d:getcontenttype>
                                  <d:getetag>"file789012"</d:getetag>
                                  <d:getlastmodified>Wed, 13 Apr 2025 09:30:00 GMT</d:getlastmodified>
                              </d:prop>
                              <d:status>HTTP/1.1 200 OK</d:status>
                          </d:propstat>
                      </d:response>
        
    """.trimIndent()

    val file = xml("d:response") {
        "d:href" {
            -"/path/to/directory/file.txt"
        }
        "d:propstat" {
            "d:prop" {
                "d:displayname" {

                }
                "d:resourcetype" {

                }
                "d:getcontentlength" {

                }
                "d:getetag" {

                }
                "d:getlastmodified" {

                }
            }
            "d:status" {
                -"HTTP/1.1 200 OK"
            }
        }
    }
    println(file)

    val data = ConcurrentHashMap<String, String>()
    data.put("dwad", "ggg")
    println(data.toJSONString())

    val asString = people.toString()
    println(asString)
}