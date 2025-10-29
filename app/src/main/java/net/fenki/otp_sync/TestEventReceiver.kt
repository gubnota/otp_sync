package net.fenki.otp_sync


import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class TestEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "net.fenki.otp_sync.TEST_SMS" -> {
                val sender = intent.getStringExtra("sender") ?: "+15551234567"
                val message = intent.getStringExtra("message") ?: "Test SMS"
                val simId = intent.getIntExtra("sim", 1)

                val smsReceiver = SmsReceiver()
                smsReceiver.sendToBackend(context, "SMS", """
                    SMS from: $sender
                    Received on SIM: $simId
                    $message
                """.trimIndent())
            }

            "net.fenki.otp_sync.TEST_CALL" -> {
                val number = intent.getStringExtra("number") ?: "+15551234567"
                val simId = intent.getIntExtra("sim", 1)

                val message = """
                    📞 Incoming call
                    Number: $number
                    SIM: $simId
                    Time: ${System.currentTimeMillis()}
                """.trimIndent()

                val smsReceiver = SmsReceiver()
                smsReceiver.sendToBackend(context, "Call", message)
            }
        }
    }
}
