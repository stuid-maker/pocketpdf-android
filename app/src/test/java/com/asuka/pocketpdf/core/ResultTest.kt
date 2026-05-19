package com.asuka.pocketpdf.core

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class ResultTest {

    @Test
    fun `resultOf wraps ordinary exceptions as Failure`() {
        val boom = IOException("disk unavailable")

        val result = resultOf<Unit> { throw boom }

        assertTrue(result is Result.Failure)
        assertSame(boom, (result as Result.Failure).error)
    }

    @Test
    fun `resultOf rethrows CancellationException`() {
        val cancelled = CancellationException("scope cancelled")

        try {
            resultOf<Unit> { throw cancelled }
            fail("CancellationException must not be wrapped as Result.Failure")
        } catch (t: CancellationException) {
            assertSame(cancelled, t)
        }
    }
}
