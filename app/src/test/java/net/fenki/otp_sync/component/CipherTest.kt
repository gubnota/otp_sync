package net.fenki.otp_sync.component
// Unit Tests (using JUnit 5)
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CipherTest {
    private val cipher = Cipher("my secret phrase")

    @Test
    fun testEncryptDecrypt() {
        val originalText = "Hello, World!"
        val encrypted = cipher.encrypt(originalText)
        val decrypted = cipher.decrypt(encrypted)
        assertEquals(originalText, decrypted)
    }

    @Test
    fun testLongText() {
        val originalText = "This is a much longer text that spans multiple lines.\n".repeat(10)
        val encrypted = cipher.encrypt(originalText)
        val decrypted = cipher.decrypt(encrypted)
        assertEquals(originalText, decrypted)
    }

    @Test
    fun testDifferentKeys() {
        val originalText = "Secret message"
        val cipher1 = Cipher("key1")
        val cipher2 = Cipher("key2")
        val encrypted = cipher1.encrypt(originalText)
        val decrypted = cipher2.decrypt(encrypted)
//        assertNotEquals(originalText, decrypted)
        assertThrows<Exception> {
            cipher2.decrypt(encrypted)
        }
    }

    @Test
    fun testEmptyString() {
        val originalText = ""
        val encrypted = cipher.encrypt(originalText)
        val decrypted = cipher.decrypt(encrypted)
        assertEquals(originalText, decrypted)
    }
}