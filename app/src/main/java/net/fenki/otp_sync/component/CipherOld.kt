package net.fenki.otp_sync.component

import java.security.SecureRandom
import java.util.*
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class CipherOld(private val secretPhrase: String) {
    companion object {
        private const val ALGORITHM = "AES/CBC/NoPadding"
        private const val KEY_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val ITERATIONS = 65536
        private const val KEY_LENGTH = 256
    }

    private fun deriveKey(salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance(KEY_ALGORITHM)
        val spec = PBEKeySpec(secretPhrase.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val secretKey = factory.generateSecret(spec)
        return SecretKeySpec(secretKey.encoded, "AES")
    }

    fun encrypt(plaintext: String): String {
        val salt = ByteArray(16).apply { SecureRandom().nextBytes(this) }
        val iv = ByteArray(16).apply { SecureRandom().nextBytes(this) }
        val key = deriveKey(salt)

        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val combined = salt + iv + ciphertext
        return android.util.Base64.encodeToString(combined, android.util.Base64.NO_WRAP)
    }

    fun decrypt(encryptedText: String): String {
        val combined = android.util.Base64.decode(encryptedText, android.util.Base64.NO_WRAP)
        val salt = combined.sliceArray(0 until 16)
        val iv = combined.sliceArray(16 until 32)
        val ciphertext = combined.sliceArray(32 until combined.size)

        val key = deriveKey(salt)

        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))

        return try {
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: javax.crypto.BadPaddingException) {
            "Decryption failed: Invalid padding or corrupted data."
        } catch (e: Exception) {
            "Decryption failed: ${e.message}"
        }
    }
}