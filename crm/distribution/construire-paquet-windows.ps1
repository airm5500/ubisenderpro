<#
.SYNOPSIS
  Construit le paquet d'installation Windows du CRM UBIPHARM.

.DESCRIPTION
  A lancer sur un poste Windows 10/11 64 bits relie a Internet, depuis une
  copie du depot (Git pour Windows requis : une dependance du compagnon
  WhatsApp se recupere par Git). Le script :
    1. recupere un JDK 25 (Temurin) et un Node.js portable, garde en cache ;
    2. construit l'interface Angular puis le jar du CRM ;
    3. reduit le JDK a un Java embarque (jlink) ;
    4. prepare le compagnon WhatsApp (node.exe + dependances) ;
    5. ajoute WinSW (services Windows), les scripts et le guide ;
    6. produit distribution\paquet\UBIPHARM-CRM-<version>.zip.

  Rien n'est a installer sur le poste d'UBIPHARM hormis MariaDB : le paquet
  embarque Java et Node.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File crm\distribution\construire-paquet-windows.ps1

.EXAMPLE
  # Sans telechargement : JDK 25 et WinSW deja presents sur le poste
  .\construire-paquet-windows.ps1 -Jdk "C:\Java\jdk-25" -WinSW "C:\outils\WinSW-x64.exe"
#>
[CmdletBinding()]
param(
    # JDK 25 pour Windows x64 ; a defaut JAVA_HOME s'il est en version 25, sinon Temurin 25 est telecharge.
    [string]$Jdk,
    # Version de Node.js embarquee pour le compagnon WhatsApp (LTS).
    [string]$VersionNode = '24.22.0',
    # Dossier contenant node et npm pour la construction ; a defaut le Node portable telecharge.
    [string]$NodeConstruction,
    # WinSW-x64.exe (2.12) ; a defaut telecharge depuis GitHub.
    [string]$WinSW,
    # Dossier de sortie du paquet.
    [string]$Sortie
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

# $IsWindows n'existe qu'a partir de PowerShell 6 : absent = Windows PowerShell 5.1
$SurWindows = ($IsWindows -ne $false)
$Exe = if ($SurWindows) { '.exe' } else { '' }

$Distribution = $PSScriptRoot
$Crm = Split-Path $Distribution -Parent
$Cache = Join-Path $Distribution 'cache'
if (-not $Sortie) { $Sortie = Join-Path $Distribution 'paquet' }
$UrlWinSW = 'https://github.com/winsw/winsw/releases/download/v2.12.0/WinSW-x64.exe'
$UrlJdk = 'https://api.adoptium.net/v3/binary/latest/25/ga/windows/x64/jdk/hotspot/normal/eclipse'
# Modules Java utilises par le CRM (jdeps sur le jar, plus les fournisseurs charges a l'execution :
# JNDI et DNS, jeux de caracteres, cryptographie TLS, formats regionaux, zip)
$Modules = 'java.base,java.compiler,java.desktop,java.instrument,java.management,java.naming,java.net.http,' +
    'java.prefs,java.rmi,java.scripting,java.security.jgss,java.sql,java.sql.rowset,java.xml.crypto,jdk.charsets,' +
    'jdk.crypto.ec,jdk.jfr,jdk.localedata,jdk.management,jdk.naming.dns,jdk.unsupported,jdk.zipfs'

function Etape([string]$texte) { Write-Host ''; Write-Host "==> $texte" -ForegroundColor Cyan }

function Telecharger([string]$url, [string]$fichier) {
    $cible = Join-Path $Cache $fichier
    if (Test-Path $cible) { Write-Host "    deja en cache : $fichier"; return $cible }
    Write-Host "    telechargement : $url"
    New-Item -ItemType Directory -Force $Cache | Out-Null
    Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile "$cible.part"
    Move-Item "$cible.part" $cible
    return $cible
}

function Extraire([string]$zip, [string]$dossier) {
    if (-not (Test-Path $dossier)) {
        Write-Host "    extraction : $(Split-Path $zip -Leaf)"
        Expand-Archive -Path $zip -DestinationPath $dossier
    }
    # Les archives JDK et Node contiennent un seul dossier racine
    return (Get-ChildItem $dossier -Directory | Select-Object -First 1).FullName
}

function Lancer([string]$commande, [string[]]$arguments, [string]$dossier) {
    Push-Location $dossier
    try {
        & $commande @arguments
        if ($LASTEXITCODE -ne 0) { throw "Echec ($LASTEXITCODE) : $commande $($arguments -join ' ')" }
    } finally { Pop-Location }
}

function VersionJava([string]$jdk) {
    $release = Join-Path $jdk 'release'
    if (-not (Test-Path $release)) { return '' }
    $ligne = Select-String -Path $release -Pattern '^JAVA_VERSION="([^"]+)"' | Select-Object -First 1
    if ($ligne) { return $ligne.Matches[0].Groups[1].Value } else { return '' }
}

$version = ([xml](Get-Content (Join-Path $Crm 'pom.xml'))).project.version
$nom = "UBIPHARM-CRM-$version"
# Dossier racine sans numero de version : extrait dans C:\, il donne C:\UBIPHARM-CRM\application,
# a cote du dossier donnees qui survit aux mises a jour
$racinePaquet = Join-Path $Sortie 'UBIPHARM-CRM'
$app = Join-Path $racinePaquet 'application'
Write-Host "Paquet $nom -> $Sortie"

# --- 1. Outils ------------------------------------------------------------------
Etape 'JDK 25'
if (-not $Jdk -and $env:JAVA_HOME -and (VersionJava $env:JAVA_HOME) -like '25*') { $Jdk = $env:JAVA_HOME }
if (-not $Jdk) {
    $zipJdk = Telecharger $UrlJdk 'temurin-25-windows-x64.zip'
    $Jdk = Extraire $zipJdk (Join-Path $Cache 'jdk')
}
$versionJava = VersionJava $Jdk
if ($versionJava -notlike '25*') { throw "JDK 25 attendu dans $Jdk (trouve : '$versionJava')." }
Write-Host "    $Jdk ($versionJava)"

Etape "Node.js $VersionNode portable"
$zipNode = Telecharger "https://nodejs.org/dist/v$VersionNode/node-v$VersionNode-win-x64.zip" "node-v$VersionNode-win-x64.zip"
$nodePortable = Extraire $zipNode (Join-Path $Cache "node-$VersionNode")
if (-not $NodeConstruction) { $NodeConstruction = $nodePortable }
$npm = if ($SurWindows) { Join-Path $NodeConstruction 'npm.cmd' } else { Join-Path $NodeConstruction 'npm' }
$npx = if ($SurWindows) { Join-Path $NodeConstruction 'npx.cmd' } else { Join-Path $NodeConstruction 'npx' }
# npm lance node par le PATH
$env:PATH = $NodeConstruction + [IO.Path]::PathSeparator + $env:PATH

Etape 'WinSW 2.12'
if (-not $WinSW) { $WinSW = Telecharger $UrlWinSW 'WinSW-x64.exe' }
if (-not (Test-Path $WinSW)) { throw "WinSW introuvable : $WinSW" }

& git --version | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Git est requis (dependance libsignal du compagnon WhatsApp) : installez Git pour Windows.' }

# --- 2. Interface et jar ------------------------------------------------------------
Etape 'Interface Angular'
$front = Join-Path $Crm 'front'
Lancer $npm @('ci', '--no-audit', '--no-fund') $front
Lancer $npx @('ng', 'build') $front

Etape 'Jar du CRM (tests ignores : ils demandent une base MariaDB, lancez-les a part)'
$env:JAVA_HOME = $Jdk
$mvnw = if ($SurWindows) { Join-Path $Crm 'mvnw.cmd' } else { Join-Path $Crm 'mvnw' }
Lancer $mvnw @('-B', '-q', 'clean', 'package', '-DskipTests') $Crm
$jar = Get-ChildItem (Join-Path $Crm 'target') -Filter '*.jar' | Where-Object { $_.Name -notlike '*-plain.jar' } | Select-Object -First 1
if (-not $jar) { throw 'Jar introuvable dans target.' }

# --- 3. Assemblage ------------------------------------------------------------------
Etape 'Assemblage du dossier application'
if (Test-Path $racinePaquet) { Remove-Item -Recurse -Force $racinePaquet }
New-Item -ItemType Directory -Force $app | Out-Null
Copy-Item $jar.FullName (Join-Path $app 'crm.jar')

Write-Host '    Java embarque (jlink)'
$jlink = Join-Path (Join-Path $Jdk 'bin') "jlink$Exe"
Lancer $jlink @(
    '--add-modules', $Modules, '--strip-debug', '--no-header-files', '--no-man-pages', '--compress', 'zip-6',
    '--output', (Join-Path $app 'runtime')) $Crm

Write-Host '    Node.js et compagnon WhatsApp'
New-Item -ItemType Directory -Force (Join-Path $app 'node') | Out-Null
Copy-Item (Join-Path $nodePortable 'node.exe') (Join-Path $app 'node')
Copy-Item (Join-Path $nodePortable 'LICENSE') (Join-Path $app 'node')
$waWeb = Join-Path $app 'wa-web'
New-Item -ItemType Directory -Force $waWeb | Out-Null
foreach ($f in 'server.js', 'quiet.js', 'package.json', 'package-lock.json', 'README.md') {
    $source = Join-Path (Join-Path $Crm 'wa-web') $f
    if (Test-Path $source) { Copy-Item $source $waWeb }
}
# Les dependances optionnelles de Baileys (sharp, jimp...) ne servent pas aux relances
Lancer $npm @('install', '--omit=dev', '--legacy-peer-deps', '--no-audit', '--no-fund') $waWeb

Write-Host '    Services, scripts et guide'
$modele = Join-Path $Distribution 'modele'
Copy-Item $WinSW (Join-Path $app 'crm-service.exe')
Copy-Item $WinSW (Join-Path $app 'wa-web-service.exe')
Copy-Item (Join-Path $modele 'crm-service.xml') $app
Copy-Item (Join-Path $modele 'wa-web-service.xml') $app
foreach ($f in 'installer.bat', 'desinstaller.bat') {
    # Fins de ligne Windows, quelle que soit la configuration Git du poste de construction
    $texte = [IO.File]::ReadAllText((Join-Path $modele $f)) -replace "`r?`n", "`r`n"
    [IO.File]::WriteAllText((Join-Path $app $f), $texte)
}
New-Item -ItemType Directory -Force (Join-Path $app 'modeles') | Out-Null
Copy-Item (Join-Path $modele 'application-prod.yml') (Join-Path $app 'modeles')
Copy-Item (Join-Path $modele 'wa-web.env') (Join-Path $app 'modeles')
Copy-Item (Join-Path $Distribution 'INSTALLATION.md') $app

$commit = (& git -C $Crm rev-parse --short HEAD) 2>$null
@(
    "UBIPHARM CRM $version",
    "Commit       : $commit",
    "Construit le : $((Get-Date).ToString('yyyy-MM-dd HH:mm'))",
    "Java         : $versionJava (reduit par jlink)",
    "Node.js      : $VersionNode"
) | Set-Content -Encoding ASCII (Join-Path $app 'VERSION.txt')

# --- 4. Archive -----------------------------------------------------------------------
Etape 'Archive zip'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = Join-Path $Sortie "$nom.zip"
if (Test-Path $zip) { Remove-Item -Force $zip }
[IO.Compression.ZipFile]::CreateFromDirectory($racinePaquet, $zip, [IO.Compression.CompressionLevel]::Optimal, $true)
$taille = [math]::Round((Get-Item $zip).Length / 1MB)
Write-Host ''
Write-Host "Paquet pret : $zip ($taille Mo)" -ForegroundColor Green
Write-Host 'Sur le poste UBIPHARM : extraire dans C:\ (donne C:\UBIPHARM-CRM\application) puis suivre INSTALLATION.md.'
