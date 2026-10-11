package com.sanchr.feature.auth

import io.grpc.Status
import java.io.IOException

/**
 * What someone reads when signing in goes wrong.
 *
 * They used to read the exception: `e.message`, straight onto the screen. For
 * a wrong code that is "UNAUTHENTICATED: invalid or expired code"; with no
 * network, "UNAVAILABLE: Unable to resolve host api.sanchr.com"; rate-limited,
 * "RESOURCE_EXHAUSTED: …". True, and no use to the person holding the phone,
 * who needs to know what to do next. iOS has said it in words for a while;
 * these are the same words.
 *
 * Nothing the server or the network library wrote is ever shown. A status
 * with no wording of its own gets the step's general message.
 */
internal object AuthErrorMessages {
    const val NO_CONNECTION = "Can't reach Sanchr. Check your connection and try again."
    const val TOO_SLOW = "That took too long. Check your connection and try again."
    const val TOO_MANY_ATTEMPTS = "Too many attempts. Wait a little and try again."
    const val BAD_NUMBER = "That doesn't look like a valid phone number for the selected country."
    const val BAD_CODE = "That code isn't right or has expired. Check the message and try again."
    const val CODE_NOT_SENT = "Couldn't send the code. Try again in a moment."
    const val CODE_NOT_CHECKED = "Couldn't check the code. Try again in a moment."
    const val SETUP_FAILED = "Couldn't finish setting up this phone. Try again in a moment."

    /** Asking for a code, or asking for it again. */
    fun forRequestingCode(error: Throwable): String =
        common(error) ?: when (Status.fromThrowable(error).code) {
            Status.Code.INVALID_ARGUMENT, Status.Code.FAILED_PRECONDITION -> BAD_NUMBER
            else -> CODE_NOT_SENT
        }

    /** Submitting the code. */
    fun forVerifyingCode(error: Throwable): String =
        common(error) ?: when (Status.fromThrowable(error).code) {
            Status.Code.UNAUTHENTICATED,
            Status.Code.PERMISSION_DENIED,
            Status.Code.INVALID_ARGUMENT,
            Status.Code.NOT_FOUND,
            -> BAD_CODE
            else -> CODE_NOT_CHECKED
        }

    /** Generating and uploading keys once the code was accepted. */
    fun forSettingUp(error: Throwable): String = common(error) ?: SETUP_FAILED

    /** The failures that mean the same thing whatever was being attempted. */
    private fun common(error: Throwable): String? =
        when (Status.fromThrowable(error).code) {
            Status.Code.UNAVAILABLE -> NO_CONNECTION
            Status.Code.DEADLINE_EXCEEDED -> TOO_SLOW
            Status.Code.RESOURCE_EXHAUSTED -> TOO_MANY_ATTEMPTS
            else -> NO_CONNECTION.takeIf { error is IOException || error.cause is IOException }
        }
}
