package com.procam.camera.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Streaming AES-256-GCM encryption. Format: 12-byte IV followed by authenticated ciphertext. */
class EncryptionManager {
    private val alias = "procam_vault_aes_v2"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }
    fun encrypt(input: File, output: File) {
        output.parentFile?.mkdirs()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        FileOutputStream(output).use { raw ->
            raw.write(cipher.iv)
            CipherOutputStream(raw, cipher).use { encrypted -> FileInputStream(input).use { it.copyTo(encrypted) } }
        }
    }
    fun encrypt(input: java.io.InputStream, output: File) {
        output.parentFile?.mkdirs()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        FileOutputStream(output).use { raw ->
            raw.write(cipher.iv)
            CipherOutputStream(raw, cipher).use { encrypted -> input.use { it.copyTo(encrypted) } }
        }
    }
    fun decrypt(input: File, output: File) {
        require(input.length() > 28L) { "Invalid encrypted vault file" }
        output.parentFile?.mkdirs()
        FileInputStream(input).use { raw ->
            val iv = ByteArray(12); require(raw.read(iv) == 12) { "Invalid vault header" }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
            CipherInputStream(raw, cipher).use { decrypted -> FileOutputStream(output).use { decrypted.copyTo(it) } }
        }
    }
}
