package com.sanchr.proto.media

import kotlin.test.Test
import kotlin.test.assertEquals
import sanchr.media.Media

class MediaMappersTest {
    @Test
    fun `upload request carries size, type, hash and purpose on the wire`() {
        val proto =
            GetUploadUrlRequest(
                fileSize = 1234L,
                contentType = "image/jpeg",
                sha256Hex = "ab" + "cd".repeat(31),
                purpose = MediaPurpose.AVATAR,
            ).toProto()

        assertEquals(1234L, proto.fileSize)
        assertEquals("image/jpeg", proto.contentType)
        assertEquals("ab" + "cd".repeat(31), proto.sha256Hash)
        assertEquals(Media.MediaPurpose.MEDIA_PURPOSE_AVATAR, proto.purpose)
    }

    @Test
    fun `attachment is the default purpose, matching the wire's zero value`() {
        val proto = GetUploadUrlRequest(fileSize = 1L, contentType = "application/octet-stream").toProto()
        assertEquals(Media.MediaPurpose.MEDIA_PURPOSE_ATTACHMENT, proto.purpose)
        assertEquals(0, proto.purposeValue)
    }

    @Test
    fun `presigned response maps every field`() {
        val model =
            Media.PresignedUrlResponse
                .newBuilder()
                .setUrl("https://bucket/put?sig=1")
                .setMediaId("m-1")
                .setExpiresIn(900)
                .setDisplayUrl("/avatars/m-1.jpg")
                .build()
                .toModel()

        assertEquals(PresignedUrlResponse("https://bucket/put?sig=1", "m-1", 900L, "/avatars/m-1.jpg"), model)
    }
}
