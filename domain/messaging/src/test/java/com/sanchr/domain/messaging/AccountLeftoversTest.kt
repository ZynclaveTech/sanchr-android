package com.sanchr.domain.messaging

import android.app.NotificationManager
import android.content.Context
import com.sanchr.core.crypto.profile.ProfileKeyStore
import com.sanchr.core.crypto.store.SanchrKyberPreKeyStore
import com.sanchr.core.crypto.store.SanchrSenderKeyStore
import com.sanchr.core.datastore.NotifierState
import com.sanchr.domain.messaging.media.MediaCacheCleaner
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest

class AccountLeftoversTest {
    private val notifications = mockk<NotificationManager>(relaxed = true)
    private val context = mockk<Context> { every { getSystemService(Context.NOTIFICATION_SERVICE) } returns notifications }
    private val mediaCache = mockk<MediaCacheCleaner>(relaxed = true)
    private val kyberPreKeys = mockk<SanchrKyberPreKeyStore>(relaxed = true)
    private val senderKeys = mockk<SanchrSenderKeyStore>(relaxed = true)
    private val profileKeys = mockk<ProfileKeyStore>(relaxed = true)
    private val notifierState = mockk<NotifierState>(relaxed = true)
    private val leftovers = AccountLeftovers(context, mediaCache, kyberPreKeys, senderKeys, profileKeys, notifierState)

    private fun verifyEverythingRemoved() {
        coVerify(exactly = 1) { mediaCache.clear() }
        verify(exactly = 1) { kyberPreKeys.wipeAll() }
        verify(exactly = 1) { senderKeys.wipeAll() }
        verify(exactly = 1) { profileKeys.clear() }
        coVerify(exactly = 1) { notifierState.clear() }
        verify(exactly = 1) { notifications.cancelAll() }
    }

    @Test
    fun `everything the account left outside the database is removed`() =
        runTest {
            leftovers.wipe()

            verifyEverythingRemoved()
        }

    /** A wipe that stops at the first failure leaves more behind than one that carries on. */
    @Test
    fun `one thing that will not go does not save the rest`() =
        runTest {
            coEvery { mediaCache.clear() } throws IllegalStateException("disk")
            every { senderKeys.wipeAll() } throws IllegalStateException("io")

            leftovers.wipe()

            verifyEverythingRemoved()
        }

    @Test
    fun `cancellation is not swallowed as a failed step`() =
        runTest {
            coEvery { mediaCache.clear() } throws CancellationException("cancelled")

            assertFailsWith<CancellationException> { leftovers.wipe() }
        }
}
