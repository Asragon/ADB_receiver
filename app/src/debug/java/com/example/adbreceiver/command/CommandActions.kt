package com.example.adbreceiver.command

/**
 * Keep these constants in sync with the <action> entries declared in
 * src/debug/AndroidManifest.xml: adding a new command requires a new ACTION_EXTRA_*
 * pair here, a new <action> in the manifest, and the matching branches in CommandDispatcher.
 */
object CommandActions {
    private const val PACKAGE = "com.example.adbreceiver"

    const val ACTION_CHANGE_BACKGROUND = "$PACKAGE.ACTION_CHANGE_BACKGROUND"
    const val ACTION_CHANGE_LABEL = "$PACKAGE.ACTION_CHANGE_LABEL"

    const val EXTRA_COLOR = "color"
    const val EXTRA_TEXT = "text"
}
