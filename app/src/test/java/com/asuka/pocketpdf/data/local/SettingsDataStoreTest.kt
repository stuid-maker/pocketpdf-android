package com.asuka.pocketpdf.data.local

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import app.cash.turbine.test

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsDataStoreTest {

    private val cipher = FakeApiKeyCipher()
    private lateinit var store: SettingsDataStore

    @Before
    fun setUp() {
        store = SettingsDataStore(RuntimeEnvironment.getApplication(), cipher)
    }

    @After
    fun tearDown() = runTest {
        store.resetDefaults()
    }

    @Test
    fun `api key is encrypted before persistence and decrypted when read`() = runTest {
        store.setApiKey("sk-secret")

        assertEquals("sk-secret", store.apiKey.first())
        assertEquals(listOf("sk-secret"), cipher.encryptedInputs)
        assertFalse(cipher.persistedValues.single().contains("sk-secret"))
    }

    @Test
    fun `clearing api key removes persisted value`() = runTest {
        store.setApiKey("sk-secret")
        store.setApiKey(null)

        assertNull(store.apiKey.first())
    }

    @Test
    fun `reset defaults does not transiently clear completed onboarding`() = runTest {
        store.setOnboardingCompleted(true)
        store.setBaseUrl("https://example.test/v1")

        store.onboardingCompleted.test {
            assertEquals(true, awaitItem())

            store.resetDefaults()

            val emittedAfterReset = withTimeoutOrNull(500) { awaitItem() }
            if (emittedAfterReset != null) {
                assertEquals(true, emittedAfterReset)
            }
        }
    }

    private class FakeApiKeyCipher : ApiKeyCipher {
        val encryptedInputs = mutableListOf<String>()
        val persistedValues = mutableListOf<String>()

        override fun encrypt(plaintext: String): String {
            encryptedInputs += plaintext
            return "enc:v1:${plaintext.reversed()}".also(persistedValues::add)
        }

        override fun decrypt(ciphertext: String): String =
            ciphertext.removePrefix("enc:v1:").reversed()

        override fun isEncrypted(value: String): Boolean = value.startsWith("enc:v1:")
    }
}
