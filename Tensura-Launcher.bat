@echo off
setlocal
chcp 65001 >nul
set "TENSURA_NO_LAUNCH="
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Actualizar-Tensura-Cliente.ps1" %*
set "RESULT=%ERRORLEVEL%"
if not "%RESULT%"=="0" pause
exit /b %RESULT%
