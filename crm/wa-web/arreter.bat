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
REM Retrait d'eventuels guillemets (sans guillemets englobants : ils
REM casseraient l'expansion).
if defined PORT set PORT=%PORT:"=%
if defined WA_WEB_TOKEN set WA_WEB_TOKEN=%WA_WEB_TOKEN:"=%
for /f "tokens=* delims= " %%p in ("%PORT%") do set "PORT=%%p"

echo  Service vise : http://localhost:%PORT%
echo.

REM --- Le service repond-il ? ---
powershell -NoProfile -Command "try{Invoke-RestMethod -Uri 'http://localhost:%PORT%/health' -TimeoutSec 5 | Out-Null;exit 0}catch{exit 1}"
if errorlevel 1 goto NE_REPOND_PAS

echo  Demande d'arret propre en cours...
powershell -NoProfile -Command "try{Invoke-RestMethod -Uri 'http://localhost:%PORT%/arret' -Method Post -Headers @{'X-Api-Token'='%WA_WEB_TOKEN%'} -TimeoutSec 10 | Out-Null;exit 0}catch{$c=$_.Exception.Response.StatusCode.value__;if($c -eq 404){exit 3};if($c -eq 401){exit 2};exit 1}"
if errorlevel 3 goto ANCIENNE_INSTANCE
if errorlevel 2 goto MAUVAIS_JETON
if errorlevel 1 goto ECHEC

REM --- Laisse le temps au processus de se terminer et de liberer le port ---
powershell -NoProfile -Command "Start-Sleep -Seconds 4"
call :PORT_OCCUPE
if defined PIDPORT goto ENCORE_OCCUPE

echo.
echo  ** Service arrete proprement. **
echo  Caches et cles de chiffrement sauvegardes ; l'appareil reste appaire
echo  (aucun QR a rescanner au prochain demarrage).
goto FIN

:NE_REPOND_PAS
REM  Le service ne repond plus, mais le port peut rester tenu par un
REM  processus residuel : c'est ce qui provoque EADDRINUSE au redemarrage.
call :PORT_OCCUPE
if defined PIDPORT goto ENCORE_OCCUPE
echo  Le service ne repond pas et le port %PORT% est libre :
echo  il est deja arrete. Rien a faire.
goto FIN

:ENCORE_OCCUPE
echo.
echo  ATTENTION : le port %PORT% est encore occupe par le processus %PIDPORT%.
echo.
REM  Un processus node residuel empeche tout redemarrage (EADDRINUSE).
tasklist /fi "PID eq %PIDPORT%" 2>nul | findstr /i "node.exe" >nul
if errorlevel 1 goto PAS_NODE
echo  Il s'agit bien d'un processus node.exe (une instance residuelle).
echo.
choice /c ON /n /m " Le terminer maintenant ? (O = oui / N = non) "
if errorlevel 2 goto REFUS_KILL
taskkill /PID %PIDPORT% /F >nul 2>&1
powershell -NoProfile -Command "Start-Sleep -Seconds 2"
call :PORT_OCCUPE
if defined PIDPORT goto KILL_ECHEC
echo.
echo  ** Processus residuel termine, port %PORT% libere. **
echo  Vous pouvez relancer demarrer.bat.
goto FIN

:REFUS_KILL
echo.
echo  Processus laisse en place. Le redemarrage echouera tant que le port
echo  %PORT% restera occupe.
goto FIN

:KILL_ECHEC
echo.
echo  Le port reste occupe. Fermez la fenetre du service concernee,
echo  ou redemarrez le poste.
goto FIN

:PAS_NODE
echo  Ce processus n'est PAS node.exe : une autre application utilise le
echo  port %PORT%. Ne la terminez pas a l'aveugle.
echo  Solution : changez PORT dans le fichier .env.
goto FIN

:ANCIENNE_INSTANCE
echo.
echo  L'instance en cours est ANCIENNE : elle a ete demarree avant la
echo  mise a jour et ne connait pas encore la commande d'arret propre.
echo.
echo  Le mieux : retrouvez sa fenetre (titre "Service WhatsApp Web")
echo  et appuyez sur Ctrl+C dedans. C'est l'arret le plus sur.
echo.
echo  A defaut, ce script peut terminer le processus :
call :PORT_OCCUPE
if defined PIDPORT goto ENCORE_OCCUPE
echo  (le port est deja libre : plus rien a arreter)
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
goto FIN

:PORT_OCCUPE
REM  Renseigne PIDPORT avec le processus qui ECOUTE sur le port, sinon vide.
set "PIDPORT="
for /f "tokens=5" %%p in ('netstat -ano ^| findstr /c:":%PORT% " ^| findstr /i "LISTENING"') do set "PIDPORT=%%p"
goto :eof

:FIN
echo.
pause
endlocal
