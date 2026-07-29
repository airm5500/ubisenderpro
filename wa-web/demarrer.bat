@echo off
REM ---------------------------------------------------------------------------
REM  Demarrage du service compagnon WhatsApp Web (UbiSmartCRM Pro).
REM  Double-cliquez sur ce fichier, ou lancez-le depuis une invite de commandes.
REM
REM  La configuration est lue dans le fichier .env situe dans ce dossier :
REM  plus besoin de retaper  set VAR=...  a chaque nouvelle fenetre.
REM ---------------------------------------------------------------------------
setlocal
cd /d "%~dp0"
title Service WhatsApp Web - UbiSmartCRM Pro

echo ============================================================
echo   Service WhatsApp Web - UbiSmartCRM Pro
echo ============================================================
echo.

REM --- 1. Node.js installe ? ---
where node >nul 2>&1
if errorlevel 1 goto PAS_DE_NODE
for /f "delims=" %%v in ('node --version') do echo  Node.js detecte : %%v

REM --- 2. Fichier de configuration .env ---
if not exist ".env" goto CREER_ENV
echo  Fichier .env trouve.
goto DEPENDANCES

:CREER_ENV
if not exist ".env.example" goto PAS_EXEMPLE
copy /y ".env.example" ".env" >nul
echo.
echo  Un fichier .env vient d'etre cree a partir de .env.example.
echo  Ouvrez-le et verifiez UBISENDER_CALLBACK et WA_WEB_TOKEN,
echo  puis relancez ce script.
echo.
goto FIN

:DEPENDANCES
if not exist "node_modules" goto INSTALLER
REM --- Version de Baileys conforme a package.json ? ---
node -e "var d=require('./package.json').dependencies['@whiskeysockets/baileys'];var i='';try{i=require('./node_modules/@whiskeysockets/baileys/package.json').version}catch(e){};if(d!==i){console.log(' Baileys installe : '+(i||'aucun')+'  /  attendu : '+d);process.exit(1)}"
if errorlevel 1 goto INSTALLER
echo  Dependances a jour.
goto DEMARRER

:INSTALLER
echo.
echo  Installation des dependances (premiere execution, patientez)...
echo.
call npm install
if errorlevel 1 goto ECHEC_NPM

:DEMARRER
REM --- Le port est-il deja occupe par une autre instance ? ---
set "PORT=3000"
for /f "usebackq eol=# tokens=1,* delims==" %%a in (".env") do (
    if /i "%%a"=="PORT" set "PORT=%%b"
)
if defined PORT set PORT=%PORT:"=%
for /f "tokens=* delims= " %%p in ("%PORT%") do set "PORT=%%p"
powershell -NoProfile -Command "try{Invoke-RestMethod -Uri 'http://localhost:%PORT%/health' -TimeoutSec 3 | Out-Null;exit 0}catch{exit 1}"
if not errorlevel 1 goto DEJA_LANCE

echo.
echo  Demarrage du service... (laissez cette fenetre OUVERTE)
echo  Pour ARRETER le service : appuyez sur Ctrl+C (arret propre).
echo  Evitez de fermer la fenetre d'un coup : les cles de chiffrement
echo  risquent d'etre a demi ecrites (messages illisibles ensuite).
echo.
node server.js
echo.
echo  ** Le service s'est arrete. **
goto FIN

:DEJA_LANCE
echo.
echo  ** Le service tourne DEJA sur le port %PORT%. **
echo.
echo  Il n'y a rien a relancer : la fenetre du service est deja ouverte
echo  quelque part. Verifiez : http://localhost:%PORT%/health
echo.
echo  Pour le REMPLACER : lancez arreter.bat, attendez quelques
echo  secondes, puis relancez ce script.
goto FIN

:PAS_DE_NODE
echo.
echo  ERREUR : Node.js est introuvable.
echo  Installez Node.js 18 ou superieur : https://nodejs.org
echo.
goto FIN

:PAS_EXEMPLE
echo.
echo  ERREUR : ni .env ni .env.example dans ce dossier.
echo  Dossier courant : %CD%
echo  Verifiez que vous avez bien recupere la derniere version du projet.
echo.
goto FIN

:ECHEC_NPM
echo.
echo  ERREUR : l'installation des dependances a echoue (npm install).
echo  Verifiez votre connexion reseau puis relancez.
echo.
goto FIN

:FIN
echo.
pause
endlocal
