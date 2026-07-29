@echo off
REM ---------------------------------------------------------------------------
REM  Arret PROPRE du service compagnon WhatsApp Web (UbiSmartCRM Pro).
REM
REM  Demande l'arret au service lui-meme : il sauvegarde ses caches puis ferme
REM  les sessions sans delier l'appareil. C'est l'equivalent exact de Ctrl+C.
REM  A l'inverse, un "taskkill /F" coupe le processus net et peut laisser les
REM  cles de chiffrement a demi ecrites (messages illisibles ensuite).
REM ---------------------------------------------------------------------------
setlocal
cd /d "%~dp0"
title Arret du service WhatsApp Web

echo ============================================================
echo   Arret du service WhatsApp Web - UbiSmartCRM Pro
echo ============================================================
echo.

if not exist ".env" goto PAS_ENV

REM --- Port et jeton lus dans .env (les lignes # sont ignorees) ---
set "PORT=3000"
set "WA_WEB_TOKEN="
for /f "usebackq eol=# tokens=1,* delims==" %%a in (".env") do (
    if /i "%%a"=="PORT" set "PORT=%%b"
    if /i "%%a"=="WA_WEB_TOKEN" set "WA_WEB_TOKEN=%%b"
)
REM Retrait d'eventuels guillemets autour des valeurs (sans guillemets
REM englobants : ils casseraient l'expansion).
if defined PORT set PORT=%PORT:"=%
if defined WA_WEB_TOKEN set WA_WEB_TOKEN=%WA_WEB_TOKEN:"=%
for /f "tokens=* delims= " %%p in ("%PORT%") do set "PORT=%%p"

echo  Service vise : http://localhost:%PORT%
echo.

REM --- Le service repond-il ? ---
powershell -NoProfile -Command "try{Invoke-RestMethod -Uri 'http://localhost:%PORT%/health' -TimeoutSec 5 | Out-Null;exit 0}catch{exit 1}"
if errorlevel 1 goto PAS_DEMARRE

echo  Demande d'arret propre en cours...
powershell -NoProfile -Command "try{Invoke-RestMethod -Uri 'http://localhost:%PORT%/arret' -Method Post -Headers @{'X-Api-Token'='%WA_WEB_TOKEN%'} -TimeoutSec 10 | Out-Null;exit 0}catch{if($_.Exception.Response.StatusCode.value__ -eq 401){exit 2};exit 1}"
if errorlevel 2 goto MAUVAIS_JETON
if errorlevel 1 goto ECHEC

REM --- Verification : le service ne doit plus repondre ---
powershell -NoProfile -Command "Start-Sleep -Seconds 3"
powershell -NoProfile -Command "try{Invoke-RestMethod -Uri 'http://localhost:%PORT%/health' -TimeoutSec 4 | Out-Null;exit 0}catch{exit 1}"
if errorlevel 1 goto ARRETE

echo.
echo  Le service repond encore : laissez-lui quelques secondes,
echo  puis relancez ce script si necessaire.
goto FIN

:ARRETE
echo.
echo  ** Service arrete proprement. **
echo  Caches et cles de chiffrement sauvegardes ; l'appareil reste appaire
echo  (aucun QR a rescanner au prochain demarrage).
goto FIN

:PAS_DEMARRE
echo  Le service ne repond pas sur le port %PORT% :
echo  il est probablement deja arrete. Rien a faire.
goto FIN

:MAUVAIS_JETON
echo.
echo  ERREUR : jeton refuse par le service (401).
echo  Verifiez WA_WEB_TOKEN dans le fichier .env.
goto FIN

:PAS_ENV
echo  ERREUR : fichier .env introuvable dans ce dossier.
echo  Dossier courant : %CD%
goto FIN

:ECHEC
echo.
echo  ERREUR : la demande d'arret a echoue.
echo  Repli : dans la fenetre du service, appuyez sur Ctrl+C.
echo  N'utilisez taskkill /F qu'en dernier recours (arret brutal).
goto FIN

:FIN
echo.
pause
endlocal
