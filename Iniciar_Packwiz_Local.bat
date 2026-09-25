@echo off
setlocal EnableExtensions
chcp 65001 >nul
title Tensura Pack - Servidor local Packwiz

cd /d "%~dp0"

if not exist "packwiz.exe" (
    echo [ERROR] No se encontro packwiz.exe.
    echo Coloca este BAT dentro de C:\TensuraPack-Master
    echo.
    pause
    exit /b 1
)

echo ============================================================
echo        PACKWIZ LOCAL - TENSURA PACK
echo ============================================================
echo.
echo URL local:
echo   http://localhost:8080/pack.toml
echo.
echo Este servidor es SOLO para pruebas locales.
echo Para tus amigos se usa GitHub, asi que no hace falta
echo mantener esta ventana abierta normalmente.
echo.
echo Para detenerlo: Ctrl+C o cierra esta ventana.
echo.

packwiz.exe serve

echo.
echo Packwiz serve se detuvo.
pause
