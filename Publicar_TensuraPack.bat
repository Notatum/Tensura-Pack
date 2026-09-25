@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul
title Publicar Tensura Pack

cd /d "%~dp0"

echo ============================================================
echo              PUBLICAR TENSURA PACK
echo ============================================================
echo.
echo Carpeta:
echo   %CD%
echo.

rem ============================================================
rem VALIDACIONES
rem ============================================================
if not exist "packwiz.exe" (
    echo [ERROR] No se encontro packwiz.exe en esta carpeta.
    echo Este BAT debe estar dentro de:
    echo   C:\TensuraPack-Master
    echo.
    pause
    exit /b 1
)

if not exist ".git\" (
    echo [ERROR] Esta carpeta no parece ser el repositorio Git.
    echo No se encontro la carpeta .git
    echo.
    pause
    exit /b 1
)

where git.exe >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Git no esta disponible en PATH.
    echo.
    pause
    exit /b 1
)

rem ============================================================
rem PACKWIZ
rem ============================================================
echo [1/4] Actualizando indice de Packwiz...
packwiz.exe refresh
if errorlevel 1 (
    echo.
    echo [ERROR] packwiz refresh fallo.
    echo No se publico ningun cambio.
    echo.
    pause
    exit /b 1
)

echo.
echo [2/4] Preparando cambios para Git...
git add -A
if errorlevel 1 (
    echo.
    echo [ERROR] git add fallo.
    echo.
    pause
    exit /b 1
)

rem Si no hay cambios preparados, no crea commits vacios.
git diff --cached --quiet
if not errorlevel 1 (
    echo.
    echo ============================================================
    echo No hay cambios nuevos para publicar.
    echo ============================================================
    echo.
    pause
    exit /b 0
)

rem ============================================================
rem COMMIT AUTOMATICO
rem ============================================================
set "STAMP="
for /f "usebackq delims=" %%T in (`powershell -NoProfile -Command "Get-Date -Format 'yyyy-MM-dd HH:mm'"`) do set "STAMP=%%T"

if not defined STAMP set "STAMP=actualizacion"

echo.
echo [3/4] Creando commit...
git commit -m "Actualizacion Tensura Pack - %STAMP%"
if errorlevel 1 (
    echo.
    echo [ERROR] No se pudo crear el commit.
    echo.
    pause
    exit /b 1
)

rem ============================================================
rem PUSH
rem ============================================================
echo.
echo [4/4] Subiendo a GitHub...
git push
if errorlevel 1 (
    echo.
    echo ============================================================
    echo [ERROR] EL PUSH FALLO
    echo ============================================================
    echo.
    echo El commit quedo guardado localmente, pero GitHub no se actualizo.
    echo Revisa tu conexion o autenticacion e intenta nuevamente.
    echo.
    pause
    exit /b 1
)

echo.
echo ============================================================
echo        TENSURA PACK PUBLICADO CORRECTAMENTE
echo ============================================================
echo.
echo GitHub ya tiene la version nueva.
echo Los jugadores la recibiran al ejecutar su actualizador.
echo.

git log --oneline -1
echo.
pause
exit /b 0
