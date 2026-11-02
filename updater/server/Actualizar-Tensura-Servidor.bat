@echo off
setlocal
cd /d "%~dp0"
java -jar "%~dp0Actualizador-Tensura-Servidor.jar" --server-dir "%~dp0." %*
exit /b %ERRORLEVEL%
