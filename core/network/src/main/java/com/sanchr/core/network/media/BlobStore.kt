package com.sanchr.core.network.media

import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

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
        ) = withContext(Dispatchers.IO) {
            val request =
                Request
                    .Builder()
                    .url(url)
                    .put(bytes.toRequestBody(contentType.toMediaTypeOrNull()))
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
