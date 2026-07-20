#!/bin/bash
# Usage: change_background.sh <hex_color>   (e.g. #FF5733)
# Meant to be opened/run from adb_script_runner (positional parameter $1).
# "-p com.example.adbreceiver" makes the broadcast explicit: since Android 8.0 (API 26),
# manifest-declared receivers no longer get implicit broadcasts, so without -p/-n this
# would silently reach zero receivers.
# The value must be wrapped in single quotes inside the double quotes: without them, a
# leading "#" (as in a hex color) is parsed as a comment by the device's shell once "adb
# shell" re-tokenizes the command, silently eating the rest of the argument.
adb shell am broadcast -p com.example.adbreceiver -a com.example.adbreceiver.ACTION_CHANGE_BACKGROUND --es color "'$1'"
