@echo off
REM Pre-requisito do millenium-agente-ia.mcpb: instala o Java 21 (se faltar).
REM Clique duplo neste arquivo ANTES de instalar a extensao .mcpb no Claude Desktop.
setlocal
echo Verificando/instalando o Java 21...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0prereq-java.ps1"
set ERR=%ERRORLEVEL%
echo.
if %ERR% NEQ 0 (
  echo [ERRO] Terminou com codigo %ERR%. Veja as mensagens acima.
) else (
  echo Java 21 pronto.
)
echo.
pause
endlocal
