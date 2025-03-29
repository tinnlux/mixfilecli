package com.donut.mixfile.server.core.utils

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONWriter
import com.alibaba.fastjson2.writer.ObjectWriter
import java.lang.reflect.Type
import java.util.Date

object MillisDateWriter : ObjectWriter<Date?> {
    override fun write(
        jsonWriter: JSONWriter,
        `object`: Any?,
        fieldName: Any?,
        fieldType: Type?,
        features: Long
    ) {
        if (`object` == null) {
            jsonWriter.writeNull()
            return
        }
        jsonWriter.writeInt64((`object` as Date).time) // 输出毫秒时间戳
    }

}

fun registerJson() {
    JSON.register(Date::class.java, MillisDateWriter)
}