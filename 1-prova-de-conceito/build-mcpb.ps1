<#
.SYNOPSIS
  Empacota o servidor MCP como um Desktop Extension (.mcpb) para o Claude Desktop.

.DESCRIPTION
  Gera dist\millenium-agente-ia.mcpb - o arquivo que o usuario instala arrastando para o
  Claude Desktop (Configuracoes -> Extensions), sem editar JSON. O .mcpb ja inclui o jar
  com todos os fixes; a maquina do usuario so precisa ter o Java 21.

  Este script e para QUEM DISTRIBUI (roda uma vez para gerar o pacote). Requer Maven + JDK
  no PATH para compilar o jar (ou use -SkipBuild se o jar ja existir em target\).

.PARAMETER SkipBuild
  Nao recompila; usa o jar existente em target\.
#>
[CmdletBinding()]
param([switch]$SkipBuild)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$jar  = Join-Path $root 'target\millenium-agente-ia-0.1.0-SNAPSHOT.jar'
$stage = Join-Path $root 'build\mcpb'
$dist  = Join-Path $root 'dist'
$mcpb  = Join-Path $dist 'millenium-agente-ia.mcpb'

function Info($m) { Write-Host "    $m" -ForegroundColor Gray }
function Step($m) { Write-Host "`n==> $m" -ForegroundColor Cyan }
function Ok($m)   { Write-Host "    [OK] $m" -ForegroundColor Green }

# --- jar ---
if (-not $SkipBuild -or -not (Test-Path $jar)) {
    Step 'Compilando o jar (mvn clean package)'
    $mvn = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if (-not $mvn) { $mvn = Get-Command mvn -ErrorAction SilentlyContinue }
    if (-not $mvn) { throw "Maven nao encontrado no PATH. Rode install.cmd primeiro, ou instale o Maven." }
    Push-Location $root
    try {
        & $mvn.Source '-B' 'clean' 'package'
        if ($LASTEXITCODE -ne 0) { throw "Falha no build (exit $LASTEXITCODE)." }
    } finally { Pop-Location }
}
if (-not (Test-Path $jar)) { throw "Jar nao encontrado: $jar" }
Ok "Jar: $jar"

# --- monta a pasta do pacote ---
Step 'Montando o pacote .mcpb'
if (Test-Path $stage) { Remove-Item $stage -Recurse -Force }
New-Item -ItemType Directory -Force -Path (Join-Path $stage 'server') | Out-Null
Copy-Item (Join-Path $root 'mcpb\manifest.json') (Join-Path $stage 'manifest.json') -Force
Copy-Item $jar (Join-Path $stage 'server\millenium-agente-ia.jar') -Force
# planilhas .xlsx que o MCP le (a extensao aponta fonte_dir para ${__dirname}/fonte)
New-Item -ItemType Directory -Force -Path (Join-Path $stage 'fonte') | Out-Null
$xlsx = Get-ChildItem -Path (Join-Path $root 'fonte') -Filter '*.xlsx' -ErrorAction SilentlyContinue
if (-not $xlsx) { throw "Nenhum .xlsx em fonte\ para empacotar. Copie os Excel para a pasta fonte\." }
Copy-Item $xlsx.FullName (Join-Path $stage 'fonte') -Force
# documentos uteis dentro do pacote (referencia)
Copy-Item (Join-Path $root 'agentes') (Join-Path $stage 'agentes') -Recurse -Force
Ok "Conteudo montado em: $stage ($($xlsx.Count) planilha(s))"

# --- empacota ---
New-Item -ItemType Directory -Force -Path $dist | Out-Null
if (Test-Path $mcpb) { Remove-Item $mcpb -Force }

$packed = $false
if (Get-Command npx -ErrorAction SilentlyContinue) {
    Step 'Empacotando com @anthropic-ai/mcpb (npx)'
    try {
        & npx --yes @anthropic-ai/mcpb pack $stage $mcpb
        if ($LASTEXITCODE -eq 0 -and (Test-Path $mcpb)) { $packed = $true }
    } catch { Info "mcpb CLI indisponivel, usando fallback de zip." }
}
if (-not $packed) {
    Step 'Empacotando com Compress-Archive (fallback)'
    $zip = Join-Path $dist 'millenium-agente-ia.zip'
    if (Test-Path $zip) { Remove-Item $zip -Force }
    Compress-Archive -Path (Join-Path $stage '*') -DestinationPath $zip -Force
    Move-Item $zip $mcpb -Force
}

if (-not (Test-Path $mcpb)) { throw "Falha ao gerar o .mcpb." }

# coloca o pre-requisito do Java ao lado do .mcpb, para distribuir os dois juntos
Copy-Item (Join-Path $root 'prereq-java.cmd') $dist -Force
Copy-Item (Join-Path $root 'prereq-java.ps1') $dist -Force
Ok "Pre-requisito do Java copiado para: $dist"

$sizeMb = [math]::Round((Get-Item $mcpb).Length / 1MB, 1)
Write-Host "`n===================================================================" -ForegroundColor Green
Write-Host "  PACOTE GERADO: $mcpb ($sizeMb MB)" -ForegroundColor Green
Write-Host "===================================================================" -ForegroundColor Green
Write-Host @"

Distribua os arquivos de dist\ juntos (o .mcpb e o prereq-java.cmd).
Como o usuario instala (Claude Desktop):
  1. Duplo-clique em prereq-java.cmd (instala o Java 21 se faltar).
  2. Abra o Claude Desktop -> Configuracoes -> Extensions (ou Desenvolvedor).
  3. Arraste o arquivo .mcpb para a janela (ou 'Install from file') e confirme.
     - Em 'Java 21': deixe 'java' se estiver no PATH, ou aponte o java.exe.
     - Em 'Pasta de dados': deixe o padrao (mocks sao gerados na 1a execucao).
  4. Nas Skills/Instruction do Projeto, cole os arquivos da pasta 'agentes\'.
"@ -ForegroundColor White
