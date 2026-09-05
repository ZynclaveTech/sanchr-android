package com.sanchr.domain.messaging.media

/**
 * Whether incoming media may be fetched without the user asking, per
 * Settings → Storage → Media auto-download.
 *
 * The screen offers Wi-Fi only, Always and Never, and nothing read it: every
 * photo in a conversation downloaded as soon as its bubble scrolled into
 * view, on any network. "Wi-Fi only" is a promise about someone's data bill.
 *
 * Applies only to automatic fetches. Tapping a photo is the user asking for
 * it, and always downloads.
 */
object MediaAutoDownloadPolicy {
    const val WIFI = "wifi"
    const val ALWAYS = "always"
    const val NEVER = "never"

    /**
     * @param setting the stored choice; anything unrecognised is treated as
     *   [WIFI], the option the app ships with.
     * @param isMetered whether the active network charges by the byte.
     */
    fun shouldAutoDownload(
        setting: String,
        isMetered: Boolean,
    ): Boolean =
        when (setting) {
            ALWAYS -> true
            NEVER -> false
            else -> !isMetered
        }
}
