@echo off
setlocal
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Run-BossDeathTracker.ps1"
set EXITCODE=%ERRORLEVEL%
echo.
if not "%EXITCODE%"=="0" (
    echo Boss Death Tracker launcher exited with code %EXITCODE%.
    pause
)
exit /b %EXITCODE%
