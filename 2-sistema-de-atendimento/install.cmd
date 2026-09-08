@echo off
REM Instalador do Millenium Agente IA (MCP). Clique duplo ou rode no terminal.
REM Repassa qualquer argumento para o install.ps1 (ex.: install.cmd -SkipBuild).
setlocal
echo Iniciando o instalador do Millenium Agente IA...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0install.ps1" %*
set ERR=%ERRORLEVEL%
echo.
if %ERR% NEQ 0 (
  echo [ERRO] O instalador terminou com codigo %ERR%. Veja as mensagens acima.
) else (
  echo Concluido.
)
echo.
pause
endlocal
