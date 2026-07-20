#!/bin/bash
# Usage: change_label.sh <text>   (e.g. "Hello from ADB!")
# The value must be wrapped in single quotes inside the double quotes: it goes through two
# levels of shell parsing, the local one and the device's one via "adb shell".
# "-p com.example.adbreceiver" makes the broadcast explicit: since Android 8.0 (API 26),
# manifest-declared receivers no longer get implicit broadcasts, so without -p/-n this
# would silently reach zero receivers.
adb shell am broadcast -p com.example.adbreceiver -a com.example.adbreceiver.ACTION_CHANGE_LABEL --es text "'$1'"
