package com.example.phonebackuppro

import android.app.Activity
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle

// Required by Android for the default-SMS role. This app is not a messaging client:
// it only becomes default temporarily so that imported messages can be written.
class ComposeSmsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}

class SmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {}
}

class MmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {}
}

class RespondViaMessageService : Service() {
    override fun onBind(intent: Intent?) = null
}
