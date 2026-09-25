@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul
title Actualizar mods Tensura Pack - Minecraft 1.21.1

cd /d "%~dp0"

echo ============================================================
echo       ACTUALIZAR MODS - TENSURA PACK 1.21.1
echo ============================================================
echo.
echo Busca actualizaciones de todos los archivos gestionados
echo por Packwiz (Modrinth / CurseForge), manteniendo el pack
echo en Minecraft 1.21.1.
echo.
echo NO publica nada en GitHub automaticamente.
echo Primero proba el juego y despues usa Publicar_TensuraPack.bat
echo.

rem ============================================================
rem VALIDACIONES
rem ============================================================
if not exist "packwiz.exe" (
    echo [ERROR] No se encontro packwiz.exe.
    echo Este BAT debe estar dentro de C:\TensuraPack-Master
    echo.
    pause
    exit /b 1
)

if not exist "pack.toml" (
    echo [ERROR] No se encontro pack.toml.
    echo.
    pause
    exit /b 1
)

set "MCVER="
set "NFVER="

for /f "tokens=3" %%V in ('findstr /B /C:"minecraft = " "pack.toml"') do set "MCVER=%%~V"
for /f "tokens=3" %%V in ('findstr /B /C:"neoforge = " "pack.toml"') do set "NFVER=%%~V"

if not defined MCVER (
    echo [ERROR] No pude leer la version de Minecraft desde pack.toml.
    echo.
    echo Lineas detectadas:
    findstr /I "minecraft neoforge" "pack.toml"
    echo.
    pause
    exit /b 1
)

if /I not "%MCVER%"=="1.21.1" (
    echo [ERROR] El pack esta configurado para Minecraft %MCVER%
    echo Este actualizador esta bloqueado a Minecraft 1.21.1.
    echo.
    pause
    exit /b 1
)

echo Version detectada:
echo   Minecraft: %MCVER%
if defined NFVER (
    echo   NeoForge:  %NFVER%
) else (
    echo   NeoForge:  no se pudo leer la version
)
echo.

rem ============================================================
rem BACKUP
rem ============================================================
set "STAMP="
for /f "usebackq delims=" %%T in (`powershell -NoProfile -Command "Get-Date -Format 'yyyyMMdd-HHmmss'"`) do set "STAMP=%%T"
if not defined STAMP set "STAMP=backup"

set "BACKUP=%LOCALAPPDATA%\TensuraPackBackups\%STAMP%"
mkdir "%BACKUP%" >nul 2>&1

set "HAD_MODS=0"
set "HAD_SHADERS=0"
set "HAD_RESOURCES=0"

if exist "mods\" set "HAD_MODS=1"
if exist "shaderpacks\" set "HAD_SHADERS=1"
if exist "resourcepacks\" set "HAD_RESOURCES=1"

echo [1/4] Creando respaldo...
echo       %BACKUP%
echo.

copy /Y "pack.toml" "%BACKUP%\pack.toml" >nul
if exist "index.toml" copy /Y "index.toml" "%BACKUP%\index.toml" >nul

if exist "mods\" robocopy "mods" "%BACKUP%\mods" /E /R:1 /W:1 /NFL /NDL /NJH /NJS /NP >nul
if exist "shaderpacks\" robocopy "shaderpacks" "%BACKUP%\shaderpacks" /E /R:1 /W:1 /NFL /NDL /NJH /NJS /NP >nul
if exist "resourcepacks\" robocopy "resourcepacks" "%BACKUP%\resourcepacks" /E /R:1 /W:1 /NFL /NDL /NJH /NJS /NP >nul

rem ============================================================
rem UPDATE
rem ============================================================
echo [2/4] Buscando las versiones mas nuevas compatibles...
echo.
packwiz.exe update --all -y
set "UPDATE_RESULT=%ERRORLEVEL%"

if not "%UPDATE_RESULT%"=="0" (
    echo.
    echo ============================================================
    echo [ERROR] PACKWIZ NO PUDO COMPLETAR LA ACTUALIZACION
    echo ============================================================
    echo.
    echo Restaurando el Master...
    call :RestoreBackup
    echo.
    echo El pack fue restaurado al estado anterior.
    echo Respaldo:
    echo   %BACKUP%
    echo.
    pause
    exit /b %UPDATE_RESULT%
)

rem ============================================================
rem REFRESH
rem ============================================================
echo.
echo [3/4] Actualizando index.toml...
packwiz.exe refresh
if errorlevel 1 (
    echo.
    echo [ERROR] packwiz refresh fallo.
    echo Restaurando el Master...
    call :RestoreBackup
    echo.
    pause
    exit /b 1
)

rem ============================================================
rem RESUMEN
rem ============================================================
echo.
echo [4/4] Cambios detectados:
echo ------------------------------------------------------------
where git.exe >nul 2>&1
if errorlevel 1 (
    echo Git no esta disponible para mostrar el resumen.
) else (
    git status --short -- mods shaderpacks resourcepacks pack.toml index.toml
)
echo ------------------------------------------------------------

echo.
echo ============================================================
echo          ACTUALIZACION LOCAL COMPLETADA
echo ============================================================
echo.
echo Minecraft sigue fijado en 1.21.1.
echo.
echo IMPORTANTE: todavia NO se subio nada a GitHub.
echo Proba el juego primero.
echo.
echo Si funciona bien, ejecuta:
echo   Publicar_TensuraPack.bat
echo.
echo Respaldo:
echo   %BACKUP%
echo.
pause
exit /b 0


:RestoreBackup
copy /Y "%BACKUP%\pack.toml" "pack.toml" >nul 2>&1
if exist "%BACKUP%\index.toml" copy /Y "%BACKUP%\index.toml" "index.toml" >nul 2>&1

if "%HAD_MODS%"=="1" (
    if exist "mods\" rmdir /S /Q "mods"
    robocopy "%BACKUP%\mods" "mods" /E /R:1 /W:1 /NFL /NDL /NJH /NJS /NP >nul
) else (
    if exist "mods\" rmdir /S /Q "mods"
)

if "%HAD_SHADERS%"=="1" (
    if exist "shaderpacks\" rmdir /S /Q "shaderpacks"
    robocopy "%BACKUP%\shaderpacks" "shaderpacks" /E /R:1 /W:1 /NFL /NDL /NJH /NJS /NP >nul
) else (
    if exist "shaderpacks\" rmdir /S /Q "shaderpacks"
)

if "%HAD_RESOURCES%"=="1" (
    if exist "resourcepacks\" rmdir /S /Q "resourcepacks"
    robocopy "%BACKUP%\resourcepacks" "resourcepacks" /E /R:1 /W:1 /NFL /NDL /NJH /NJS /NP >nul
) else (
    if exist "resourcepacks\" rmdir /S /Q "resourcepacks"
)

exit /b 0
