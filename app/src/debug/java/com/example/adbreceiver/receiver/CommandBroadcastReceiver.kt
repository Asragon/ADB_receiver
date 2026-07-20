package com.example.adbreceiver.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.adbreceiver.command.CommandDispatcher

private const val TAG = "CommandBroadcastReceiver"

/**
 * Declared ONLY in src/debug/AndroidManifest.xml: it doesn't exist in the release build.
 * A fresh instance is created for every broadcast (no state of its own): onReceive() runs on
 * the app's main thread, so it's safe to write to AppUiState directly.
 */
class CommandBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val extras = intent.extras?.keySet()
            ?.joinToString { key -> "$key=${intent.extras?.get(key)}" }
            ?: "none"
        Log.i(TAG, "Broadcast received: action=${intent.action}, extras=[$extras]")

        CommandDispatcher.dispatch(intent)
    }
}
