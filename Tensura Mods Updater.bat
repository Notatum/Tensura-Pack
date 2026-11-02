@echo off
setlocal
chcp 65001 >nul
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0Actualizar-Tensura-Cliente.ps1" -Mode Strict %*
set "RESULT=%ERRORLEVEL%"
if not "%RESULT%"=="0" if not defined TENSURA_NO_PAUSE pause
exit /b %RESULT%
