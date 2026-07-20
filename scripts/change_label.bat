@echo off
REM Usage: change_label.bat <text>   (e.g. "Hello from ADB!")
REM The value must be wrapped in single quotes inside the double quotes: it goes through two
REM levels of shell parsing, the local one (cmd) and the device's one via "adb shell".
REM "-p com.example.adbreceiver" makes the broadcast explicit: since Android 8.0 (API 26),
REM manifest-declared receivers no longer get implicit broadcasts, so without -p/-n this
REM would silently reach zero receivers.
adb shell am broadcast -p com.example.adbreceiver -a com.example.adbreceiver.ACTION_CHANGE_LABEL --es text "'%1'"
