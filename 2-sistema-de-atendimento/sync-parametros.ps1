<#
  Sincroniza os limiares: FONTE UNICA = wiki\parametros.md  ->  src\main\resources\application.properties.
  Edite os numeros SOMENTE no parametros.md. Rode este script (ou o install.ps1) antes do 'mvn package'.
#>
[CmdletBinding()]
param([string]$ProjectRoot = $PSScriptRoot)
$ErrorActionPreference = 'Stop'

$md    = Join-Path $ProjectRoot 'wiki\parametros.md'
$props = Join-Path $ProjectRoot 'src\main\resources\application.properties'
if (-not (Test-Path $md))    { throw "Nao encontrei $md" }
if (-not (Test-Path $props)) { throw "Nao encontrei $props" }

$keys = @(
  'millenium.antiguidade-limiar-meses',
  'millenium.inatividade-limiar-meses',
  'millenium.preventiva-janela-meses',
  'millenium.peso-recencia',
  'millenium.peso-antiguidade',
  'millenium.peso-monetizacao'
)
$dropComments = @(
  '# Limiares comerciais (parametros.md - a calibrar com o comercial).',
  '# Gatilho B: preventiva vencida = sem visita ha >= N meses (proxy; nao ha coluna de preventiva).',
  '# Pesos da priorizacao RFM.'
)
$begin = '# >>> GERADO A PARTIR DE wiki/parametros.md - NAO EDITAR (rode sync-parametros / install.ps1) >>>'
$end   = '# <<< FIM GERADO <<<'

# 1) le os valores do markdown (fonte unica)
$mdText = Get-Content -Raw -Path $md
$vals = [ordered]@{}
foreach ($k in $keys) {
    $m = [regex]::Match($mdText, [regex]::Escape($k) + '\s*=\s*([0-9]+(?:\.[0-9]+)?)')
    if (-not $m.Success) { throw "Chave ausente no parametros.md: $k" }
    $vals[$k] = $m.Groups[1].Value
}

# 2) remove linhas antigas (chaves, comentarios e marcadores) e anexa a regiao gerada
$kept = New-Object System.Collections.Generic.List[string]
foreach ($line in (Get-Content -Path $props)) {
    $t = $line.Trim()
    if ($dropComments -contains $t) { continue }
    if ($t -eq $begin -or $t -eq $end) { continue }
    $isKey = $false
    foreach ($k in $keys) { if ($t -match ('^' + [regex]::Escape($k) + '\s*=')) { $isKey = $true; break } }
    if ($isKey) { continue }
    $kept.Add($line)
}
while ($kept.Count -gt 0 -and [string]::IsNullOrWhiteSpace($kept[$kept.Count-1])) { $kept.RemoveAt($kept.Count-1) }

$region = @($begin) + ($keys | ForEach-Object { "$_=$($vals[$_])" }) + @($end)
$final  = (($kept -join "`r`n") + "`r`n`r`n" + ($region -join "`r`n") + "`r`n")
[System.IO.File]::WriteAllText($props, $final, (New-Object System.Text.UTF8Encoding($false)))
Write-Host ("Sincronizado: {0} parametros de parametros.md -> application.properties" -f $keys.Count)
foreach ($k in $keys) { Write-Host ("  {0}={1}" -f $k, $vals[$k]) }
