package net.fenki.otp_sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import android.util.Log
import net.fenki.otp_sync.component.Cipher
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import net.fenki.otp_sync.component.MyHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "SmsReceiver"
    }

    private val receiverScope = CoroutineScope(Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "SMS received with action: ${intent.action}")

        if (intent.action == "android.provider.Telephony.SMS_RECEIVED") {
            val bundle = intent.extras
            if (bundle != null) {
                val pdus = bundle.get("pdus") as Array<*>?
                Log.d(TAG, "PDUs size: ${pdus?.size}")

                pdus?.forEach { pdu ->
                    try {
                        val format = bundle.getString("format", "3gpp")
                        val smsMessage =
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                SmsMessage.createFromPdu(pdu as ByteArray, format)
                            } else {
                                @Suppress("DEPRECATION")
                                SmsMessage.createFromPdu(pdu as ByteArray)
                            }

                        val sender = smsMessage.displayOriginatingAddress
                        val messageBody = smsMessage.messageBody
                        val subscriptionId = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1) {
                            bundle.getInt("subscription", -1)
                        } else {
                            -1
                        }
                        Log.d(TAG, "SMS from: $sender")
                        Log.d(TAG, "Message: $messageBody") 
                        Log.d(TAG, "Received on SIM: $subscriptionId")
                        sendToBackend(context, "SMS", """
                            SMS from: $sender
                            Received on SIM: $subscriptionId
                            $messageBody
                        """.trimIndent())
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing SMS", e)
                    }
                }
            } else {
                Log.w(TAG, "Received null bundle with SMS intent")
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

     fun sendToBackend(context: Context, type: String, data: String) {
        val dataStore = DataStoreRepository(context)
        
        receiverScope.launch {
            val backendUrl = validateAndFixUrl(dataStore.backendUrl.first())
            val secret = dataStore.secret.first()
//            if (secret == "") secret =
            val notifyBackend = dataStore.notifyBackend.first()
            val ids = dataStore.ids.first()

            if (!notifyBackend) {
                Log.i(TAG, "Backend notifications disabled, skipping")
                return@launch
            }

            if (backendUrl.isNotEmpty()) {
                val client = MyHttpClient(context).create()
                val cypher = Cipher(secret)
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
}