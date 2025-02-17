package net.fenki.otp_sync.component

import android.util.Base64
import android.util.Log
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class Cypher(private val secret: String) {
    private val secretKey = secret.trim().let {
        if (it.isEmpty()) byteArrayOf() else it.toByteArray().copyOf(32)
    }

    fun encryptData(data: String): String {
        if (secretKey.isEmpty()) return data
    
        try {
            val nonce = ByteArray(12)
            SecureRandom().nextBytes(nonce)
    
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = SecretKeySpec(secretKey, "AES")
            val paramSpec = GCMParameterSpec(128, nonce) // 128-bit authentication tag
    
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, paramSpec)
            val encrypted = cipher.doFinal(data.toByteArray())
    
            // Combine nonce, encrypted data, and authentication tag
            val tag = cipher.getIV() // Get the authentication tag
            Log.d("Cypher", "Tag size: ${tag.size}, value: ${Base64.encodeToString(tag, Base64.NO_WRAP)}")
            return Base64.encodeToString(nonce + encrypted + tag, Base64.NO_WRAP)
        } catch (e: Exception) {
            return data // Return original data if encryption fails
        }
    }

    fun decryptData(encryptedData: String): String {
        if (secretKey.isEmpty()) return encryptedData

        try {
            val combined = Base64.decode(encryptedData, Base64.NO_WRAP)
            if (combined.size <= 12) return encryptedData

            // Split nonce and encrypted data
            val nonce = combined.slice(0..11).toByteArray()
            val encrypted = combined.slice(12 until combined.size).toByteArray()

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = SecretKeySpec(secretKey, "AES")
            val paramSpec = GCMParameterSpec(128, nonce) // 128-bit authentication tag

            cipher.init(Cipher.DECRYPT_MODE, keySpec, paramSpec)
            return String(cipher.doFinal(encrypted))
        } catch (e: Exception) {
            return encryptedData // Return original data if decryption fails
        }
    }
}