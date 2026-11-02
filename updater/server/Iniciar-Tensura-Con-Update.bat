@echo off
setlocal
chcp 65001 >nul
if "%~1"=="" (
  echo Uso: Iniciar-Tensura-Con-Update.bat comando-original-de-inicio
  exit /b 2
)
cd /d "%~dp0"
java -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -jar "%~dp0Actualizador-Tensura-Servidor.jar" --server-dir "%~dp0." -- %*
exit /b %ERRORLEVEL%
