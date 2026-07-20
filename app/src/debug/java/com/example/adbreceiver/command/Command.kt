package com.example.adbreceiver.command

/** Typed representation of a command received via an ADB broadcast. */
sealed class CommandAction {
    data class ChangeBackground(val colorHex: String) : CommandAction()
    data class ChangeLabel(val text: String) : CommandAction()
}
