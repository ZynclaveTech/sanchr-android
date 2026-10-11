package com.sanchr.feature.chats.voice

import kotlin.test.Test
import kotlin.test.assertEquals

class VoicePlaybackTurnTest {
    /** Two notes used to play over each other. */
    @Test
    fun `starting a second note stops the first`() {
        val stopped = mutableListOf<String>()
        val first = Any()
        val second = Any()

        VoicePlaybackTurn.take(first) { stopped += "first" }
        VoicePlaybackTurn.take(second) { stopped += "second" }

        assertEquals(listOf("first"), stopped)
        VoicePlaybackTurn.release(second)
    }

    @Test
    fun `resuming the same note does not stop itself`() {
        val stopped = mutableListOf<String>()
        val note = Any()

        VoicePlaybackTurn.take(note) { stopped += "note" }
        VoicePlaybackTurn.take(note) { stopped += "note" }

        assertEquals(emptyList(), stopped)
        VoicePlaybackTurn.release(note)
    }

    /** A bubble that scrolls away must not be told to stop later, or stop someone else. */
    @Test
    fun `a note that has gone is not stopped, and cannot take the turn from its successor`() {
        val stopped = mutableListOf<String>()
        val gone = Any()
        val next = Any()
        VoicePlaybackTurn.take(gone) { stopped += "gone" }
        VoicePlaybackTurn.release(gone)

        VoicePlaybackTurn.take(next) { stopped += "next" }
        VoicePlaybackTurn.release(gone) // late, and no longer its turn

        assertEquals(emptyList(), stopped)
        VoicePlaybackTurn.take(Any()) { }
        assertEquals(listOf("next"), stopped)
    }

    @Test
    fun `a touch on the waveform lands at that fraction of the clip`() {
        assertEquals(0, VoicePlaybackTurn.positionFor(0f, 140f, 60_000))
        assertEquals(30_000, VoicePlaybackTurn.positionFor(70f, 140f, 60_000))
        assertEquals(60_000, VoicePlaybackTurn.positionFor(140f, 140f, 60_000))
    }

    @Test
    fun `a touch outside the waveform is held to its ends, and nothing divides by zero`() {
        assertEquals(0, VoicePlaybackTurn.positionFor(-20f, 140f, 60_000))
        assertEquals(60_000, VoicePlaybackTurn.positionFor(900f, 140f, 60_000))
        assertEquals(0, VoicePlaybackTurn.positionFor(50f, 0f, 60_000))
        assertEquals(0, VoicePlaybackTurn.positionFor(50f, 140f, 0))
    }
}
