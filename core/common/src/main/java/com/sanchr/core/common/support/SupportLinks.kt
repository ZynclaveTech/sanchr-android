package com.sanchr.core.common.support

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast

/**
 * The addresses and pages the support screens point at. They live on
 * sanchr.com so one edit updates every client, as iOS `LegalDocument` does.
 */
object SupportLinks {
    const val PRIVACY_POLICY = "https://sanchr.com/privacy"
    const val TERMS_OF_SERVICE = "https://sanchr.com/terms"
    const val DOCUMENTATION = "https://sanchr.com/docs"
    const val OPEN_SOURCE_LICENSES = "https://sanchr.com/licenses"
    const val SUPPORT_EMAIL = "support@sanchr.com"
    const val SECURITY_EMAIL = "security@sanchr.com"

    /** Opens [url] in the browser; tells the user when the device has none. */
    fun openUrl(
        context: Context,
        url: String,
    ) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No app can open $url", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Hands [subject] and [body] to the user's mail app. With no mail app
     * installed the message is copied to the clipboard instead of vanishing,
     * as iOS falls back to the pasteboard.
     */
    fun sendEmail(
        context: Context,
        to: String,
        subject: String,
        body: String,
    ) {
        val intent =
            Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$to")).apply {
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("Sanchr support", "To: $to\nSubject: $subject\n\n$body"))
            Toast.makeText(context, "No mail app found. The message was copied to the clipboard.", Toast.LENGTH_LONG).show()
        }
    }

    /** "Sanchr 1.2.3 (45)" for the version footer and support mails. */
    fun appVersion(context: Context): String =
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val code =
                if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.P
                ) {
                    info.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    info.versionCode.toLong()
                }
            "${info.versionName} ($code)"
        }.getOrDefault("unknown")

    /** The device and app details a support mail carries, as iOS's "include device info" does. */
    fun deviceReport(context: Context): String =
        deviceReport(Build.MANUFACTURER, Build.MODEL, Build.VERSION.RELEASE, Build.VERSION.SDK_INT, appVersion(context))

    internal fun deviceReport(
        manufacturer: String,
        model: String,
        release: String,
        apiLevel: Int,
        appVersion: String,
    ): String = "---\nDevice: $manufacturer $model (Android $release, API $apiLevel)\nApp: Sanchr $appVersion"
}
