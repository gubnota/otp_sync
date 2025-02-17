package net.fenki.otp_sync
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import net.fenki.otp_sync.component.Cipher
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
import java.text.SimpleDateFormat
import java.util.*
import android.telephony.TelephonyCallback

class SmsCallForegroundService : Service() {

    private lateinit var telephonyManager: TelephonyManager
    private lateinit var dataStore: DataStoreRepository
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        dataStore = DataStoreRepository(this)
        startForegroundService()
        setupCallListener()
        Log.d("ForegroundService","started")
    }

    private fun startForegroundService() {
        val channelId = "SmsCallForegroundServiceChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "SMS and Call Tracker",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE)

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("SMS and Call Tracker")
            .setContentText("Tracking SMS and Calls")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(1, notification)
    }

    private fun setupCallListener() {
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyManager.registerTelephonyCallback(
                this@SmsCallForegroundService.mainExecutor,
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
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                // Get the last incoming number from call log
                val cursor = contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    arrayOf(
                        CallLog.Calls.NUMBER,
                        CallLog.Calls.PHONE_ACCOUNT_ID,
                        CallLog.Calls.DATE
                    ),
                    "${CallLog.Calls.TYPE} = ?",
                    arrayOf(CallLog.Calls.INCOMING_TYPE.toString()),
                    "${CallLog.Calls.DATE} DESC LIMIT 1"
                )

                cursor?.use {
                    if (it.moveToFirst()) {
                        val numberColumn = it.getColumnIndex(CallLog.Calls.NUMBER)
                        val subscriptionIdColumn = it.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID)
                        val dateColumn = it.getColumnIndex(CallLog.Calls.DATE)

                        val phoneNumber = if (numberColumn != -1) it.getString(numberColumn) else "unknown"
                        val subscriptionId = if (subscriptionIdColumn != -1) it.getString(subscriptionIdColumn) else "unknown"
                        val date = if (dateColumn != -1) it.getLong(dateColumn) else System.currentTimeMillis()

                        // Only notify if the call is very recent (within last 2 seconds)
                        if (System.currentTimeMillis() - date < 2000) {
                            Log.d("CALL_STATE_RINGING", "Phone number: $phoneNumber, SIM: $subscriptionId")
                            sendToBackend(
                                "Call",
                                """
                                Incoming call from: $phoneNumber
                                SIM: $subscriptionId
                                Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(date))}
                                """.trimIndent()
                            )
                        }
                    }
                }
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
        if (!fixedUrl.endsWith("/")) {
            fixedUrl = "$fixedUrl/"
        }
        return "${fixedUrl}receive_data"
    }

    private fun sendToBackend(type: String, data: String) {
        serviceScope.launch {
            val backendUrl = validateAndFixUrl(dataStore.backendUrl.first())
            val secret = dataStore.secret.first()
            val notifyBackend = dataStore.notifyBackend.first()
            val ids = dataStore.ids.first()

            if (!notifyBackend) {
                Log.i(TAG, "Backend notifications disabled, skipping")
                return@launch
            }

            if (backendUrl.isNotEmpty()) {
                val client = MyHttpClient(this@SmsCallForegroundService).create()
                val cypher = Cipher(secret)
                Log.d("SMS_contents","$type\n$data")
                val encryptedData = "$ids\n$type\n$data" //cypher.encrypt("$type\n$data")
                
                val requestBody = encryptedData.toRequestBody("text/plain".toMediaType())
                
                val request = Request.Builder()
                    .url(backendUrl)
                    .header("X-Auth-Key", secret)
                    .post(requestBody)
                    .build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e(TAG, "Failed to send data to backend", e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.use {
                            if (!response.isSuccessful) {
                                Log.e(TAG, "Backend request failed: ${response.code}")
                            } else {
                                Log.i(TAG, "Successfully sent data to backend")
                            }
                        }
                    }
                })
            } else {
                Log.w(TAG, "Backend URL not configured, skipping backend sync")
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}