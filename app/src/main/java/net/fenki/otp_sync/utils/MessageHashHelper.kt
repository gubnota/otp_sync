package net.fenki.otp_sync.utils

import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*

object MessageHashHelper {
    private val sdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault())

    /**
     * Generate SHA-256 hash including today's date
     * Same message on different days will have different hashes
     */
    fun generateHash(type: String, content: String): String {
        val today = sdf.format(Date())
        val input = "$type|$content|$today"

        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())

        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Check if message should be sent (not already sent today)
     * Returns true if message is new, false if duplicate
     */
    fun shouldSendMessage(
        cache: MutableMap<String, Long>,
        hash: String,
        maxEntries: Int = 100
    ): Boolean {
        val now = System.currentTimeMillis()

        // Check if already sent
        if (cache.containsKey(hash)) {
            return false
        }

        // Add to cache
        cache[hash] = now

        // Clean old entries if cache is too large
        if (cache.size > maxEntries) {
            val toRemove = cache.entries
                .sortedBy { it.value }
                .take(cache.size - maxEntries)
            toRemove.forEach { cache.remove(it.key) }
        }

        return true
    }
}
