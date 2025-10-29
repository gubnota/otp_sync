package net.fenki.otp_sync
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.IOException
import net.fenki.otp_sync.component.MyHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.provider.CallLog
import android.provider.Telephony
import android.telephony.TelephonyCallback
import net.fenki.otp_sync.utils.MessageHashHelper
import net.fenki.otp_sync.utils.NotifiedCacheHelper

class SmsCallForegroundService : Service() {

    private lateinit var telephonyManager: TelephonyManager
    private lateinit var dataStore: DataStoreRepository
    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private val pollHandler = Handler(Looper.getMainLooper())
    private val pollInterval = 60_000L // 1 minute

    companion object {
        @Volatile
        var isServiceRunning = false
        // Change from call/SMS specific caches to hash-based cache
        val sentMessageHashes = mutableMapOf<String, Long>()
        private val notifiedSms = mutableMapOf<String, Long>()
        private val notifiedCalls = mutableMapOf<String, Long>()

        fun start(context: Context) {
            val intent = Intent(context, SmsCallForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SmsCallForegroundService::class.java))
        }

        fun restart(context: Context) {
            stop(context)
            start(context)
        }
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            pollCallLogAndSms()
            pollHandler.postDelayed(this, pollInterval)
        }
    }

    override fun onCreate() {
        super.onCreate()
        dataStore = DataStoreRepository(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("SmsCallForegroundSvc", "onStartCommand")
        startForeground(1, createNotification())
        pollHandler.post(pollRunnable)
        setupCallListener()
        isServiceRunning = true
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        pollHandler.removeCallbacks(pollRunnable)
        Log.d("SmsCallForegroundSvc", "Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        val channelId = "SmsCallForegroundServiceChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "SMS and Call Tracker",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Tracks incoming calls and sends them to backend"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("SMS and Call Tracker")
            .setContentText("Monitoring calls and SMS...")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun setupCallListener() {
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyManager.registerTelephonyCallback(
                mainExecutor,
                object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        handleCallState(state)
                    }
                }
            )
        } else {
            @Suppress("DEPRECATION")
            telephonyManager.listen(object : PhoneStateListener() {
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    handleCallState(state)
                }
            }, PhoneStateListener.LISTEN_CALL_STATE)
        }
    }
    private fun queryCallLogAndNotify() {
        val now = System.currentTimeMillis()
        val cursor = contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.PHONE_ACCOUNT_ID, CallLog.Calls.DATE),
            "${CallLog.Calls.TYPE} = ? AND ${CallLog.Calls.NEW} = 1",
            arrayOf(CallLog.Calls.INCOMING_TYPE.toString()),
            "${CallLog.Calls.DATE} DESC"
        )

        cursor?.use {
            if (it.moveToFirst()) {
                val id = it.getString(0)
                val number = it.getString(1) ?: "unknown"
                val simId = it.getString(2) ?: "unknown"
                val date = it.getLong(3)

                if (NotifiedCacheHelper.shouldNotify(notifiedCalls, id, date)) {
                    val formatted = NotifiedCacheHelper.formatTimestamp(date)
                    val message = """
                    📞 Incoming call
                    Number: $number
                    SIM: $simId
                    Time: $formatted
                """.trimIndent()
                    sendToBackend("Call", message)
                }
            }
        }
    }
    private fun handleCallState(state: Int) {
        if (state == TelephonyManager.CALL_STATE_RINGING) {
            // Only use the delayed handler
            Handler(Looper.getMainLooper()).postDelayed({
                queryCallLogAndNotify()
            }, 2000) // 2 seconds

            // REMOVE THIS ENTIRE BLOCK - it's causing the duplicate:
            /*
            val cursor = contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls._ID,
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.PHONE_ACCOUNT_ID,
                    CallLog.Calls.DATE
                ),
                "${CallLog.Calls.TYPE} = ?",
                arrayOf(CallLog.Calls.INCOMING_TYPE.toString()),
                "${CallLog.Calls.DATE} DESC"
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val id = it.getString(0)
                    val number = it.getString(1) ?: "unknown"
                    val simId = it.getString(2) ?: "unknown"
                    val date = it.getLong(3)
                    val cacheKey = "$number-$date"
                    if (NotifiedCacheHelper.shouldNotify(notifiedCalls, id, date)) {
                        val formatted = NotifiedCacheHelper.formatTimestamp(date)
                        val message = """
                            📞 Incoming call
                            Number: $number
                            SIM: $simId
                            Time: $formatted
                        """.trimIndent()
                        sendToBackend("Call", message)
                    }
                }
            }
            */
        }
    }

    private fun pollCallLogAndSms() {
        val now = System.currentTimeMillis()

        val callCursor = contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.DATE),
            null, null, "${CallLog.Calls.DATE} DESC"
        )
        callCursor?.use {
            while (it.moveToNext()) {
                val id = it.getString(0)
                val number = it.getString(1) ?: "unknown"
                val date = it.getLong(2)

                if (NotifiedCacheHelper.shouldNotify(notifiedCalls, id, date)) {
                    val message = """
                        📞 Missed or recent call
                        From: $number
                        Time: ${NotifiedCacheHelper.formatTimestamp(date)}
                    """.trimIndent()
                    sendToBackend("Call", message)
                }
            }
        }

        val smsCursor = contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms._ID, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.ADDRESS, Telephony.Sms.READ),
            null, null, "${Telephony.Sms.DATE} DESC"
        )
        smsCursor?.use {
            while (it.moveToNext()) {
                val id = it.getString(0)
                val body = it.getString(1) ?: "no content"
                val date = it.getLong(2)
                val sender = it.getString(3) ?: "unknown"
                val read = it.getInt(4)

                if (NotifiedCacheHelper.shouldNotify(notifiedSms, id, date)) {
                    val formatted = NotifiedCacheHelper.formatTimestamp(date)
                    val message = """
                        📩 Incoming SMS
                        From: $sender
                        Time: $formatted
                        Body: $body
                    """.trimIndent()
                    sendToBackend("SMS", message)
                }
                // ✅ Mark SMS as read after 10 minutes
                if (now - date > 10 * 60 * 1000L && read == 0) {
                    val values = ContentValues().apply { put(Telephony.Sms.READ, 1) }
                    val updated = contentResolver.update(
                        Telephony.Sms.Inbox.CONTENT_URI,
                        values,
                        "${Telephony.Sms._ID}=?",
                        arrayOf(id)
                    )
                    Log.d("Cleanup", "Marked SMS $id as read ($updated rows)")
                }
                // 🧹 Delete SMS older than 1 hour
                if (now - date > NotifiedCacheHelper.MAX_AGE_MILLIS) {
                    val deleted = contentResolver.delete(
                        Telephony.Sms.Inbox.CONTENT_URI,
                        "${Telephony.Sms._ID}=?",
                        arrayOf(id)
                    )
                    Log.d("Cleanup", "Deleted old SMS $id ($deleted rows)")
                }

                if (NotifiedCacheHelper.isExpired(date)) {
                    contentResolver.delete(
                        Telephony.Sms.Inbox.CONTENT_URI,
                        "${Telephony.Sms._ID}=?",
                        arrayOf(id)
                    )
                }
            }
        }

        NotifiedCacheHelper.cleanOldEntries(notifiedCalls)
        NotifiedCacheHelper.cleanOldEntries(notifiedSms)
    }


    private fun sendToBackend(type: String, data: String) {
        serviceScope.launch {
            // Generate hash for deduplication
            val messageHash = MessageHashHelper.generateHash(type, data)

            // Check if we should send (not sent today)
            if (!MessageHashHelper.shouldSendMessage(sentMessageHashes, messageHash)) {
                Log.d("SmsCallForegroundSvc", "Duplicate message detected, skipping: $messageHash")
                return@launch
            }

            val backendUrl = validateAndFixUrl(dataStore.backendUrl.first())
            val secret = dataStore.secret.first()
            val notifyBackend = dataStore.notifyBackend.first()
            val ids = dataStore.ids.first()

            if (!notifyBackend || backendUrl.isEmpty()) return@launch

            val client = MyHttpClient(this@SmsCallForegroundService).create()
            val encryptedData = "$ids\n$type\n$data"

            val request = Request.Builder()
                .url(backendUrl)
                .header("X-Auth-Key", secret)
                .post(encryptedData.toRequestBody("text/plain".toMediaType()))
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("SmsCallForegroundSvc", "Failed to send data", e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!it.isSuccessful) {
                            Log.e("SmsCallForegroundSvc", "Failed: ${it.code}")
                        } else {
                            Log.i("SmsCallForegroundSvc", "Data sent successfully (hash: $messageHash)")
                        }
                    }
                }
            })
        }
    }
}

    private fun validateAndFixUrl(url: String): String {
        var fixedUrl = url.trim()
        if (!fixedUrl.startsWith("http://") && !fixedUrl.startsWith("https://")) {
            fixedUrl = if (fixedUrl.startsWith("192.168.") || fixedUrl == "localhost" || fixedUrl == "127.0.0.1") {
                "http://$fixedUrl"
            } else {
                "https://$fixedUrl"
            }
        }
        return if (!fixedUrl.endsWith("/")) "$fixedUrl/receive_data" else "$fixedUrl/receive_data"
    }
