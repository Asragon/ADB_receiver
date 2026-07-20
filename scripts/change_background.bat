@echo off
REM Usage: change_background.bat <hex_color>   (e.g. #FF5733)
REM Meant to be opened/run from adb_script_runner (positional parameter %1).
REM "-p com.example.adbreceiver" makes the broadcast explicit: since Android 8.0 (API 26),
REM manifest-declared receivers no longer get implicit broadcasts, so without -p/-n this
REM would silently reach zero receivers.
REM The value must be wrapped in single quotes inside the double quotes: without them, a
REM leading "#" (as in a hex color) is parsed as a comment by the device's shell once "adb
REM shell" re-tokenizes the command, silently eating the rest of the argument.
adb shell am broadcast -p com.example.adbreceiver -a com.example.adbreceiver.ACTION_CHANGE_BACKGROUND --es color "'%1'"
