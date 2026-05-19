package com.asuka.pocketpdf.data.local.converter

import androidx.room.TypeConverter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VectorTypeConverter {

    @TypeConverter
    fun fromFloatArray(value: FloatArray?): ByteArray? {
        if (value == null) return null
        // 每个 Float 占 4 个字节
        val buffer = ByteBuffer.allocate(value.size * 4)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        value.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    @TypeConverter
    fun toFloatArray(value: ByteArray?): FloatArray? {
        if (value == null) return null
        val buffer = ByteBuffer.wrap(value)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        val floatArray = FloatArray(value.size / 4)
        for (i in floatArray.indices) {
            floatArray[i] = buffer.getFloat()
        }
        return floatArray
    }
}
