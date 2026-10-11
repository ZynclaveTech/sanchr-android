package com.sanchr.feature.contacts.lookup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SmsInviteTest {
    @Test
    fun `a number typed with its country code is addressed as it was typed`() {
        assertEquals("smsto:+919876543210", SmsInvite.addressFor("+91 98765 43210"))
        assertEquals("smsto:+14155551234", SmsInvite.addressFor(" +1 (415) 555-1234 "))
    }

    @Test
    fun `a number typed without a plus is not given one`() {
        assertEquals("smsto:9876543210", SmsInvite.addressFor("98765 43210"))
    }

    /** What was typed goes into a URI: nothing but digits and a leading plus gets through. */
    @Test
    fun `nothing but the number reaches the address`() {
        assertEquals("smsto:+15551234", SmsInvite.addressFor("+1555?body=x&1234"))
        assertEquals("smsto:1234", SmsInvite.addressFor("12+34"))
    }

    @Test
    fun `no number, no invitation`() {
        assertNull(SmsInvite.addressFor(""))
        assertNull(SmsInvite.addressFor("   "))
        assertNull(SmsInvite.addressFor("+"))
        assertNull(SmsInvite.addressFor("abc"))
    }

    @Test
    fun `the invitation says where to get the app`() {
        assertTrue("https://sanchr.com/download" in SmsInvite.MESSAGE)
    }
}
