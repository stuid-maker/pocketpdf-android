package com.asuka.pocketpdf.data.local.converter

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VectorTypeConverterTest {

    private val converter = VectorTypeConverter()

    @Test
    fun `round trip conversion works seamlessly`() {
        // Arrange
        val original = floatArrayOf(0.1f, -0.5f, 3.14159f, 0.0f, 100f)

        // Act
        val bytes = converter.fromFloatArray(original)
        val reconstructed = converter.toFloatArray(bytes)

        // Assert
        // delta 设为 0，因为字节无损转换应该是严格相等的
        assertArrayEquals(original, reconstructed, 0.0f)
    }

    @Test
    fun `null handling works seamlessly`() {
        assertNull(converter.fromFloatArray(null))
        assertNull(converter.toFloatArray(null))
    }
}
