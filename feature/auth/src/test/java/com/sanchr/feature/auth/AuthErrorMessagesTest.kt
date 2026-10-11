package com.sanchr.feature.auth

import io.grpc.Status
import io.grpc.StatusException
import io.grpc.StatusRuntimeException
import java.io.IOException
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Sign-in used to show the exception's own text. */
class AuthErrorMessagesTest {
    private fun status(
        code: Status.Code,
        description: String = "server detail that must not be shown",
    ) = StatusRuntimeException(code.toStatus().withDescription(description))

    @Test
    fun `a wrong or expired code says so`() {
        listOf(Status.Code.UNAUTHENTICATED, Status.Code.PERMISSION_DENIED, Status.Code.INVALID_ARGUMENT, Status.Code.NOT_FOUND).forEach {
            assertEquals(AuthErrorMessages.BAD_CODE, AuthErrorMessages.forVerifyingCode(status(it)), it.name)
        }
    }

    @Test
    fun `a number the server will not take says so`() {
        assertEquals(AuthErrorMessages.BAD_NUMBER, AuthErrorMessages.forRequestingCode(status(Status.Code.INVALID_ARGUMENT)))
        assertEquals(AuthErrorMessages.BAD_NUMBER, AuthErrorMessages.forRequestingCode(status(Status.Code.FAILED_PRECONDITION)))
    }

    @Test
    fun `no network reads the same at every step`() {
        val offline = status(Status.Code.UNAVAILABLE, "Unable to resolve host api.sanchr.com")

        assertEquals(AuthErrorMessages.NO_CONNECTION, AuthErrorMessages.forRequestingCode(offline))
        assertEquals(AuthErrorMessages.NO_CONNECTION, AuthErrorMessages.forVerifyingCode(offline))
        assertEquals(AuthErrorMessages.NO_CONNECTION, AuthErrorMessages.forSettingUp(offline))
    }

    @Test
    fun `a bare network failure is no network too`() {
        assertEquals(AuthErrorMessages.NO_CONNECTION, AuthErrorMessages.forRequestingCode(UnknownHostException("api.sanchr.com")))
        assertEquals(AuthErrorMessages.NO_CONNECTION, AuthErrorMessages.forVerifyingCode(RuntimeException(IOException("reset"))))
    }

    @Test
    fun `being rate limited and timing out each have their own words`() {
        assertEquals(AuthErrorMessages.TOO_MANY_ATTEMPTS, AuthErrorMessages.forRequestingCode(status(Status.Code.RESOURCE_EXHAUSTED)))
        assertEquals(AuthErrorMessages.TOO_MANY_ATTEMPTS, AuthErrorMessages.forVerifyingCode(status(Status.Code.RESOURCE_EXHAUSTED)))
        assertEquals(AuthErrorMessages.TOO_SLOW, AuthErrorMessages.forVerifyingCode(status(Status.Code.DEADLINE_EXCEEDED)))
    }

    @Test
    fun `anything else gets the step's own general message`() {
        assertEquals(AuthErrorMessages.CODE_NOT_SENT, AuthErrorMessages.forRequestingCode(status(Status.Code.INTERNAL)))
        assertEquals(AuthErrorMessages.CODE_NOT_CHECKED, AuthErrorMessages.forVerifyingCode(IllegalStateException("boom")))
        assertEquals(AuthErrorMessages.SETUP_FAILED, AuthErrorMessages.forSettingUp(IllegalStateException("keystore")))
    }

    @Test
    fun `the checked kind of status exception is read the same way`() {
        val checked = StatusException(Status.UNAUTHENTICATED.withDescription("invalid or expired code"))

        assertEquals(AuthErrorMessages.BAD_CODE, AuthErrorMessages.forVerifyingCode(checked))
    }

    /** Whatever goes wrong, what the server or the library wrote stays off the screen. */
    @Test
    fun `nothing the server wrote is ever shown`() {
        val secret = "pg error: relation pending_registrations at 10.0.3.7"
        val shown =
            Status.Code.entries.flatMap { code ->
                val error = status(code, secret)
                listOf(
                    AuthErrorMessages.forRequestingCode(error),
                    AuthErrorMessages.forVerifyingCode(error),
                    AuthErrorMessages.forSettingUp(error),
                )
            }

        shown.forEach { message ->
            assertFalse(secret in message)
            assertFalse(Status.Code.entries.any { it.name in message }, message)
        }
    }
}
