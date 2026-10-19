<#
.SYNOPSIS
  Met a jour le CRM UBIPHARM deja installe sur ce poste, depuis la copie du depot.

.DESCRIPTION
  Pour suivre les corrections sans reconstruire ni reinstaller le paquet :
    1. recupere les dernieres modifications (git pull) ;
    2. reconstruit l'interface et le jar avec le JDK et le Node gardes en cache
       par construire-paquet-windows.ps1 (a lancer une fois avant) ;
    3. arrete le service CRM, remplace crm.jar et les fichiers des services,
       met a jour le compagnon WhatsApp s'il a change, puis redemarre.
  Le dossier donnees (configuration, journaux, session WhatsApp) et la base
  ne sont pas touches ; la base se met a jour seule au demarrage.

.EXAMPLE
  # En administrateur, depuis la copie du depot
  crm\distribution\mettre-a-jour.bat
#>
[CmdletBinding()]
param(
    # Dossier d'installation (celui qui contient application et donnees).
    [string]$Installation = 'C:\UBIPHARM-CRM',
    # Ne pas lancer git pull (modifications locales a tester).
    [switch]$SansPull
)

$ErrorActionPreference = 'Stop'
$Distribution = $PSScriptRoot
$Crm = Split-Path $Distribution -Parent
$Cache = Join-Path $Distribution 'cache'
$App = Join-Path $Installation 'application'
$Donnees = Join-Path $Installation 'donnees'

function Etape([string]$texte) { Write-Host ''; Write-Host "==> $texte" -ForegroundColor Cyan }

function Lancer([string]$commande, [string[]]$arguments, [string]$dossier) {
    Push-Location $dossier
    try {
        & $commande @arguments
        if ($LASTEXITCODE -ne 0) { throw "Echec ($LASTEXITCODE) : $commande $($arguments -join ' ')" }
    } finally { Pop-Location }
}

function Identique([string]$a, [string]$b) {
    (Test-Path $a) -and (Test-Path $b) -and ((Get-FileHash $a).Hash -eq (Get-FileHash $b).Hash)
}

$admin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole(
    [Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $admin) { throw 'Lancez la mise a jour en administrateur (clic droit > Executer en tant qu''administrateur).' }
if (-not (Test-Path (Join-Path $App 'crm-service.exe'))) {
    throw "Aucune installation dans $App : installez d'abord le paquet (INSTALLATION.md)."
}
$jdk = Get-ChildItem (Join-Path $Cache 'jdk') -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
$node = Get-ChildItem $Cache -Directory -Filter 'node-*' -ErrorAction SilentlyContinue | Sort-Object Name -Descending |
    ForEach-Object { Get-ChildItem $_.FullName -Directory | Select-Object -First 1 } | Select-Object -First 1
if (-not $jdk -or -not $node) {
    throw 'JDK ou Node absent du cache : lancez une fois construire-paquet-windows.ps1, puis relancez la mise a jour.'
}

# --- 1. Sources ------------------------------------------------------------------
if (-not $SansPull) {
    Etape 'Recuperation des modifications (git pull)'
    Lancer 'git' @('pull', '--ff-only') $Crm
}
$commit = (& git -C $Crm rev-parse --short HEAD) 2>$null

# --- 2. Construction -------------------------------------------------------------
Etape 'Interface Angular'
$env:PATH = $node.FullName + [IO.Path]::PathSeparator + $env:PATH
$front = Join-Path $Crm 'front'
if (-not (Test-Path (Join-Path $front 'node_modules'))) {
    Lancer (Join-Path $node.FullName 'npm.cmd') @('ci', '--no-audit', '--no-fund') $front
}
Lancer (Join-Path $node.FullName 'npx.cmd') @('ng', 'build') $front

Etape 'Jar du CRM'
$env:JAVA_HOME = $jdk.FullName
Lancer (Join-Path $Crm 'mvnw.cmd') @('-B', '-q', 'clean', 'package', '-DskipTests') $Crm
$jar = Get-ChildItem (Join-Path $Crm 'target') -Filter '*.jar' | Where-Object { $_.Name -notlike '*-plain.jar' } | Select-Object -First 1
if (-not $jar) { throw 'Jar introuvable dans target.' }

# --- 3. Remplacement -------------------------------------------------------------
$modele = Join-Path $Distribution 'modele'
$waSource = Join-Path $Crm 'wa-web'
$waCible = Join-Path $App 'wa-web'
$fichiersWa = 'server.js', 'quiet.js', 'package.json', 'package-lock.json'
$waChange = ($fichiersWa | Where-Object { (Test-Path (Join-Path $waSource $_)) -and -not (Identique (Join-Path $waSource $_) (Join-Path $waCible $_)) }).Count -gt 0
$xmlWaChange = -not (Identique (Join-Path $modele 'wa-web-service.xml') (Join-Path $App 'wa-web-service.xml'))

Etape 'Arret du CRM'
& (Join-Path $App 'crm-service.exe') stop
if ($waChange -or $xmlWaChange) { & (Join-Path $App 'wa-web-service.exe') stop }
Start-Sleep -Seconds 3

Etape 'Remplacement des fichiers'
$ancien = Join-Path $App 'crm.jar'
if (Test-Path $ancien) { Copy-Item $ancien (Join-Path $App 'crm.jar.precedent') -Force }
Copy-Item $jar.FullName $ancien -Force
# Les services relisent leur fichier .xml a chaque demarrage
Copy-Item (Join-Path $modele 'crm-service.xml') $App -Force
Copy-Item (Join-Path $modele 'wa-web-service.xml') $App -Force
foreach ($f in 'installer.bat', 'desinstaller.bat') {
    $texte = [IO.File]::ReadAllText((Join-Path $modele $f)) -replace "`r?`n", "`r`n"
    [IO.File]::WriteAllText((Join-Path $App $f), $texte)
}
Copy-Item (Join-Path $Distribution 'INSTALLATION.md') $App -Force
if ($waChange) {
    Write-Host '    compagnon WhatsApp modifie : mise a jour'
    $dependances = -not (Identique (Join-Path $waSource 'package-lock.json') (Join-Path $waCible 'package-lock.json'))
    foreach ($f in $fichiersWa) { if (Test-Path (Join-Path $waSource $f)) { Copy-Item (Join-Path $waSource $f) $waCible -Force } }
    if ($dependances) {
        Lancer (Join-Path $node.FullName 'npm.cmd') @('install', '--omit=dev', '--legacy-peer-deps', '--no-audit', '--no-fund') $waCible
    }
}
$version = Join-Path $App 'VERSION.txt'
Add-Content -Encoding ASCII $version "Mis a jour : $((Get-Date).ToString('yyyy-MM-dd HH:mm')) (commit $commit)"

# --- 4. Redemarrage --------------------------------------------------------------
Etape 'Redemarrage'
if ($waChange -or $xmlWaChange) { & (Join-Path $App 'wa-web-service.exe') start }
& (Join-Path $App 'crm-service.exe') start
$port = 8080
$ligne = Select-String -Path (Join-Path $Donnees 'application-prod.yml') -Pattern '^  port: *([0-9]+)' | Select-Object -First 1
if ($ligne) { $port = [int]$ligne.Matches[0].Groups[1].Value }
Write-Host "    attente du CRM sur le port $port..."
for ($i = 0; $i -lt 60; $i++) {
    try {
        Invoke-WebRequest -UseBasicParsing -TimeoutSec 3 "http://localhost:$port/api/auth/csrf" | Out-Null
        Write-Host ''
        Write-Host "CRM a jour (commit $commit) : http://localhost:$port" -ForegroundColor Green
        Write-Host 'Dans le navigateur, rechargez la page (Ctrl+F5) pour prendre la nouvelle interface.'
        exit 0
    } catch { Start-Sleep -Seconds 2 }
}
Write-Host ''
Write-Host "Le CRM ne repond pas : voir $Donnees\logs\crm.log." -ForegroundColor Yellow
Write-Host "Pour revenir a la version precedente : arreter le service, remplacer crm.jar par crm.jar.precedent, redemarrer."
exit 1
