// package net.fenki.otp_sync.domain.managers

// import android.content.Context
// import io.github.cdimascio.dotenv.dotenv
// import java.io.File

// object EnvironmentManager {
//     private var envVars: Map<String, String> = emptyMap()
    
//     fun init(context: Context) {
//         try {
//             // Create .env file if it doesn't exist
//             val envFile = File(context.filesDir, ".env")
//             if (!envFile.exists()) {
//                 envFile.createNewFile()
//                 // Add default values
//                 envFile.writeText("""
//                     BACKEND_URL=https://example.com
//                     API_KEY=your_default_key
//                     DEBUG_MODE=false
//                 """.trimIndent())
//             }

//             // Load environment variables
//             val dotenv = dotenv {
//                 directory = context.filesDir.absolutePath
//                 filename = ".env"
//             }
            
//             envVars = dotenv.entries().associate { it.key to it.value }
//         } catch (e: Exception) {
//             e.printStackTrace()
//             // Fallback to default values if file operations fail
//             envVars = mapOf(
//                 "BACKEND_URL" to "https://example.com",
//                 "API_KEY" to "your_default_key",
//                 "DEBUG_MODE" to "false"
//             )
//         }
//     }

//     fun get(key: String): String {
//         return envVars[key] ?: ""
//     }

//     fun set(context: Context, key: String, value: String) {
//         val envFile = File(context.filesDir, ".env")
//         val currentContent = if (envFile.exists()) envFile.readLines() else emptyList()
        
//         val newContent = currentContent.map { line ->
//             if (line.startsWith("$key=")) {
//                 "$key=$value"
//             } else {
//                 line
//             }
//         }.toMutableList()

//         if (!currentContent.any { it.startsWith("$key=") }) {
//             newContent.add("$key=$value")
//         }

//         envFile.writeText(newContent.joinToString("\n"))
//         init(context) // Reload variables
//     }

//     fun getBackendUrl() = get("BACKEND_URL")
//     fun getApiKey() = get("API_KEY")
//     fun isDebugMode() = get("DEBUG_MODE").toBoolean()
// } 