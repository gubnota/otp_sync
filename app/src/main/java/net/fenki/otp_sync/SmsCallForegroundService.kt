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
import android.telephony.TelephonyCallback
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
import androidx.annotation.RequiresApi
import net.fenki.otp_sync.utils.NotifiedCacheHelper
import org.json.JSONArray
import org.json.JSONObject

class SmsCallForegroundService : Service() {

    private lateinit var telephonyManager: TelephonyManager
    private lateinit var dataStore: DataStoreRepository
    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private val pollHandler = Handler(Looper.getMainLooper())
    private val pollInterval = 60_000L
    private val healthCheckInterval = 600_000L

    companion object {
        @Volatile
        var isServiceRunning = false
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
            serviceScope.launch {
                pollCallLogAndSms()
            }
            pollHandler.postDelayed(this, pollInterval)
        }
    }

    private val healthCheckRunnable = object : Runnable {
        override fun run() {
            serviceScope.launch {
                checkBackendHealth()
            }
            pollHandler.postDelayed(this, healthCheckInterval)
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
        pollHandler.post(healthCheckRunnable)
        setupCallListener()
        isServiceRunning = true

        if (intent?.action == "TEST_NOTIFICATION") {
            pollHandler.postDelayed({
                serviceScope.launch {
                    sendTestNotification()
                }
            }, 1000)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        pollHandler.removeCallbacks(pollRunnable)
        pollHandler.removeCallbacks(healthCheckRunnable)
        serviceScope.launch {
            // Cleanup coroutines
        }
        Log.d("SmsCallForegroundSvc", "Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        val channelId = "SmsCallForegroundServiceChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "SMS and Call Tracker",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Tracks incoming calls and SMS messages"
                setSound(null, null)
                enableVibration(false)
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
            .setContentTitle("OTP Sync Service")
            .setContentText("Monitoring SMS and calls...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setSound(null)
            .setVibrate(null)
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

    private fun handleCallState(state: Int) {
        if (state == TelephonyManager.CALL_STATE_RINGING) {
            Handler(Looper.getMainLooper()).postDelayed({
                serviceScope.launch {
                    queryCallLogAndNotify()
                }
            }, 2000)
        }
    }

    private suspend fun queryCallLogAndNotify() {
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
                val simId = it.getString(2) ?: "1"
                val date = it.getLong(3)

                if (NotifiedCacheHelper.shouldNotify(notifiedCalls, id, date)) {
                    sendToBackendBulk("call", number, simId, date)
                }
            }
        }
    }

    private suspend fun pollCallLogAndSms() {
        val now = System.currentTimeMillis()
        val messages = JSONArray()
        val userIds = getUserIds()

        // Poll SMS
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
                    val msgObj = JSONObject().apply {
                        put("ids", userIds)
                        put("sms", body)
                    }
                    messages.put(msgObj)
                }

//                if (now - date > 10 * 60 * 1000L && read == 0) {
//                    val values = ContentValues().apply { put(Telephony.Sms.READ, 1) }
//                    contentResolver.update(
//                        Telephony.Sms.Inbox.CONTENT_URI,
//                        values,
//                        "${Telephony.Sms._ID}=?",
//                        arrayOf(id)
//                    )
//                }
//
//                if (now - date > NotifiedCacheHelper.MAX_AGE_MILLIS) {
//                    contentResolver.delete(
//                        Telephony.Sms.Inbox.CONTENT_URI,
//                        "${Telephony.Sms._ID}=?",
//                        arrayOf(id)
//                    )
//                }
            }
        }

        // Poll Incoming Calls
        val callCursor = contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.DATE, CallLog.Calls.TYPE),
            "${CallLog.Calls.TYPE} = ?",
            arrayOf(CallLog.Calls.INCOMING_TYPE.toString()),
            "${CallLog.Calls.DATE} DESC"
        )
        callCursor?.use {
            while (it.moveToNext()) {
                val id = it.getString(0)
                val number = it.getString(1) ?: "unknown"
                val date = it.getLong(2)

                if (NotifiedCacheHelper.shouldNotify(notifiedCalls, id, date)) {
                    val msgObj = JSONObject().apply {
                        put("ids", userIds)
                        put("call", true)
                        put("from", number)
                        put("to", getSimName(1))
                    }
                    messages.put(msgObj)
                }
            }
        }

        if (messages.length() > 0) {
            sendToBackend(messages.toString())
        }

        NotifiedCacheHelper.cleanOldEntries(notifiedCalls)
        NotifiedCacheHelper.cleanOldEntries(notifiedSms)
    }

    private suspend fun sendToBackendBulk(type: String, from: String, to: String, date: Long) {
        val userIds = getUserIds()
        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("ids", userIds)
                when (type) {
                    "call" -> {
                        put("call", true)
                        put("from", from)
                        put("to", getSimName(to.toIntOrNull() ?: 1))
                    }
                    "sms" -> {
                        put("sms", from)
                    }
                }
            })
        }
        sendToBackend(messages.toString())
    }

    private suspend fun sendTestNotification() {
        val userIds = getUserIds()
        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("ids", userIds)
                put("sms", "Test notification from ${getDeviceName()} at ${System.currentTimeMillis()}")
            })
        }
        sendToBackend(messages.toString())
    }

    private suspend fun sendToBackend(jsonBody: String) {
        val backendUrl = validateAndFixUrl(dataStore.backendUrl.first())
        val authKey = dataStore.secret.first()
        val notifyBackend = dataStore.notifyBackend.first()

        if (!notifyBackend || backendUrl.isEmpty() || authKey.isEmpty()) {
            Log.d("SmsCallForegroundSvc", "Backend not configured")
            return
        }

        val client = MyHttpClient(this).create()

        val request = Request.Builder()
            .url(backendUrl)
            .addHeader("X-Auth-Key", authKey)
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("SmsCallForegroundSvc", "Backend request failed: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    when (response.code) {
                        200, 207 -> Log.d("SmsCallForegroundSvc", "Messages sent: ${response.code}")
                        else -> Log.e("SmsCallForegroundSvc", "Backend error: ${response.code}")
                    }
                }
            }
        })
    }

    private suspend fun checkBackendHealth() {
        val backendUrl = validateAndFixUrl(dataStore.backendUrl.first())
        if (backendUrl.isEmpty()) return

        val client = MyHttpClient(this).create()
        val request = Request.Builder()
            .url(backendUrl.replace("/receive_data", ""))
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            @RequiresApi(Build.VERSION_CODES.M)
            override fun onFailure(call: Call, e: IOException) {
                showBackendErrorNotification("Backend unreachable")
            }

            @RequiresApi(Build.VERSION_CODES.M)
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (response.code != 200) {
                        showBackendErrorNotification("Backend error: ${response.code}")
                    } else {
                        Log.d("SmsCallForegroundSvc", "Backend is healthy")
                    }
                }
            }
        })
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun showBackendErrorNotification(message: String) {
        val channelId = "BackendErrorChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Backend Status",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Backend Error")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSound(null)
            .setVibrate(null)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(2, notification)
    }

    private suspend fun getUserIds(): String {
        return try {
            dataStore.ids.first()
        } catch (e: Exception) {
            Log.e("SmsCallForegroundSvc", "Error reading user IDs: ${e.message}")
            ""
        }
    }

    private fun getSimName(simSlot: Int): String {
        val defaultName = "SIM $simSlot"
        return defaultName
    }

    private fun getDeviceName(): String {
        return Build.MODEL
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
        return if (!fixedUrl.endsWith("/receive_data")) "$fixedUrl/receive_data" else fixedUrl
    }
}
