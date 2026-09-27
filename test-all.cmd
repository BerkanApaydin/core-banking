@echo off
rem PowerShell policy is scoped to this process; no machine setting is changed.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\test-all.ps1" %*
exit /b %ERRORLEVEL%
