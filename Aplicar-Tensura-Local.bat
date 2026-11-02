@echo off
chcp 65001 >nul
echo Cerra Minecraft antes de continuar.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0Aplicar-Tensura-Local.ps1" %*
if errorlevel 1 pause
