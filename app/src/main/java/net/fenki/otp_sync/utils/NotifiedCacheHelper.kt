package net.fenki.otp_sync.utils

object NotifiedCacheHelper {
	const val MAX_ENTRIES = 10
	const val MAX_AGE_MILLIS = 60 * 60 * 1000L // 1 hour

	fun shouldNotify(map: MutableMap<String, Long>, key: String?, date: Long): Boolean {
		val now = System.currentTimeMillis()
		if (key.isNullOrBlank()) return false
		if (map.containsKey(key)) return false
		if (now - date > MAX_AGE_MILLIS) return false

		map[key] = now
		return true
	}


	fun cleanOldEntries(map: MutableMap<String, Long>) {
		if (map.size <= MAX_ENTRIES) return
		val toRemove = map.entries
			.sortedByDescending { it.value }
			.drop(MAX_ENTRIES)
		toRemove.forEach { map.remove(it.key) }
	}

	fun isExpired(date: Long): Boolean {
		val now = System.currentTimeMillis()
		return now - date > MAX_AGE_MILLIS
	}

	fun formatTimestamp(date: Long): String {
		return java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
			.format(java.util.Date(date))
	}
}