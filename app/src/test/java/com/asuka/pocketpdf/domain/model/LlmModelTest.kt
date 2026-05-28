package com.asuka.pocketpdf.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * LlmModel 数据类测试。
 */
class LlmModelTest {

    @Test
    fun `constructs with id and ownedBy`() {
        val model = LlmModel(id = "gemma-3-4b", ownedBy = "google")
        assertEquals("gemma-3-4b", model.id)
        assertEquals("google", model.ownedBy)
    }

    @Test
    fun `ownedBy can be null`() {
        val model = LlmModel(id = "custom-model", ownedBy = null)
        assertEquals("custom-model", model.id)
        assertEquals(null, model.ownedBy)
    }

    @Test
    fun `data class equality works`() {
        val a = LlmModel(id = "gpt-4", ownedBy = "openai")
        val b = LlmModel(id = "gpt-4", ownedBy = "openai")
        assertEquals(a, b)
    }

    @Test
    fun `data class equality differs on id`() {
        val a = LlmModel(id = "model-a", ownedBy = null)
        val b = LlmModel(id = "model-b", ownedBy = null)
        assertNotEquals(a, b)
    }

    @Test
    fun `data class equality differs on ownedBy`() {
        val a = LlmModel(id = "m", ownedBy = "org1")
        val b = LlmModel(id = "m", ownedBy = "org2")
        assertNotEquals(a, b)
    }

    @Test
    fun `equals treats null ownedBy consistently`() {
        val a = LlmModel(id = "m", ownedBy = null)
        val b = LlmModel(id = "m", ownedBy = null)
        assertEquals(a, b)
    }

    @Test
    fun `toString contains id`() {
        val model = LlmModel(id = "deepseek-v3", ownedBy = "deepseek")
        val str = model.toString()
        assertEquals(true, str.contains("deepseek-v3"))
        assertEquals(true, str.contains("deepseek"))
    }
}
