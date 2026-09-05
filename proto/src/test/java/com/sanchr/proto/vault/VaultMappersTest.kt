package com.sanchr.proto.vault

import com.google.protobuf.ByteString
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import sanchr.vault.Vault

class VaultMappersTest {
    @Test
    fun `create request carries only the id, media id, opaque metadata and expiry`() {
        val proto = CreateVaultItemRequest("v-1", "m-1", byteArrayOf(9, 8, 7), expiresAt = 123L).toProto()
        assertEquals("v-1", proto.vaultItemId)
        assertEquals("m-1", proto.mediaId)
        assertEquals(ByteString.copyFrom(byteArrayOf(9, 8, 7)), proto.encryptedMetadata)
        assertEquals(123L, proto.expiresAt)
    }

    @Test
    fun `item maps every field`() {
        val model =
            Vault.VaultItem
                .newBuilder()
                .setVaultItemId("v-1")
                .setMediaId("m-1")
                .setEncryptedMetadata(ByteString.copyFrom(byteArrayOf(1)))
                .setCreatedAt(5L)
                .setExpiresAt(6L)
                .build()
                .toModel()
        assertEquals("v-1", model.vaultItemId)
        assertEquals("m-1", model.mediaId)
        assertContentEquals(byteArrayOf(1), model.encryptedMetadata)
        assertEquals(5L, model.createdAt)
        assertEquals(6L, model.expiresAt)
    }
}
