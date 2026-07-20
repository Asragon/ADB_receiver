package com.example.adbreceiver.ui

import android.util.Log

private const val TAG = "AppUiState"

/**
 * Bridge between the BroadcastReceiver (debug-only, no state of its own, a fresh instance per
 * broadcast) and the Views-based UI. A singleton with a simple listener list is enough for a
 * single screen: onReceive() runs on the main thread, so listeners can update Views directly,
 * no thread-hop needed.
 */
object AppUiState {

    var backgroundColor: Int = android.graphics.Color.WHITE
        private set

    var labelText: String = ""
        private set

    private val listeners = mutableListOf<() -> Unit>()

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
        Log.v(TAG, "Listener added, total=${listeners.size}")
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
        Log.v(TAG, "Listener removed, total=${listeners.size}")
    }

    fun setBackgroundColor(color: Int) {
        Log.d(TAG, "setBackgroundColor: #${Integer.toHexString(color)}")
        backgroundColor = color
        notifyListeners()
    }

    fun setLabelText(text: String) {
        Log.d(TAG, "setLabelText: \"$text\"")
        labelText = text
        notifyListeners()
    }

    fun reset(defaultLabelText: String) {
        Log.d(TAG, "reset")
        backgroundColor = android.graphics.Color.WHITE
        labelText = defaultLabelText
        notifyListeners()
    }

    private fun notifyListeners() {
        Log.v(TAG, "Notifying ${listeners.size} listener(s)")
        listeners.toList().forEach { it() }
    }
}
