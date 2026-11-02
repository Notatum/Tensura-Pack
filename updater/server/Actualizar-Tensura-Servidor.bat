@echo off
setlocal
chcp 65001 >nul
cd /d "%~dp0"
java -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -jar "%~dp0Actualizador-Tensura-Servidor.jar" --server-dir "%~dp0." %*
exit /b %ERRORLEVEL%
