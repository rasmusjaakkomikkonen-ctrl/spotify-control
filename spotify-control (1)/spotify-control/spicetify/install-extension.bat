@echo off
REM Requires Spicetify already installed (https://spicetify.app)
if not exist "%APPDATA%\spicetify\Extensions" mkdir "%APPDATA%\spicetify\Extensions"
copy /Y "%~dp0mcspotify.js" "%APPDATA%\spicetify\Extensions\mcspotify.js"
spicetify config extensions mcspotify.js
spicetify apply
pause
