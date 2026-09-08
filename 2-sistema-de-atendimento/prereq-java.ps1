<#
.SYNOPSIS
  Pre-requisito do .mcpb: garante o Java 21 na maquina (e so isso).

.DESCRIPTION
  Acompanha o pacote millenium-agente-ia.mcpb. Verifica se ja existe um JDK/JRE 21;
  se nao, instala o Microsoft OpenJDK 21 via winget. Ao final, mostra o caminho do java.exe
  para preencher o campo "Java 21" na instalacao da extensao, caso 'java' nao esteja no PATH.
#>
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

function Write-Ok($m)   { Write-Host "    [OK] $m" -ForegroundColor Green }
function Write-Warn2($m){ Write-Host "    [!]  $m" -ForegroundColor Yellow }
function Write-Info($m) { Write-Host "    $m" -ForegroundColor Gray }

function Get-JavaMajor([string]$javaExe) {
    try { $line = (& $javaExe -version 2>&1 | Select-Object -First 1) -join ' ' } catch { return 0 }
    if ($line -match 'version "(\d+)(?:\.(\d+))?') {
        $major = [int]$Matches[1]
        if ($major -eq 1 -and $Matches[2]) { $major = [int]$Matches[2] }
        return $major
    }
    return 0
}

function Find-Jdk21 {
    $cands = @()
    if ($env:JAVA_HOME) { $cands += $env:JAVA_HOME }
    $roots = @("$env:ProgramFiles\Microsoft","$env:ProgramFiles\Eclipse Adoptium","$env:ProgramFiles\Java","$env:ProgramFiles\Zulu","$env:ProgramFiles\Amazon Corretto")
    foreach ($r in $roots) {
        if (Test-Path $r) {
            $cands += (Get-ChildItem -Path $r -Directory -Filter 'jdk*' -ErrorAction SilentlyContinue |
                Sort-Object Name -Descending | ForEach-Object { $_.FullName })
        }
    }
    foreach ($c in $cands) {
        $exe = Join-Path $c 'bin\java.exe'
        if ((Test-Path $exe) -and ((Get-JavaMajor $exe) -ge 21)) { return $exe }
    }
    $onPath = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($onPath -and (Get-JavaMajor $onPath.Source) -ge 21) { return $onPath.Source }
    return $null
}

Write-Host "===================================================================" -ForegroundColor White
Write-Host "  Pre-requisito: Java 21 (para o Millenium Agente IA .mcpb)" -ForegroundColor White
Write-Host "===================================================================" -ForegroundColor White

$javaExe = Find-Jdk21
if ($javaExe) {
    Write-Ok "Java 21 ja instalado: $javaExe"
} else {
    Write-Warn2 "Java 21 nao encontrado. Instalando Microsoft OpenJDK 21 via winget..."
    if (-not (Get-Command winget -ErrorAction SilentlyContinue)) {
        throw "winget indisponivel. Baixe o JDK 21 em https://learn.microsoft.com/java/openjdk/download e instale manualmente."
    }
    winget install --id Microsoft.OpenJDK.21 -e --silent `
        --accept-package-agreements --accept-source-agreements | Out-Host
    $javaExe = Find-Jdk21
    if (-not $javaExe) {
        throw "JDK instalado, mas nao localizado nesta sessao. Feche/reabra o terminal e rode de novo."
    }
    Write-Ok "Java 21 instalado: $javaExe"
}

# esta 'java' no PATH desta sessao?
$onPath = Get-Command java.exe -ErrorAction SilentlyContinue
$javaNoPath = ($onPath -and (Get-JavaMajor $onPath.Source) -ge 21)

Write-Host "`n-------------------------------------------------------------------" -ForegroundColor White
Write-Host "  Ao instalar o .mcpb no Claude Desktop, no campo 'Java 21':" -ForegroundColor White
if ($javaNoPath) {
    Write-Info "Pode deixar simplesmente:  java"
    Write-Info "(ou, se preferir, o caminho completo abaixo)"
} else {
    Write-Warn2 "'java' ainda NAO esta no PATH desta sessao. Use o caminho completo:"
}
Write-Host "      $javaExe" -ForegroundColor Cyan
Write-Host "-------------------------------------------------------------------" -ForegroundColor White
Write-Host "`nPronto. Agora arraste o millenium-agente-ia.mcpb para o Claude Desktop." -ForegroundColor Green
