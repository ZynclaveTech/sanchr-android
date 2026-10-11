package com.sanchr.app.bootstrap

import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.datastore.UserPreferences
import com.sanchr.proto.settings.SettingsServiceClient
import com.sanchr.proto.settings.UserSettings
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class ServerSettingsLoaderTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val dispatchers =
        object : DispatcherProvider {
            override val main: CoroutineDispatcher = dispatcher
            override val io: CoroutineDispatcher = dispatcher
            override val default: CoroutineDispatcher = dispatcher
            override val unconfined: CoroutineDispatcher = dispatcher
            override val signalDispatcher: CoroutineDispatcher = dispatcher
        }
    private val client = mockk<SettingsServiceClient>()
    private val prefs = mockk<UserPreferences>(relaxed = true)
    private val loader = ServerSettingsLoader(client, prefs, dispatchers)

    /** Turned on from another device, it has to reach this one without Settings being opened. */
    @Test
    fun `Sanchr Mode turned on elsewhere is taken up at launch`() =
        runTest(dispatcher) {
            coEvery { client.getSettings(any()) } returns UserSettings(sanchrModeEnabled = true)

            assertTrue(loader.load())

            coVerify(exactly = 1) { prefs.setSanchrModeEnabled(true) }
        }

    @Test
    fun `and turned off elsewhere, it is turned off here`() =
        runTest(dispatcher) {
            coEvery { client.getSettings(any()) } returns UserSettings(sanchrModeEnabled = false)

            assertTrue(loader.load())

            coVerify(exactly = 1) { prefs.setSanchrModeEnabled(false) }
        }

    /**
     * These are written on the phone first and sent to the server later. The
     * server's copy can be the older one, and taking it would undo a choice
     * made offline — most harmfully, someone having turned a thing off.
     */
    @Test
    fun `the individual toggles are not overwritten from the server`() =
        runTest(dispatcher) {
            coEvery { client.getSettings(any()) } returns
                UserSettings(readReceiptsEnabled = true, typingIndicatorsEnabled = true, onlineStatusVisible = true)

            loader.load()

            coVerify(exactly = 0) { prefs.setReadReceiptsEnabled(any()) }
            coVerify(exactly = 0) { prefs.setTypingIndicatorsEnabled(any()) }
            coVerify(exactly = 0) { prefs.setOnlineStatusVisible(any()) }
        }

    @Test
    fun `offline, the phone keeps what it has`() =
        runTest(dispatcher) {
            coEvery { client.getSettings(any()) } throws IllegalStateException("UNAVAILABLE")

            assertFalse(loader.load())

            coVerify(exactly = 0) { prefs.setSanchrModeEnabled(any()) }
        }

    @Test
    fun `cancellation is not reported as a failed load`() =
        runTest(dispatcher) {
            coEvery { client.getSettings(any()) } throws CancellationException("gone")

            assertFailsWith<CancellationException> { loader.load() }
        }
}
