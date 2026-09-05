package com.sanchr.core.network.link

import android.content.Context
import android.util.Log
import android.util.LruCache
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** A link's card: title, the site, and its picture if it had one small enough. */
class LinkPreview(
    val url: String,
    val title: String?,
    val domain: String,
    val imageBytes: ByteArray?,
)

/**
 * Fetches and caches a page's Open Graph card, as iOS `LinkPreviewService`:
 * memory, then disk, then one network fetch per URL with concurrent
 * requests coalesced; a URL that failed is not retried until restart.
 * The site sees this device's address, which is why the feature has a
 * privacy toggle; only ever called when that toggle is on.
 */
@Singleton
class LinkPreviewFetcher internal constructor(
    private val cacheDir: File,
    private val client: OkHttpClient,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(
        File(context.cacheDir, CACHE_DIR),
        OkHttpClient
            .Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build(),
    )

    private val memory = LruCache<String, LinkPreview>(MEMORY_ENTRIES)
    private val failed = mutableSetOf<String>()
    private val inFlight = mutableMapOf<String, Deferred<LinkPreview?>>()
    private val lock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun preview(url: String): LinkPreview? {
        memory.get(url)?.let { return it }
        val job =
            lock.withLock {
                if (url in failed) return null
                inFlight[url] ?: scope.async { load(url) }.also { inFlight[url] = it }
            }
        return try {
            job.await()
        } finally {
            lock.withLock { inFlight.remove(url) }
        }
    }

    private suspend fun load(url: String): LinkPreview? {
        readDisk(url)?.let {
            memory.put(url, it)
            return it
        }
        val fetched =
            try {
                fetch(url)
            } catch (e: IOException) {
                Log.d(TAG, "preview fetch failed for $url: ${e.message}")
                null
            }
        if (fetched == null) {
            lock.withLock { failed += url }
            return null
        }
        memory.put(url, fetched)
        writeDisk(url, fetched)
        return fetched
    }

    private suspend fun fetch(url: String): LinkPreview? =
        withContext(Dispatchers.IO) {
            val html =
                get(url) { response ->
                    val type = response.header("Content-Type").orEmpty()
                    if (!type.contains("html", ignoreCase = true)) return@get null
                    response.body?.source()?.let { source ->
                        source.request(MAX_HTML_BYTES)
                        source.buffer.readUtf8(minOf(source.buffer.size, MAX_HTML_BYTES))
                    }
                } ?: return@withContext null
            val parsed = OpenGraphParser.parse(html, url)
            val image =
                parsed.imageUrl?.let { imageUrl ->
                    runCatching {
                        get(imageUrl) { response ->
                            val type = response.header("Content-Type").orEmpty()
                            if (!type.startsWith("image/", ignoreCase = true)) return@get null
                            response.body?.source()?.let { source ->
                                source.request(MAX_IMAGE_BYTES + 1)
                                if (source.buffer.size > MAX_IMAGE_BYTES) null else source.buffer.readByteArray()
                            }
                        }
                    }.getOrNull()
                }
            LinkPreview(url = url, title = parsed.title, domain = domainOf(url), imageBytes = image)
        }

    private inline fun <T> get(
        url: String,
        read: (Response) -> T?,
    ): T? {
        val request =
            Request
                .Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,image/*")
                .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) null else read(response)
        }
    }

    // --- Disk cache: <sha256>.title / .domain / .img ---

    private fun cacheFile(
        url: String,
        ext: String,
    ): File = File(cacheDir, sha256(url) + ext)

    private fun readDisk(url: String): LinkPreview? {
        val domainFile = cacheFile(url, ".domain")
        if (!domainFile.isFile) return null
        val fresh = System.currentTimeMillis() - domainFile.lastModified() < DISK_TTL_MS
        if (!fresh) return null
        val title = cacheFile(url, ".title").takeIf { it.isFile }?.readText()?.takeIf { it.isNotEmpty() }
        val image = cacheFile(url, ".img").takeIf { it.isFile }?.readBytes()
        return LinkPreview(url, title, domainFile.readText(), image)
    }

    private fun writeDisk(
        url: String,
        preview: LinkPreview,
    ) {
        runCatching {
            cacheDir.mkdirs()
            cacheFile(url, ".domain").writeText(preview.domain)
            cacheFile(url, ".title").writeText(preview.title.orEmpty())
            preview.imageBytes?.let { cacheFile(url, ".img").writeBytes(it) }
        }
    }

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        private const val TAG = "LinkPreviewFetcher"
        private const val CACHE_DIR = "link_previews"
        private const val TIMEOUT_SECONDS = 10L
        private const val MEMORY_ENTRIES = 300
        private const val MAX_HTML_BYTES = 512L * 1024
        private const val MAX_IMAGE_BYTES = 2L * 1024 * 1024
        private const val DISK_TTL_MS = 7L * 24 * 60 * 60 * 1000
        private const val USER_AGENT = "Mozilla/5.0 (Android) SanchrLinkPreview/1.0"

        fun domainOf(url: String): String =
            url
                .substringAfter("://")
                .substringBefore('/')
                .substringBefore('?')
                .removePrefix("www.")
    }
}
