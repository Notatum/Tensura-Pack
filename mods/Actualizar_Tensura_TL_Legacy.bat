@echo off
setlocal EnableExtensions
title Tensura Server - Actualizador
chcp 65001 >nul

set "GAME=%APPDATA%\.tlauncher\legacy\Minecraft\game"
set "UPDATER_DIR=%LOCALAPPDATA%\TensuraUpdater"
set "UPDATER=%UPDATER_DIR%\packwiz-installer-bootstrap.jar"
set "PACKURL=https://notatum.github.io/Tensura-Pack/pack.toml"
set "BOOTSTRAP_URL=https://github.com/packwiz/packwiz-installer-bootstrap/releases/latest/download/packwiz-installer-bootstrap.jar"

echo ============================================================
echo              TENSURA SERVER - ACTUALIZADOR
echo ============================================================
echo.
echo Carpeta de Minecraft:
echo %GAME%
echo.

if not exist "%GAME%\." (
    echo [ERROR] No se encontro la carpeta de TL Legacy:
    echo %GAME%
    echo.
    echo Abri TL Legacy al menos una vez y verifica que use:
    echo .tlauncher\legacy\Minecraft\game
    echo.
    pause
    exit /b 1
)

where java >nul 2>&1
if errorlevel 1 (
    echo [ERROR] No se encontro Java en el sistema.
    echo.
    echo Abri TL Legacy una vez para que instale Java o agrega Java al PATH.
    echo.
    pause
    exit /b 1
)

if not exist "%UPDATER_DIR%\." mkdir "%UPDATER_DIR%" >nul 2>&1

if not exist "%UPDATER%" (
    echo Descargando Packwiz Installer...
    echo.

    curl.exe -fL --retry 3 --connect-timeout 15 "%BOOTSTRAP_URL%" -o "%UPDATER%.tmp"
    if errorlevel 1 (
        echo.
        echo [ERROR] No se pudo descargar Packwiz Installer.
        if exist "%UPDATER%.tmp" del /q "%UPDATER%.tmp" >nul 2>&1
        echo Verifica tu conexion a Internet.
        echo.
        pause
        exit /b 1
    )

    move /Y "%UPDATER%.tmp" "%UPDATER%" >nul
)

echo Actualizando Tensura Server...
echo No cierres esta ventana hasta que termine.
echo.

pushd "%GAME%" >nul
java -jar "%UPDATER%" "%PACKURL%"
set "RESULT=%ERRORLEVEL%"
popd >nul

echo.
if not "%RESULT%"=="0" (
    echo [ERROR] La actualizacion no termino correctamente.
    echo Codigo de salida: %RESULT%
    echo.
    pause
    exit /b %RESULT%
)

echo ============================================================
echo   ACTUALIZACION COMPLETADA CORRECTAMENTE
echo ============================================================
echo.
echo Ya podes abrir TL Legacy y jugar con NeoForge 1.21.1.
echo.
pause
exit /b 0
