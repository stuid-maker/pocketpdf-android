package com.asuka.pocketpdf.data.remote.repository

import com.asuka.pocketpdf.core.DispatcherProvider
import com.asuka.pocketpdf.data.local.SettingsDataStore
import com.asuka.pocketpdf.domain.model.ChatMessage
import com.squareup.moshi.Moshi
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class LlmRepositoryImplCancellationTest {

    @Test
    fun `cancelling stream collection cancels blocking okhttp call`() = runBlocking {
        val requestStarted = CountDownLatch(1)
        val callCancelled = CountDownLatch(1)
        val blockingCall = AtomicReference<Call?>()
        val collectionFailure = AtomicReference<Throwable?>()

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val call = chain.call()
                blockingCall.set(call)
                requestStarted.countDown()
                while (!call.isCanceled()) {
                    Thread.sleep(10)
                }
                callCancelled.countDown()
                throw IOException("Canceled")
            }
            .build()
        val settings = mockk<SettingsDataStore>()
        every { settings.baseUrl } returns flowOf("http://localhost:1/v1")
        every { settings.apiKey } returns flowOf(null)
        val repository = LlmRepositoryImpl(
            okHttpClient = client,
            moshi = Moshi.Builder().build(),
            settingsDataStore = settings,
            dispatchers = IoDispatcherProvider,
        )

        val collection = launch(Dispatchers.Default) {
            try {
                repository.chatCompletionStream(
                    model = "test-model",
                    messages = listOf(ChatMessage(ChatMessage.ROLE_USER, "hello")),
                ).collect()
            } catch (error: Throwable) {
                collectionFailure.set(error)
            }
        }

        try {
            assertTrue(
                "HTTP request did not start; collector failed with ${collectionFailure.get()}",
                requestStarted.await(10, TimeUnit.SECONDS),
            )
            collection.cancel()
            assertTrue(
                "Cancelling the collector must cancel the underlying OkHttp call",
                callCancelled.await(10, TimeUnit.SECONDS),
            )
        } finally {
            blockingCall.get()?.cancel()
            collection.cancelAndJoin()
            client.dispatcher.executorService.shutdownNow()
            client.connectionPool.evictAll()
        }
    }

    private object IoDispatcherProvider : DispatcherProvider {
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
        override val io: CoroutineDispatcher = Dispatchers.IO
        override val default: CoroutineDispatcher = Dispatchers.Default
    }
}
