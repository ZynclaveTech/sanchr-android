package com.sanchr.core.network.link

import com.sanchr.core.network.link.LinkPreviewGuard.refusePrivateAddresses
import com.sanchr.core.network.media.BlobStoreException
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.BufferedSource

/**
 * Downloads a URL that a message supplied, as opposed to one this app's own
 * server signed.
 *
 * `BlobStore` is for presigned object-storage URLs and trusts where they
 * point. An attachment can instead carry a plain link the sender wrote, and
 * that was fetched by the same client: nothing looked at what the name
 * resolved to or where it redirected, and the whole body was read into memory
 * however large it turned out to be.
 *
 * This client is the link-preview one's sibling — the same checks at the
 * connection, on every hop ([LinkPreviewGuard]) — with a cap on what it will
 * read.
 */
@Singleton
class GuardedLinkDownloader internal constructor(
    private val client: OkHttpClient,
) {
    @Inject
    constructor() : this(
        OkHttpClient
            .Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .refusePrivateAddresses()
            .build(),
    )

    /**
     * @throws BlobStoreException on a non-2xx status.
     * @throws IOException on transport failure, a refused address, or a body over [maxBytes].
     */
    suspend fun get(
        url: String,
        maxBytes: Long,
    ): ByteArray =
        withContext(Dispatchers.IO) {
            client
                .newCall(
                    Request
                        .Builder()
                        .url(url)
                        .get()
                        .build(),
                ).execute()
                .use { response ->
                    if (!response.isSuccessful) throw BlobStoreException(response.code, "GET failed: HTTP ${response.code}")
                    response.body?.source()?.let { readCapped(it, maxBytes) } ?: ByteArray(0)
                }
        }

    companion object {
        private const val CONNECT_TIMEOUT_SECONDS = 30L
        private const val READ_TIMEOUT_SECONDS = 60L

        /**
         * All of [source] if it is at most [maxBytes]; otherwise an error,
         * having read one byte past the limit and no further. The length a
         * server declares is not consulted — it is the server's claim.
         */
        internal fun readCapped(
            source: BufferedSource,
            maxBytes: Long,
        ): ByteArray {
            if (source.request(maxBytes + 1)) throw IOException("link is larger than $maxBytes bytes")
            return source.readByteArray()
        }
    }
}
