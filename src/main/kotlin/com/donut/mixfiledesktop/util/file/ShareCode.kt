package com.donut.mixfiledesktop.util.file

import com.donut.mixfiledesktop.server.utils.bean.MixShareInfo
import com.donut.mixfiledesktop.util.decodeHex
import com.donut.mixfiledesktop.util.toHex


private val encodeMap = run {
    val map = mutableMapOf<String, String>()
    for (value in 0xfe00..0xfe0f) {
        val key = (value - 0xfe00).toString(16)
        map[key] = "${value.toChar()}"
    }
    map
}

fun MixShareInfo.shareCode(): String {
    return "mf://$this"
}

fun parseShareCode(code: String): MixShareInfo? {
    val mf = code.substringAfter("mf://")
    val encoded = decodeHex(mf)
    val parsed = MixShareInfo.tryFromString(encoded) ?: MixShareInfo.tryFromString(mf)
    return parsed
}

fun encodeHex(data: String): String {
    val sb = StringBuilder()
    for (element in data.toByteArray().toHex()) {
        if (encodeMap.containsKey(element.toString())) {
            sb.append(encodeMap[element.toString()])
        }
    }
    return sb.toString()
}

fun decodeHex(data: String): String {
    val sb = StringBuilder()
    for (element in data) {
        if (encodeMap.containsValue(element.toString())) {
            sb.append(encodeMap.filterValues { it == element.toString() }.keys.first())
        }
    }
    return sb.toString().decodeHex().decodeToString()
}