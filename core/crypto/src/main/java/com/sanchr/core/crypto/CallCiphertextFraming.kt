package com.sanchr.core.crypto

import org.signal.libsignal.protocol.message.CiphertextMessage

/**
 * The framing iOS uses for a Signal ciphertext that travels outside an
 * `EncryptedEnvelope` — today, call offers and answers: one type byte, then
 * the serialized message. The receiver dispatches on the byte instead of
 * parsing speculatively.
 *
 *   0x01  PreKeySignalMessage (new session)
 *   0x02  SignalMessage        (established session)
 *
 * Unlike the messaging path, these plaintexts are not padded: iOS hands the
 * JSON straight to `signalEncrypt`, so a padded-then-stripped round trip
 * would reject every payload from an iPhone.
 */
object CallCiphertextFraming {
    const val PREKEY: Byte = 0x01
    const val WHISPER: Byte = 0x02

    class UnknownTypeException(
        val typeByte: Int,
    ) : IllegalArgumentException("unknown call ciphertext type byte $typeByte")

    fun frame(message: CiphertextMessage): ByteArray {
        val type =
            when (message.type) {
                CiphertextMessage.PREKEY_TYPE -> PREKEY
                CiphertextMessage.WHISPER_TYPE -> WHISPER
                else -> throw IllegalArgumentException("unsupported ciphertext type ${message.type}")
            }
        val body = message.serialize()
        return ByteArray(body.size + 1).also {
            it[0] = type
            System.arraycopy(body, 0, it, 1, body.size)
        }
    }

    /** @return the type byte and the serialized message that follows it. */
    fun unframe(framed: ByteArray): Pair<Byte, ByteArray> {
        require(framed.size > 1) { "framed ciphertext too short: ${framed.size} bytes" }
        val type = framed[0]
        if (type != PREKEY && type != WHISPER) throw UnknownTypeException(type.toInt() and 0xFF)
        return type to framed.copyOfRange(1, framed.size)
    }
}
