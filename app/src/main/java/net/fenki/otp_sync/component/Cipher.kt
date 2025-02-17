package net.fenki.otp_sync.component
import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class Cipher(private val secretPhrase: String) {
    companion object {
        private const val ALGORITHM = "AES/GCM/NoPadding"
        private const val KEY_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val ITERATIONS = 65536
        private const val KEY_LENGTH = 256
        private const val TAG_LENGTH = 128  // 128-bit authentication tag
        private const val SALT_LENGTH = 16
        private const val NONCE_LENGTH = 12
    }

    private fun deriveKey(salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance(KEY_ALGORITHM)
        val spec = PBEKeySpec(secretPhrase.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    fun encrypt(plaintext: String): String {
        val salt = ByteArray(SALT_LENGTH).apply { SecureRandom().nextBytes(this) }
        val nonce = ByteArray(NONCE_LENGTH).apply { SecureRandom().nextBytes(this) }
        val key = deriveKey(salt)

        val cipher = Cipher.getInstance(ALGORITHM).apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH, nonce))
        }
        val ciphertextWithTag = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        // Split into ciphertext and authentication tag
        val ciphertext = ciphertextWithTag.copyOfRange(0, ciphertextWithTag.size - 16)
        val tag = ciphertextWithTag.copyOfRange(ciphertextWithTag.size - 16, ciphertextWithTag.size)

        val combined = salt + nonce + ciphertext + tag
        return android.util.Base64.encodeToString(combined, android.util.Base64.NO_WRAP)
    }

    fun decrypt(encryptedText: String): String {
        val combined = Base64.decode(encryptedText, Base64.NO_WRAP)
        val salt = combined.copyOfRange(0, SALT_LENGTH)
        val nonce = combined.copyOfRange(SALT_LENGTH, SALT_LENGTH + NONCE_LENGTH)
        val ciphertext = combined.copyOfRange(
            SALT_LENGTH + NONCE_LENGTH,
            combined.size - 16
        )
        val tag = combined.copyOfRange(combined.size - 16, combined.size)

        val key = deriveKey(salt)
        val cipher = Cipher.getInstance(ALGORITHM).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH, nonce))
        }

        // Recombine ciphertext and tag
        val ciphertextWithTag = ciphertext + tag

        return try {
            String(cipher.doFinal(ciphertextWithTag), Charsets.UTF_8)
        } catch (e: Exception) {
            "Decryption failed: ${e.message}"
        }
    }
}
