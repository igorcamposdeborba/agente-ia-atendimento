<#
.SYNOPSIS
  Instalador do Millenium Agente IA (MCP - Fase 1 PoC) para Windows 11.

.DESCRIPTION
  Numa maquina nova, deixa tudo pronto para o usuario abrir o Claude Desktop e usar:
    1. Garante um JDK 21 (instala via winget se faltar).
    2. Garante o Maven (usa o do PATH ou baixa uma copia local em .\.tools).
    3. Compila o projeto e gera o jar executavel (com todos os fixes).
    4. Gera as planilhas .xlsx mock (dados ficticios) em .\data.
    5. Registra o servidor MCP no claude_desktop_config.json (sem apagar o que ja existe).
    6. Mostra onde estao a Instruction e as Skills para colar no Projeto do Claude.

  A wiki e servida pelo proprio MCP (tool consultar_wiki) - nao precisa instalar nada.

  Rode a partir da pasta do projeto (onde esta o pom.xml). Coloque a pasta num lugar
  permanente (ex.: C:\Millenium\millenium-agente-ia) ANTES de rodar, pois o caminho do
  jar vai para a config do Claude.

.PARAMETER SkipBuild
  Pula a compilacao (usa o jar que ja existir em target\).

.PARAMETER NoOpenFolder
  Nao abre a pasta 'agentes' no Explorer ao final.
#>
[CmdletBinding()]
param(
    [switch]$SkipBuild,
    [switch]$NoOpenFolder,
    # Caminho do JDK 21 ja instalado, para pular a busca/winget. Ex.:
    #   install.cmd -JavaHome "C:\Program Files\Java\jdk-21"
    [string]$JavaHome
)

$ErrorActionPreference = 'Stop'
$MavenVersion = '3.9.9'

# ------------------------------------------------------------------ helpers ----
function Write-Step($msg)  { Write-Host "`n==> $msg" -ForegroundColor Cyan }
function Write-Ok($msg)    { Write-Host "    [OK] $msg" -ForegroundColor Green }
function Write-Warn2($msg) { Write-Host "    [!]  $msg" -ForegroundColor Yellow }
function Write-Info($msg)  { Write-Host "    $msg" -ForegroundColor Gray }

# Roda 'java -XshowSettings:properties -version' UMA vez e devolve @{ Major=<int>; Home=<string> }.
# Este e o metodo confiavel: le as propriedades java.version/java.home (que independem do banner
# e resolvem shims como o javapath da Oracle), em vez de parsear o texto do -version no stderr.
function Get-JavaProps([string]$javaExe) {
    $res = @{ Major = 0; Home = $null }
    try {
        $out = (& $javaExe -XshowSettings:properties -version 2>&1 | Out-String)
    } catch { return $res }
    if ($out -match 'java\.home\s*=\s*(.+)') { $res.Home = $Matches[1].Trim() }
    if ($out -match 'java\.version\s*=\s*"?(\d+)(?:\.(\d+))?') {
        $mj = [int]$Matches[1]
        if ($mj -eq 1 -and $Matches[2]) { $mj = [int]$Matches[2] }  # 1.8 -> 8
        $res.Major = $mj
    }
    return $res
}

function Get-JavaMajor([string]$javaExe) {
    # 1o via propriedades (robusto contra "Picked up JAVA_TOOL_OPTIONS" e shims).
    $mj = (Get-JavaProps $javaExe).Major
    if ($mj -gt 0) { return $mj }
    # fallback: parseia o banner do -version (varre TODA a saida, nao so a 1a linha).
    try {
        $out = (& $javaExe -version 2>&1 | Out-String)
    } catch { return 0 }
    if ($out -match 'version "(\d+)(?:\.(\d+))?') {
        $major = [int]$Matches[1]
        if ($major -eq 1 -and $Matches[2]) { $major = [int]$Matches[2] }  # 1.8 -> 8
        return $major
    }
    # alguns builds imprimem "openjdk 21.0.2" sem aspas (formato --version)
    if ($out -match '(?:^|\s)(\d+)\.\d+\.\d+') { return [int]$Matches[1] }
    return 0
}

# Descobre o JAVA_HOME real de um java.exe (resolve shims como o javapath da Oracle).
function Get-JavaHomeFromExe([string]$javaExe) {
    return (Get-JavaProps $javaExe).Home
}

# Procura um JDK >= 21 em locais comuns e no JAVA_HOME/PATH. Retorna o diretorio (JAVA_HOME).
function Find-Jdk21 {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    # Raizes comuns por fornecedor + JDKs do IntelliJ (~/.jdks) + Program Files (x86).
    $roots = @(
        "$env:ProgramFiles\Microsoft",
        "$env:ProgramFiles\Eclipse Adoptium",
        "$env:ProgramFiles\Java",
        "$env:ProgramFiles\Zulu",
        "$env:ProgramFiles\Amazon Corretto",
        "$env:ProgramFiles\BellSoft",
        "$env:ProgramFiles\Semeru",
        "${env:ProgramFiles(x86)}\Java",
        "$env:LOCALAPPDATA\Programs\Eclipse Adoptium",
        "$env:USERPROFILE\.jdks"
    )
    foreach ($r in $roots) {
        if ($r -and (Test-Path $r)) {
            # Sem filtro 'jdk*': aceita corretto-21, temurin-21, openjdk-21 etc.
            $candidates += (Get-ChildItem -Path $r -Directory -ErrorAction SilentlyContinue |
                Sort-Object Name -Descending | ForEach-Object { $_.FullName })
        }
    }
    foreach ($c in $candidates) {
        $exe = Join-Path $c 'bin\java.exe'
        if (Test-Path $exe) {
            if ((Get-JavaMajor $exe) -ge 21) { return $c }
        }
    }
    # Varre cada diretorio do PATH atual atras de um java.exe (cobre instalacoes fora das
    # raizes acima, desde que estejam no PATH herdado pelo processo).
    foreach ($dir in ($env:Path -split ';')) {
        if (-not $dir) { continue }
        $exe = Join-Path $dir 'java.exe'
        if (Test-Path $exe) {
            if ((Get-JavaMajor $exe) -ge 21) {
                $jh = Get-JavaHomeFromExe $exe
                if ($jh -and (Test-Path (Join-Path $jh 'bin\java.exe'))) { return $jh }
                return (Split-Path $dir -Parent)   # <dir>=...\bin -> JAVA_HOME
            }
        }
    }
    # por ultimo, o java do PATH via Get-Command (o mesmo que voce roda no terminal).
    # E o caso classico do Oracle javapath: 'java --version' mostra 21, mas o Source e um
    # shim em ...\Common Files\Oracle\Java\javapath\java.exe. Uma unica chamada de
    # Get-JavaProps devolve versao E java.home reais.
    $onPath = Get-Command java.exe -ErrorAction SilentlyContinue
    if (-not $onPath) { $onPath = Get-Command java -ErrorAction SilentlyContinue }
    if ($onPath) {
        $props = Get-JavaProps $onPath.Source
        if ($props.Major -ge 21 -and $props.Home -and (Test-Path (Join-Path $props.Home 'bin\java.exe'))) {
            return $props.Home
        }
    }
    return $null
}

function Ensure-Jdk21 {
    Write-Step 'Verificando JDK 21'

    # 1) Caminho informado explicitamente (-JavaHome) tem prioridade. Aceita tres formas:
    #    a) a raiz do JDK   (tem bin\java.exe)          -> ex.: C:\Program Files\Java\jdk-21.0.2
    #    b) a pasta bin     (tem java.exe direto)       -> ex.: C:\Program Files\Java\jdk-21.0.2\bin
    #    c) uma pasta-mae   (contem subpastas jdk-*)    -> ex.: C:\Program Files\Java
    if ($JavaHome) {
        $tries = @($JavaHome)
        if (Test-Path $JavaHome) {
            $tries += (Get-ChildItem -Path $JavaHome -Directory -ErrorAction SilentlyContinue |
                Sort-Object Name -Descending | ForEach-Object { $_.FullName })
        }
        foreach ($t in $tries) {
            $exe = Join-Path $t 'bin\java.exe'
            if ((Test-Path $exe) -and (Get-JavaMajor $exe) -ge 21) {
                Write-Ok "JDK 21 (via -JavaHome): $t"; return $t
            }
            $exeBin = Join-Path $t 'java.exe'   # caso $t seja a propria pasta bin
            if ((Test-Path $exeBin) -and (Get-JavaMajor $exeBin) -ge 21) {
                $jh = Split-Path $t -Parent
                Write-Ok "JDK 21 (via -JavaHome): $jh"; return $jh
            }
        }
        Write-Warn2 "-JavaHome informado ('$JavaHome') nao tem um java.exe 21+ valido; tentando detectar."
    }

    # 2) Deteccao automatica (JAVA_HOME, raizes de fornecedores, PATH).
    $home21 = Find-Jdk21
    if ($home21) { Write-Ok "JDK 21 encontrado: $home21"; return $home21 }

    # 3) Nao achou NESTE contexto. Se voce ja tem Java 21 (aparece em 'java --version'),
    #    quase sempre e PATH: rode na MESMA PowerShell onde o java funciona, ou passe -JavaHome.
    Write-Warn2 'JDK 21 nao encontrado neste contexto (PATH/perfil do processo).'
    Write-Info  'Ja tem Java 21? Rode a partir da PowerShell onde "java --version" funciona,'
    Write-Info  'ou informe o caminho:  install.cmd -JavaHome "C:\caminho\do\jdk-21"'
    Write-Warn2 'Tentando instalar via winget (Microsoft.OpenJDK.21) como ultimo recurso...'
    if (-not (Get-Command winget -ErrorAction SilentlyContinue)) {
        throw "winget indisponivel e Java 21 nao localizado. Rode na PowerShell onde 'java --version' funciona, ou use -JavaHome ""C:\caminho\do\jdk-21""."
    }
    winget install --id Microsoft.OpenJDK.21 -e --silent `
        --accept-package-agreements --accept-source-agreements | Out-Host

    $home21 = Find-Jdk21
    if (-not $home21) {
        throw "JDK instalado mas nao localizado. Feche/reabra o terminal e rode de novo, ou defina JAVA_HOME."
    }
    Write-Ok "JDK 21 instalado: $home21"
    return $home21
}

function Ensure-Maven([string]$projectRoot) {
    Write-Step 'Verificando Maven'
    $mvnOnPath = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if (-not $mvnOnPath) { $mvnOnPath = Get-Command mvn -ErrorAction SilentlyContinue }
    if ($mvnOnPath) { Write-Ok "Maven encontrado no PATH: $($mvnOnPath.Source)"; return $mvnOnPath.Source }

    $toolsDir = Join-Path $projectRoot '.tools'
    $mvnHome  = Join-Path $toolsDir "apache-maven-$MavenVersion"
    $mvnCmd   = Join-Path $mvnHome 'bin\mvn.cmd'
    if (Test-Path $mvnCmd) { Write-Ok "Maven local ja presente: $mvnCmd"; return $mvnCmd }

    Write-Warn2 "Maven nao encontrado. Baixando Apache Maven $MavenVersion (uma vez)..."
    New-Item -ItemType Directory -Force -Path $toolsDir | Out-Null
    $zip = Join-Path $toolsDir "apache-maven-$MavenVersion-bin.zip"
    $urls = @(
        "https://dlcdn.apache.org/maven/maven-3/$MavenVersion/binaries/apache-maven-$MavenVersion-bin.zip",
        "https://archive.apache.org/dist/maven/maven-3/$MavenVersion/binaries/apache-maven-$MavenVersion-bin.zip"
    )
    $downloaded = $false
    foreach ($u in $urls) {
        try {
            Write-Info "baixando de $u"
            Invoke-WebRequest -Uri $u -OutFile $zip -UseBasicParsing
            $downloaded = $true; break
        } catch { Write-Warn2 "falhou nesse espelho, tentando o proximo..." }
    }
    if (-not $downloaded) { throw "Nao consegui baixar o Maven. Verifique a internet/proxy." }

    Expand-Archive -Path $zip -DestinationPath $toolsDir -Force
    Remove-Item $zip -Force
    if (-not (Test-Path $mvnCmd)) { throw "Maven extraido mas mvn.cmd nao encontrado em $mvnCmd" }
    Write-Ok "Maven baixado em: $mvnHome"
    return $mvnCmd
}

# --------------------------------------------------------------------- main ----
$projectRoot = $PSScriptRoot
Write-Host "===================================================================" -ForegroundColor White
Write-Host "  Instalador - Millenium Agente IA (MCP Fase 1)" -ForegroundColor White
Write-Host "  Pasta do projeto: $projectRoot" -ForegroundColor White
Write-Host "  Requisito unico: Java 21 (Fase 1 le Excel local; SEM MySQL/banco)." -ForegroundColor White
Write-Host "===================================================================" -ForegroundColor White

if (-not (Test-Path (Join-Path $projectRoot 'pom.xml'))) {
    throw "pom.xml nao encontrado em $projectRoot. Rode o script de dentro da pasta do projeto."
}

# alerta se estiver rodando de um lugar temporario
if ($projectRoot -match '\\(Temp|Downloads|Download)\\') {
    Write-Warn2 "A pasta parece temporaria ($projectRoot)."
    Write-Warn2 "Mova o projeto para um local permanente (ex.: C:\Millenium\) e rode de novo,"
    Write-Warn2 "senao o caminho do jar na config do Claude vai quebrar depois."
    $r = Read-Host "Continuar mesmo assim? (s/N)"
    if ($r -ne 's' -and $r -ne 'S') { Write-Host 'Cancelado.'; return }
}

$jdkHome = Ensure-Jdk21
$javaExe = Join-Path $jdkHome 'bin\java.exe'
$env:JAVA_HOME = $jdkHome
$env:Path = "$jdkHome\bin;$env:Path"

$mvnCmd = Ensure-Maven $projectRoot

# --- build ---
$jarPath = Join-Path $projectRoot 'target\millenium-agente-ia-0.1.0-SNAPSHOT.jar'
if ($SkipBuild -and (Test-Path $jarPath)) {
    Write-Step 'Build pulado (-SkipBuild); usando jar existente'
} else {
    Write-Step 'Compilando e empacotando (mvn clean package)'
    Write-Info 'A primeira vez baixa as dependencias e pode demorar alguns minutos...'
    Push-Location $projectRoot
    try {
        & $mvnCmd '-B' 'clean' 'package'
        if ($LASTEXITCODE -ne 0) { throw "Falha no 'mvn clean package' (exit $LASTEXITCODE)." }
    } finally { Pop-Location }
    if (-not (Test-Path $jarPath)) { throw "Build terminou mas o jar nao foi gerado: $jarPath" }
    Write-Ok "Jar gerado: $jarPath"
}

# --- pastas NEUTRAS (previsiveis, independentes do caminho do projeto) ---
# O MCP apenas LE destas pastas - nao gera nada.
$fonteDir = Join-Path $env:USERPROFILE 'MilleniumAgenteIA\fonte'
$wikiDir  = Join-Path $env:USERPROFILE 'MilleniumAgenteIA\wiki'

Write-Step "Preparando a pasta de fontes (.xlsx): $fonteDir"
New-Item -ItemType Directory -Force -Path $fonteDir | Out-Null
$temXlsx = Get-ChildItem -Path $fonteDir -Filter '*.xlsx' -ErrorAction SilentlyContinue | Select-Object -First 1
if ($temXlsx) {
    Write-Ok "Fontes ja presentes em: $fonteDir"
} else {
    $xlsx = Get-ChildItem -Path (Join-Path $projectRoot 'fonte') -Filter '*.xlsx' -File -ErrorAction SilentlyContinue
    if ($xlsx) {
        Copy-Item $xlsx.FullName $fonteDir -Force
        Write-Ok "$($xlsx.Count) planilha(s) ficticia(s) copiada(s) para: $fonteDir"
    } else {
        Write-Warn2 "Nenhum .xlsx para copiar. Coloque as 5 planilhas (Contatos, Sistema, Saidas por NF, Megazap, NPS) em: $fonteDir"
    }
}
Write-Info "Para dados reais, substitua as 5 planilhas em $fonteDir mantendo os nomes do field mapping"
Write-Info "(N1 Contatos, N1 Sistema Contratado, N1 Saidas por NF, Megazap, NPS). Nenhum banco de dados e necessario."

Write-Step "Preparando a pasta da wiki (editavel): $wikiDir"
New-Item -ItemType Directory -Force -Path $wikiDir | Out-Null
$origemWiki = Join-Path $projectRoot 'wiki'
if ((Get-ChildItem -Path $wikiDir -Recurse -Filter '*.md' -ErrorAction SilentlyContinue | Select-Object -First 1)) {
    Write-Ok "Wiki ja presente em: $wikiDir"
} elseif (Test-Path $origemWiki) {
    Copy-Item (Join-Path $origemWiki '*') $wikiDir -Recurse -Force
    Write-Ok "Wiki copiada para: $wikiDir (edite os .md aqui, sem rebuild)"
} else {
    Write-Warn2 "Pasta wiki do projeto nao encontrada; o MCP usara a wiki empacotada no jar."
}

# --- config do Claude Desktop ---
Write-Step 'Registrando o MCP no Claude Desktop'
$claudeDir  = Join-Path $env:APPDATA 'Claude'
$configPath = Join-Path $claudeDir 'claude_desktop_config.json'

if (-not (Test-Path $claudeDir)) {
    Write-Warn2 "Pasta do Claude Desktop nao existe ($claudeDir)."
    Write-Warn2 "O Claude Desktop parece nao estar instalado. Vou criar a config mesmo assim;"
    Write-Warn2 "instale o Claude Desktop depois e a config ja estara pronta."
    New-Item -ItemType Directory -Force -Path $claudeDir | Out-Null
}

if (Test-Path $configPath) {
    $backup = "$configPath.bak-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
    Copy-Item $configPath $backup -Force
    Write-Info "Backup da config atual: $backup"
    try {
        $cfg = Get-Content -Raw -Path $configPath | ConvertFrom-Json
    } catch {
        throw "O arquivo $configPath existe mas nao e um JSON valido. Corrija ou remova antes de rodar."
    }
} else {
    $cfg = [pscustomobject]@{}
}

# garante o objeto mcpServers sem apagar servidores existentes
if (-not ($cfg.PSObject.Properties.Name -contains 'mcpServers') -or ($null -eq $cfg.mcpServers)) {
    $cfg | Add-Member -NotePropertyName 'mcpServers' -NotePropertyValue ([pscustomobject]@{}) -Force
}

$outputDir = Join-Path $env:USERPROFILE 'MilleniumAgenteIA\saidas'
$entry = [ordered]@{
    command = $javaExe
    args    = @(
        '-jar',
        $jarPath,
        "--millenium.fonte-dir=$fonteDir",
        "--millenium.wiki-dir=$wikiDir",
        "--millenium.output-dir=$outputDir"
    )
}
# adiciona/atualiza SO a chave do nosso servidor
$cfg.mcpServers | Add-Member -NotePropertyName 'millenium-agente-ia' -NotePropertyValue $entry -Force

# grava UTF-8 SEM BOM (o parser do Claude nao aceita BOM)
$json = $cfg | ConvertTo-Json -Depth 100
[System.IO.File]::WriteAllText($configPath, $json, (New-Object System.Text.UTF8Encoding($false)))
Write-Ok "Config atualizada: $configPath"
Write-Info "command: $javaExe"
Write-Info "jar    : $jarPath"

# --- Instruction / Skills (parte manual no Projeto do Claude) ---
$agentesDir = Join-Path $projectRoot 'agentes'
Write-Step 'Skills e Instruction (para colar no Projeto do Claude)'
Write-Info "Instruction (regras/LGPD): $agentesDir\instrucao-guardrails.md"
Write-Info "Skill Preventivo        : $agentesDir\preventivo\SKILL.md"
Write-Info "Skill Pos-NPS           : $agentesDir\pos-nps\SKILL.md"
Write-Info "Skill Exportar cadastro : $agentesDir\exportar-cadastro\SKILL.md  (gatilho: 'gerar excel')"
Write-Info "A wiki NAO precisa instalar: o agente le pela tool consultar_wiki."

# --- fim ---
Write-Host "`n===================================================================" -ForegroundColor Green
Write-Host "  INSTALACAO CONCLUIDA" -ForegroundColor Green
Write-Host "===================================================================" -ForegroundColor Green
Write-Host @"

Proximos passos (manuais, no app do Claude):
  1. FECHE o Claude Desktop pela bandeja do sistema (botao direito -> Sair) e abra de novo.
  2. Confirme que 'millenium-agente-ia' aparece conectado com 11 tools.
  3. Crie/abra um Projeto no Claude e:
       - cole o conteudo de agentes\INSTRUCTION-lgpd.md nas Instrucoes do Projeto;
       - cadastre as Skills agentes\SKILL-preventivo.md e agentes\SKILL-pos-nps.md.
  4. Teste no chat: "chame a tool preventivo_contatos".

Log do MCP (se precisar depurar):
  %APPDATA%\Claude\logs\mcp-server-millenium-agente-ia.log
"@ -ForegroundColor White

if (-not $NoOpenFolder -and (Test-Path $agentesDir)) {
    Start-Process explorer.exe $agentesDir
}
