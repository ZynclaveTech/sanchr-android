package com.sanchr.domain.messaging

/**
 * Which sealed sends wake an offline phone with a notification.
 *
 * Every sealed send — an ordinary message, a read receipt, presence, a
 * profile-key delivery — travels over the same `SendSealedMessage` RPC
 * inside the same opaque envelope, so the server cannot tell them apart on
 * its own. [SealedDeviceMessage.silent][com.sanchr.proto.messaging.SealedDeviceMessage.silent]
 * is how a client tells it: set, the server skips the recipient's offline
 * push for that device message. It does nothing else — the message is still
 * delivered live to a connected device exactly as before, and it does not
 * change how the app itself displays anything.
 *
 * The flag is deliberately an opt-out (defaults to alerting): an older
 * client that has no notion of this enum, and so never sets it, keeps
 * alerting for everything, which is the safe failure mode. The cost is that
 * it discloses one bit the server did not have before — whether a given
 * send is control traffic or a real message a person wrote — against what
 * it already observes (recipient, device fan-out, timing, size), a small
 * addition traded for not buzzing someone's phone for a read receipt.
 *
 * Modelled as an enum rather than a bare `Boolean` so a new sealed send has
 * to say which kind it is at the call site, where whoever is writing it
 * knows the answer — a defaulted or bare flag would silently pick one.
 */
enum class SealedSendKind {
    /** Something a person wrote. Alerts. */
    Message,

    /**
     * Machinery: read receipts, presence, profile-key delivery. Delivered
     * live exactly as before, but does not wake an offline device with a
     * notification.
     */
    Control,

    ;

    val isSilent: Boolean get() = this == Control
}
