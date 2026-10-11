package com.sanchr.core.designsystem.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What is scanned starts a conversation, so only a real profile link will do. */
class ProfileLinkTest {
    private val user = "3f1c9a52-7d0e-4b6a-9c11-2e8f5a7d4b90"

    @Test
    fun `the link a profile shows is read back to its user`() {
        assertEquals(user, QrCodes.userIdFromProfileLink(QrCodes.profileLink(user)))
        assertEquals(user, QrCodes.userIdFromProfileLink("  https://www.sanchr.com/u/${user.uppercase()}\n"))
    }

    @Test
    fun `a host that only ends in the right letters is not Sanchr`() {
        assertNull(QrCodes.userIdFromProfileLink("https://evilsanchr.com/u/$user"))
        assertNull(QrCodes.userIdFromProfileLink("https://sanchr.com.evil.example/u/$user"))
        assertNull(QrCodes.userIdFromProfileLink("https://sanchr.com@evil.example/u/$user"))
    }

    @Test
    fun `it must be https, at that path, and nothing more`() {
        assertNull(QrCodes.userIdFromProfileLink("http://sanchr.com/u/$user"))
        assertNull(QrCodes.userIdFromProfileLink("https://sanchr.com/x/$user"))
        assertNull(QrCodes.userIdFromProfileLink("https://sanchr.com/u/$user/extra"))
        assertNull(QrCodes.userIdFromProfileLink("https://sanchr.com/u/$user?next=1"))
        assertNull(QrCodes.userIdFromProfileLink("https://sanchr.com/u/$user#frag"))
    }

    @Test
    fun `what follows must be the shape a user id has`() {
        assertNull(QrCodes.userIdFromProfileLink("https://sanchr.com/u/"))
        assertNull(QrCodes.userIdFromProfileLink("https://sanchr.com/u/not-an-id"))
        assertNull(QrCodes.userIdFromProfileLink("https://sanchr.com/u/../../etc"))
    }

    @Test
    fun `other things people scan are not profile links`() {
        assertNull(QrCodes.userIdFromProfileLink(""))
        assertNull(QrCodes.userIdFromProfileLink("WIFI:T:WPA;S:cafe;P:secret;;"))
        assertNull(QrCodes.userIdFromProfileLink(user))
        assertNull(QrCodes.userIdFromProfileLink("not a url at all"))
    }
}
