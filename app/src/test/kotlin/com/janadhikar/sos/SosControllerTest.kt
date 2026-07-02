package com.janadhikar.sos

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SosControllerTest {

    private class FakeDispatcher(
        private val configured: Boolean = true,
        private val sendResult: Boolean = true,
    ) : SosController.Dispatcher {
        var dispatched = false
        override fun isConfigured() = configured
        override fun dispatch(): Boolean {
            dispatched = true
            return sendResult
        }
    }

    @Test
    fun `counts down then dispatches`() = runTest {
        val dispatcher = FakeDispatcher()
        val sos = SosController(backgroundScope, dispatcher, countdownSeconds = 3)
        sos.state.test {
            assertThat(awaitItem()).isEqualTo(SosController.SosState.Disarmed)
            sos.arm()
            assertThat(awaitItem()).isEqualTo(SosController.SosState.Counting(3))
            assertThat(awaitItem()).isEqualTo(SosController.SosState.Counting(2))
            assertThat(awaitItem()).isEqualTo(SosController.SosState.Counting(1))
            assertThat(awaitItem()).isEqualTo(SosController.SosState.Sent)
            assertThat(dispatcher.dispatched).isTrue()
        }
    }

    @Test
    fun `cancel during countdown never dispatches`() = runTest {
        val dispatcher = FakeDispatcher()
        val sos = SosController(backgroundScope, dispatcher, countdownSeconds = 10)
        sos.state.test {
            skipItems(1) // Disarmed
            sos.arm()
            assertThat(awaitItem()).isEqualTo(SosController.SosState.Counting(10))
            sos.cancel()
            assertThat(awaitItem()).isEqualTo(SosController.SosState.Cancelled)
            assertThat(dispatcher.dispatched).isFalse()
        }
    }

    @Test
    fun `unconfigured contact reports NotConfigured instead of counting`() = runTest {
        val sos = SosController(backgroundScope, FakeDispatcher(configured = false))
        sos.state.test {
            skipItems(1)
            sos.arm()
            assertThat(awaitItem()).isEqualTo(SosController.SosState.NotConfigured)
        }
    }

    @Test
    fun `failed send surfaces as Failed`() = runTest {
        val sos = SosController(backgroundScope, FakeDispatcher(sendResult = false), countdownSeconds = 1)
        sos.state.test {
            skipItems(1)
            sos.arm()
            assertThat(awaitItem()).isEqualTo(SosController.SosState.Counting(1))
            assertThat(awaitItem()).isEqualTo(SosController.SosState.Failed)
        }
    }

    @Test
    fun `arm is idempotent while counting`() = runTest {
        val sos = SosController(backgroundScope, FakeDispatcher(), countdownSeconds = 5)
        sos.state.test {
            skipItems(1)
            sos.arm()
            assertThat(awaitItem()).isEqualTo(SosController.SosState.Counting(5))
            sos.arm() // second Resolution recomposition must not restart the clock
            expectNoEvents()
        }
    }

    @Test
    fun `disarm resets for the next incident`() = runTest {
        val sos = SosController(backgroundScope, FakeDispatcher(), countdownSeconds = 5)
        sos.state.test {
            skipItems(1)
            sos.arm()
            skipItems(1) // Counting(5)
            sos.disarm()
            assertThat(awaitItem()).isEqualTo(SosController.SosState.Disarmed)
        }
    }
}
