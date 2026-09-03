package com.sanchr.core.network

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnonymousMethodsTest {
    @Test
    fun `a sealed send is anonymous`() {
        assertTrue(AnonymousMethods.isAnonymous("sanchr.messaging.MessagingService/SendSealedMessage"))
    }

    @Test
    fun `everything else is not`() {
        listOf(
            "sanchr.messaging.MessagingService/SendMessage",
            "sanchr.messaging.MessagingService/GetDeliveryTokens",
            "sanchr.messaging.MessagingService/GetSenderCertificate",
            "sanchr.auth.AuthService/VerifyOTP",
        ).forEach { assertFalse(AnonymousMethods.isAnonymous(it), it) }
    }
}
