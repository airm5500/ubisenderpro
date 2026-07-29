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
if exist "node_modules" goto DEMARRER
echo.
echo  Installation des dependances (premiere execution, patientez)...
echo.
call npm install
if errorlevel 1 goto ECHEC_NPM

:DEMARRER
echo.
echo  Demarrage du service... (laissez cette fenetre OUVERTE)
echo  Pour arreter le service : Ctrl+C, ou fermez cette fenetre.
echo.
node server.js
echo.
echo  ** Le service s'est arrete. **
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
