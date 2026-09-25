@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul
title Reparar hashes Packwiz - GitHub

set "MASTER=C:\TensuraPack-Master"
set "BACKUP=C:\TensuraPack-Config-Backup"
set "CHECK_LOCAL=%MASTER%\config\TPA\config\configuration.toml"
set "CHECK_REMOTE=%TEMP%\tensura-pack-configuration-remote.toml"
set "RAW_CHECK=https://raw.githubusercontent.com/Notatum/Tensura-Pack/main/config/TPA/config/configuration.toml"

echo ============================================================
echo       REPARAR HASHES PACKWIZ / GITHUB - UNA SOLA VEZ
echo ============================================================
echo.

if not exist "%MASTER%\packwiz.exe" (
    echo [ERROR] No se encontro:
    echo %MASTER%\packwiz.exe
    echo.
    pause
    exit /b 1
)

cd /d "%MASTER%"

echo [1/7] Copia de seguridad de config...
if not exist "%BACKUP%\" mkdir "%BACKUP%" >nul 2>&1
robocopy "%MASTER%\config" "%BACKUP%" /MIR /R:1 /W:1 /NFL /NDL /NJH /NJS /NP >nul
set "RC=%ERRORLEVEL%"
if %RC% GEQ 8 (
    echo [ERROR] No se pudo crear la copia de seguridad.
    pause
    exit /b 1
)
echo       Backup: %BACKUP%
echo.

echo [2/7] Fijando finales de linea LF en Git...
(
echo *.toml text eol=lf
echo *.toml.bak text eol=lf
echo *.ini text eol=lf
echo *.properties text eol=lf
echo *.txt text eol=lf
echo *.cfg text eol=lf
echo *.json text eol=lf
echo *.json5 text eol=lf
echo *.jar binary
echo *.zip binary
echo *.png binary
echo *.jpg binary
echo *.jpeg binary
echo *.gif binary
) > .gitattributes

git config core.autocrlf false
echo.

echo [3/7] Normalizando SOLO archivos de texto dentro de config...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
 "$utf8 = New-Object System.Text.UTF8Encoding($false); Get-ChildItem -LiteralPath '%MASTER%\config' -Recurse -File | Where-Object { $_.Name -match '\.(toml|ini|properties|txt)$' -or $_.Name -match '\.toml\.bak$' } | ForEach-Object { $p=$_.FullName; $s=[System.IO.File]::ReadAllText($p); $s=$s -replace \"`r`n\",\"`n\"; [System.IO.File]::WriteAllText($p,$s,$utf8) }"
if errorlevel 1 (
    echo [ERROR] Fallo la normalizacion de archivos.
    pause
    exit /b 1
)
echo.

echo [4/7] Regenerando hashes de Packwiz...
packwiz.exe refresh
if errorlevel 1 (
    echo [ERROR] packwiz refresh fallo.
    pause
    exit /b 1
)
echo.

echo [5/7] Preparando commit...
git add --renormalize .
git add .

git diff --cached --quiet
if errorlevel 1 (
    git commit -m "Normalize Packwiz config hashes for GitHub"
    if errorlevel 1 (
        echo [ERROR] No se pudo crear el commit.
        pause
        exit /b 1
    )
) else (
    echo No habia cambios nuevos para commitear.
)
echo.

echo [6/7] Subiendo a GitHub...
git push
if errorlevel 1 (
    echo [ERROR] git push fallo.
    pause
    exit /b 1
)
echo.

echo [7/7] Verificando SHA-256 local contra GitHub RAW...
if exist "%CHECK_REMOTE%" del /q "%CHECK_REMOTE%" >nul 2>&1
timeout /t 3 /nobreak >nul
curl.exe -fsSL --retry 5 "%RAW_CHECK%" -o "%CHECK_REMOTE%"
if errorlevel 1 (
    echo [ERROR] No se pudo descargar el archivo de comprobacion desde GitHub.
    pause
    exit /b 1
)

for /f "delims=" %%H in ('powershell -NoProfile -Command "(Get-FileHash -Algorithm SHA256 -LiteralPath '%CHECK_LOCAL%').Hash.ToLower()"') do set "LOCAL_HASH=%%H"
for /f "delims=" %%H in ('powershell -NoProfile -Command "(Get-FileHash -Algorithm SHA256 -LiteralPath '%CHECK_REMOTE%').Hash.ToLower()"') do set "REMOTE_HASH=%%H"

echo.
echo Local : !LOCAL_HASH!
echo GitHub: !REMOTE_HASH!
echo.

if /I not "!LOCAL_HASH!"=="!REMOTE_HASH!" (
    echo ============================================================
    echo [ERROR] LOS HASHES TODAVIA NO COINCIDEN
    echo ============================================================
    echo.
    echo No ejecutes aun el actualizador del juego.
    echo Mandame esta ventana completa.
    echo.
    pause
    exit /b 1
)

echo ============================================================
echo [OK] LOS HASHES COINCIDEN
echo ============================================================
echo.
echo Limpiando el staging fallido del actualizador...
if exist "%LOCALAPPDATA%\TensuraUpdater\staging\" rmdir /S /Q "%LOCALAPPDATA%\TensuraUpdater\staging"
if exist "%LOCALAPPDATA%\TensuraUpdater\pack.sha256" del /q "%LOCALAPPDATA%\TensuraUpdater\pack.sha256" >nul 2>&1
if exist "%CHECK_REMOTE%" del /q "%CHECK_REMOTE%" >nul 2>&1

echo.
echo Ya podes ejecutar:
echo   Actualizar_Tensura_ESTRICTO_y_Jugar_v2.bat
echo.
pause
exit /b 0
