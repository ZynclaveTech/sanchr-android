package com.sanchr.core.network.media

import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.buffer

/** PUTs bytes to a presigned object-storage URL. */
interface BlobStore {
    /**
     * @throws BlobStoreException when the store answers with a non-2xx status.
     * @throws IOException on transport failure.
     */
    suspend fun put(
        url: String,
        bytes: ByteArray,
        contentType: String,
        headers: Map<String, String> = emptyMap(),
        /** Called as the body is written, with bytes sent so far and the total. */
        onProgress: ((sent: Long, total: Long) -> Unit)? = null,
    )

    /**
     * GETs a presigned object-storage URL into memory.
     * @throws BlobStoreException on a non-2xx status.
     * @throws IOException on transport failure.
     */
    suspend fun get(url: String): ByteArray
}

class BlobStoreException(
    val statusCode: Int,
    message: String,
) : IOException(message)

@Singleton
class OkHttpBlobStore
    @Inject
    constructor() : BlobStore {
        private val client by lazy {
            OkHttpClient
                .Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(2, TimeUnit.MINUTES)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
        }

        override suspend fun put(
            url: String,
            bytes: ByteArray,
            contentType: String,
            headers: Map<String, String>,
            onProgress: ((sent: Long, total: Long) -> Unit)?,
        ) = withContext(Dispatchers.IO) {
            val body = bytes.toRequestBody(contentType.toMediaTypeOrNull())
            val request =
                Request
                    .Builder()
                    .url(url)
                    .put(if (onProgress == null) body else ProgressRequestBody(body, onProgress))
                    .apply { headers.forEach { (name, value) -> header(name, value) } }
                    .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val body =
                        response.body
                            ?.string()
                            .orEmpty()
                            .take(MAX_ERROR_BODY)
                    throw BlobStoreException(response.code, "PUT failed: HTTP ${response.code} $body")
                }
            }
        }

        override suspend fun get(url: String): ByteArray =
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
                        if (!response.isSuccessful) {
                            val body =
                                response.body
                                    ?.string()
                                    .orEmpty()
                                    .take(MAX_ERROR_BODY)
                            throw BlobStoreException(response.code, "GET failed: HTTP ${response.code} $body")
                        }
                        response.body?.bytes() ?: ByteArray(0)
                    }
            }

        private companion object {
            const val MAX_ERROR_BODY = 512
        }
    }

/**
 * Wraps a request body to report how much has actually reached the socket.
 * Progress is the bytes written by OkHttp, not an estimate: an upload that
 * stalls stops reporting rather than sliding to full.
 */
internal class ProgressRequestBody(
    private val delegate: RequestBody,
    private val onProgress: (sent: Long, total: Long) -> Unit,
) : RequestBody() {
    override fun contentType(): MediaType? = delegate.contentType()

    override fun contentLength(): Long = delegate.contentLength()

    override fun writeTo(sink: BufferedSink) {
        val total = contentLength()
        val counting =
            object : ForwardingSink(sink) {
                private var sent = 0L

                override fun write(
                    source: Buffer,
                    byteCount: Long,
                ) {
                    super.write(source, byteCount)
                    sent += byteCount
                    onProgress(sent, total)
                }
            }
        val buffered = counting.buffer()
        delegate.writeTo(buffered)
        buffered.flush()
    }
}
