@echo off
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0install-apk.ps1"
exit /b %ERRORLEVEL%
