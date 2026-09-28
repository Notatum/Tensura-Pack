@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul
title Tensura Server - Sincronizacion estricta

rem ============================================================
rem CONFIGURACION
rem ============================================================
set "BASE=%APPDATA%\.tlauncher\legacy\Minecraft"
set "GAME=%BASE%\game"
set "LAUNCHER=%BASE%\LL.exe"

set "UPDATER_DIR=%LOCALAPPDATA%\TensuraUpdater"
set "UPDATER=%UPDATER_DIR%\packwiz-installer-bootstrap.jar"
set "STAGE=%UPDATER_DIR%\staging"
set "STATE_FILE=%UPDATER_DIR%\pack.commit"

rem IMPORTANTE:
rem No usamos /main/ directamente porque raw.githubusercontent.com puede servir
rem una copia cacheada durante unos minutos. Primero obtenemos el SHA exacto
rem del ultimo commit y fijamos TODO el pack a ese commit.
set "GITHUB_API=https://api.github.com/repos/Notatum/Tensura-Pack/commits/main"

set "BOOTSTRAP_URL=https://github.com/packwiz/packwiz-installer-bootstrap/releases/latest/download/packwiz-installer-bootstrap.jar"


echo ============================================================
echo        TENSURA SERVER - SINCRONIZACION ESTRICTA
echo ============================================================
echo.
echo Carpeta del juego:
echo   %GAME%
echo.
echo Se sincroniza de forma estricta:
echo   mods
echo   config
echo   defaultconfigs
echo   resourcepacks
echo   shaderpacks
echo   kubejs
echo   scripts
echo.
echo NO se toca:
echo   saves
echo   screenshots
echo   options.txt
echo   servers.dat
echo   logs
echo.

rem ============================================================
rem VALIDACIONES
rem ============================================================
if not exist "%GAME%\." (
    echo [ERROR] No se encontro la carpeta del juego:
    echo %GAME%
    echo.
    pause
    exit /b 1
)

if not exist "%LAUNCHER%" (
    echo [ERROR] No se encontro TL Legacy:
    echo %LAUNCHER%
    echo.
    pause
    exit /b 1
)

tasklist /FI "IMAGENAME eq LL.exe" 2>nul | find /I "LL.exe" >nul
if not errorlevel 1 (
    echo [ERROR] TL Legacy esta abierto.
    echo Cerralo antes de sincronizar el pack y ejecuta este BAT de nuevo.
    echo.
    pause
    exit /b 1
)

rem ============================================================
rem BUSCAR JAVA
rem ============================================================
set "JAVA_EXE="
for /f "delims=" %%J in ('where java.exe 2^>nul') do (
    if not defined JAVA_EXE set "JAVA_EXE=%%J"
)

if not defined JAVA_EXE if exist "%BASE%\jre\" (
    for /r "%BASE%\jre" %%J in (java.exe) do (
        if not defined JAVA_EXE if exist "%%~fJ" set "JAVA_EXE=%%~fJ"
    )
)

if not defined JAVA_EXE (
    echo [ERROR] No se encontro Java.
    echo Abri TL Legacy una vez para que descargue su Java y volve a intentar.
    echo.
    pause
    exit /b 1
)

echo Java:
echo   %JAVA_EXE%
echo.

rem ============================================================
rem PREPARAR CARPETA DEL ACTUALIZADOR
rem ============================================================
if not exist "%UPDATER_DIR%\." mkdir "%UPDATER_DIR%" >nul 2>&1

rem ============================================================
rem DESCARGAR PACKWIZ INSTALLER SI FALTA
rem ============================================================
if not exist "%UPDATER%" (
    echo Descargando Packwiz Installer...
    call :Download "%BOOTSTRAP_URL%" "%UPDATER%.tmp"
    if errorlevel 1 (
        echo.
        echo [ERROR] No se pudo descargar Packwiz Installer.
        if exist "%UPDATER%.tmp" del /q "%UPDATER%.tmp" >nul 2>&1
        pause
        exit /b 1
    )
    move /Y "%UPDATER%.tmp" "%UPDATER%" >nul
)

rem ============================================================
rem OBTENER EL COMMIT EXACTO DEL PACK
rem ============================================================
echo Comprobando version del pack...

set "REMOTE_COMMIT="
for /f "usebackq delims=" %%S in (`powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $h=@{'User-Agent'='TensuraUpdater';'Cache-Control'='no-cache'}; (Invoke-RestMethod -UseBasicParsing -Headers $h -Uri '%GITHUB_API%').sha"`) do (
    set "REMOTE_COMMIT=%%S"
)

if not defined REMOTE_COMMIT (
    echo.
    echo [ERROR] No se pudo obtener el ultimo commit de GitHub.
    echo.
    pause
    exit /b 1
)

set "PACKURL=https://raw.githubusercontent.com/Notatum/Tensura-Pack/%REMOTE_COMMIT%/pack.toml"

echo Commit remoto:
echo   %REMOTE_COMMIT%

set "OLD_COMMIT="
if exist "%STATE_FILE%" set /p OLD_COMMIT=<"%STATE_FILE%"

if /I not "%REMOTE_COMMIT%"=="%OLD_COMMIT%" (
    echo Se detecto una version nueva del pack.
    echo Reconstruyendo copia canonica desde cero...

    if exist "%STAGE%\" (
        rmdir /S /Q "%STAGE%"
        if exist "%STAGE%\" (
            echo.
            echo [ERROR] No se pudo limpiar el staging:
            echo   %STAGE%
            echo Cerra Minecraft, TL Legacy y cualquier ventana de Packwiz.
            echo.
            pause
            exit /b 1
        )
    )
)

if not exist "%STAGE%\." mkdir "%STAGE%" >nul 2>&1

rem ============================================================
rem ACTUALIZAR COPIA CANONICA EN STAGING
rem ============================================================
echo.
echo Descargando / validando el modpack...
echo.

pushd "%STAGE%" >nul
"%JAVA_EXE%" -jar "%UPDATER%" "%PACKURL%"
set "PW_RESULT=%ERRORLEVEL%"
popd >nul

if not "%PW_RESULT%"=="0" (
    echo.
    echo [ERROR] Packwiz no pudo actualizar el pack.
    echo No se modifico tu instalacion de Minecraft.
    echo Codigo de salida: %PW_RESULT%
    echo.
    pause
    exit /b %PW_RESULT%
)

> "%STATE_FILE%" echo %REMOTE_COMMIT%

rem ============================================================
rem VERIFICACION DE PACKS DE VEHICULOS ACTUALES
rem ============================================================
if exist "%STAGE%\mods\SVMP3.7.3 1.16+.jar" (
    echo [OK] SVMP descargado.
) else (
    echo [AVISO] SVMP no aparece en staging.
)

if exist "%STAGE%\mods\Trin Civil Pack-1.20.1-4.5.0.jar" (
    echo [OK] Trin Civil Pack descargado.
) else (
    echo [AVISO] Trin Civil Pack no aparece en staging.
)

if exist "%STAGE%\mods\Trin Parts Pack-1.20.1-2.28.0.jar" (
    echo [OK] Trin Part Pack descargado.
) else (
    echo [AVISO] Trin Part Pack no aparece en staging.
)

rem ============================================================
rem SINCRONIZACION ESTRICTA
rem /MIR elimina archivos extra del destino.
rem ============================================================
echo.
echo Aplicando sincronizacion estricta...
echo.

call :MirrorFolder "mods"
if errorlevel 1 goto :sync_error

call :MirrorFolder "config"
if errorlevel 1 goto :sync_error

call :MirrorFolder "defaultconfigs"
if errorlevel 1 goto :sync_error

call :MirrorFolder "resourcepacks"
if errorlevel 1 goto :sync_error

call :MirrorFolder "shaderpacks"
if errorlevel 1 goto :sync_error

call :MirrorFolder "kubejs"
if errorlevel 1 goto :sync_error

call :MirrorFolder "scripts"
if errorlevel 1 goto :sync_error

echo.
echo ============================================================
echo        SINCRONIZACION COMPLETADA CORRECTAMENTE
echo ============================================================
echo.
echo Abriendo TL Legacy...
echo.

rem ============================================================
rem ABRIR SOLO EL LAUNCHER
rem No intenta iniciar Minecraft automaticamente.
rem ============================================================
if not exist "%LAUNCHER%" (
    echo [ERROR] No se encontro TL Legacy al intentar abrirlo:
    echo   %LAUNCHER%
    echo.
    pause
    exit /b 1
)

start "" /D "%BASE%" "%LAUNCHER%"

if errorlevel 1 (
    echo [AVISO] START no pudo abrir TL Legacy. Intentando con PowerShell...
    powershell -NoProfile -ExecutionPolicy Bypass -Command ^
      "Start-Process -FilePath '%LAUNCHER%' -WorkingDirectory '%BASE%'"
)

exit /b 0

:Download
set "DL_URL=%~1"
set "DL_OUT=%~2"
if exist "%DL_OUT%" del /q "%DL_OUT%" >nul 2>&1

where curl.exe >nul 2>&1
if not errorlevel 1 (
    curl.exe -fsSL --retry 3 --connect-timeout 15 "%DL_URL%" -o "%DL_OUT%"
    exit /b !ERRORLEVEL!
)

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "try { Invoke-WebRequest -UseBasicParsing -Uri '%DL_URL%' -OutFile '%DL_OUT%'; exit 0 } catch { exit 1 }"
exit /b !ERRORLEVEL!


:MirrorFolder
set "SYNC_FOLDER=%~1"

if exist "%STAGE%\%SYNC_FOLDER%\" (
    if not exist "%GAME%\%SYNC_FOLDER%\" mkdir "%GAME%\%SYNC_FOLDER%" >nul 2>&1

    robocopy "%STAGE%\%SYNC_FOLDER%" "%GAME%\%SYNC_FOLDER%" /MIR /R:2 /W:1 /NFL /NDL /NJH /NJS /NP >nul
    set "RC=!ERRORLEVEL!"

    if !RC! GEQ 8 (
        echo [ERROR] Fallo la sincronizacion de "%SYNC_FOLDER%". Robocopy: !RC!
        exit /b 1
    )

    echo [OK] %SYNC_FOLDER%
) else (
    if exist "%GAME%\%SYNC_FOLDER%\" (
        rmdir /S /Q "%GAME%\%SYNC_FOLDER%" 2>nul
        if exist "%GAME%\%SYNC_FOLDER%\" (
            echo [ERROR] No se pudo limpiar "%SYNC_FOLDER%".
            exit /b 1
        )
    )
    echo [OK] %SYNC_FOLDER% ^(vacio en el pack^)
)

exit /b 0


:sync_error
echo.
echo ============================================================
echo [ERROR] LA SINCRONIZACION NO PUDO COMPLETARSE
echo ============================================================
echo.
echo Verifica que Minecraft este cerrado y ejecuta el BAT nuevamente.
echo.
pause
exit /b 1
