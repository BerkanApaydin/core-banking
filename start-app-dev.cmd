@echo off
rem PowerShell policy is scoped to this process; no machine setting is changed.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start-app-dev.ps1"
exit /b %ERRORLEVEL%
