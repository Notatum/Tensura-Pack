@echo off
setlocal
if "%~1"=="" (
  echo Uso: Iniciar-Tensura-Con-Update.bat comando-original-de-inicio
  exit /b 2
)
cd /d "%~dp0"
java -jar "%~dp0Actualizador-Tensura-Servidor.jar" --server-dir "%~dp0." -- %*
exit /b %ERRORLEVEL%
