@echo off
rem PowerShell policy is scoped to this process; no machine setting is changed.
rem On failure the window stays open (pause) so the error above remains visible
rem instead of flashing away - especially on double-click launches.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start-app-dev.ps1"
set EXITCODE=%ERRORLEVEL%
if %EXITCODE% neq 0 (
  echo.
  echo start-app-dev FAILED with exit code %EXITCODE%. See the error messages above.
  echo Common causes: Docker Desktop not running, ports 8080/5432/6389 already in use
  echo ^(stop the other instance: "docker compose stop app" or Ctrl+C in the host-app window^).
  pause
)
exit /b %EXITCODE%
