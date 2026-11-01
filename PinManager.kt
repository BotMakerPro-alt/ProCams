package com.procam.camera.security

import android.content.Context
import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Stores only a salted PBKDF2 verifier, never the PIN itself. */
class PinManager(context: Context) {
    private val prefs = context.getSharedPreferences("procam_auth", Context.MODE_PRIVATE)
    fun isConfigured() = prefs.contains("pin_hash")
    fun setPin(pin: CharArray) {
        require(pin.size in 4..12 && pin.all(Char::isDigit)) { "PIN must contain 4–12 digits" }
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val hash = derive(pin, salt)
        prefs.edit().putString("pin_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("pin_hash", Base64.encodeToString(hash, Base64.NO_WRAP)).apply()
        pin.fill('\u0000')
    }
    fun verify(pin: CharArray): Boolean {
        val salt = prefs.getString("pin_salt", null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return false
        val expected = prefs.getString("pin_hash", null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return false
        val actual = derive(pin, salt); pin.fill('\u0000')
        return java.security.MessageDigest.isEqual(expected, actual)
    }
    private fun derive(pin: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin, salt, 210_000, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
    }
}
