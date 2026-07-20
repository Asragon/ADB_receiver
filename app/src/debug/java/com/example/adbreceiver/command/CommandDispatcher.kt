package com.example.adbreceiver.command

import android.content.Intent
import android.graphics.Color
import android.util.Log
import com.example.adbreceiver.ui.AppUiState
import androidx.core.graphics.toColorInt

/**
 * Single entry point that translates a received Intent (ADB broadcast) into an action on the
 * shared UI state. Adding a third command: a new CommandAction subtype, a new branch here in
 * parse()/apply(), a new constant in CommandActions, a new <action> in the debug manifest.
 */
object CommandDispatcher {
    private const val TAG = "CommandDispatcher"

    private fun String.withHashPrefix(): String = if (startsWith("#")) this else "#$this"

    fun dispatch(intent: Intent) {
        Log.d(TAG, "Dispatching action=${intent.action}")
        val command = parse(intent)
        if (command == null) {
            Log.w(TAG, "Unrecognized intent: action=${intent.action}")
            return
        }
        apply(command)
    }

    private fun parse(intent: Intent): CommandAction? = when (intent.action) {
        CommandActions.ACTION_CHANGE_BACKGROUND ->
            intent.getStringExtra(CommandActions.EXTRA_COLOR)?.let { CommandAction.ChangeBackground(it) }

        CommandActions.ACTION_CHANGE_LABEL ->
            intent.getStringExtra(CommandActions.EXTRA_TEXT)?.let { CommandAction.ChangeLabel(it) }

        else -> null
    }

    private fun apply(command: CommandAction) {
        when (command) {
            is CommandAction.ChangeBackground -> {
                runCatching { command.colorHex.withHashPrefix().toColorInt() }
                    .onSuccess { AppUiState.setBackgroundColor(it) }
                    .onFailure { Log.w(TAG, "Invalid color: ${command.colorHex}", it) }
            }

            is CommandAction.ChangeLabel -> {
                AppUiState.setLabelText(command.text)
            }
        }
        Log.d(TAG, "Command applied: $command")
    }
}
