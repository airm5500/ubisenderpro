@echo off
REM ---------------------------------------------------------------------------
REM  Installation (ou reinstallation apres mise a jour) du CRM UBIPHARM.
REM  A lancer en administrateur depuis le dossier application :
REM  clic droit sur installer.bat > Executer en tant qu'administrateur.
REM
REM  1re execution : cree le dossier donnees voisin avec les fichiers de
REM  configuration, puis s'arrete pour que vous les completiez.
REM  2e execution : installe et demarre les deux services Windows.
REM ---------------------------------------------------------------------------
setlocal
cd /d "%~dp0"
title Installation du CRM UBIPHARM

net session >nul 2>&1
if errorlevel 1 goto PAS_ADMIN

set "APP=%~dp0"
set "APP=%APP:~0,-1%"
for %%i in ("%APP%\..") do set "RACINE=%%~fi"
set "DONNEES=%RACINE%\donnees"

echo ============================================================
echo   Installation du CRM UBIPHARM
echo ============================================================
echo   Application : %APP%
echo   Donnees     : %DONNEES%
echo.

if not exist "%APP%\runtime\bin\java.exe" goto PAQUET_INCOMPLET
if not exist "%APP%\node\node.exe" goto PAQUET_INCOMPLET
if not exist "%APP%\crm-service.exe" goto PAQUET_INCOMPLET

if not exist "%DONNEES%" mkdir "%DONNEES%"
if not exist "%DONNEES%\logs" mkdir "%DONNEES%\logs"
if not exist "%DONNEES%\wa-web-sessions" mkdir "%DONNEES%\wa-web-sessions"

REM --- 1. Fichiers de configuration (crees une seule fois, jamais ecrases) ---
if exist "%DONNEES%\application-prod.yml" goto CONFIG_PRESENTE
echo  Creation des fichiers de configuration...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$j = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N');" ^
  "foreach ($f in 'application-prod.yml','wa-web.env') {" ^
  "  $t = [IO.File]::ReadAllText((Join-Path $env:APP ('modeles\' + $f))).Replace('__JETON_WA_WEB__', $j);" ^
  "  [IO.File]::WriteAllText((Join-Path $env:DONNEES $f), $t) }"
if errorlevel 1 goto ECHEC
echo.
echo  Deux fichiers viennent d'etre crees dans %DONNEES% :
echo    - application-prod.yml : renseignez le mot de passe de la base
echo      (ligne password: "A_RENSEIGNER") et, si vous l'avez, le serveur e-mail ;
echo    - wa-web.env : rien a changer en general.
echo.
echo  Puis relancez installer.bat en administrateur.
start "" notepad "%DONNEES%\application-prod.yml"
goto FIN

:CONFIG_PRESENTE
if not exist "%DONNEES%\wa-web.env" goto ENV_MANQUANT
findstr /c:"A_RENSEIGNER" "%DONNEES%\application-prod.yml" >nul
if not errorlevel 1 goto CONFIG_INCOMPLETE
echo  Configuration trouvee.

REM --- 2. Services Windows (remplaces s'ils existent deja) ---
call :INSTALLER_SERVICE wa-web-service ubipharm-crm-whatsapp
if errorlevel 1 goto ECHEC
call :INSTALLER_SERVICE crm-service ubipharm-crm
if errorlevel 1 goto ECHEC

REM --- 3. Demarrage : WhatsApp d'abord, le CRM ensuite ---
echo.
echo  Demarrage des services...
"%APP%\wa-web-service.exe" start
"%APP%\crm-service.exe" start

set "PORT=8080"
for /f "tokens=1,* delims=:" %%a in ('findstr /r /c:"^  port: *[0-9]" "%DONNEES%\application-prod.yml"') do set "PORT=%%b"
set "PORT=%PORT: =%"
echo  Attente du CRM sur le port %PORT% (premier demarrage : creation de la base, jusqu'a 2 minutes)...
powershell -NoProfile -Command ^
  "for ($i = 0; $i -lt 60; $i++) { try { Invoke-WebRequest -UseBasicParsing -TimeoutSec 3 ('http://localhost:' + $env:PORT + '/api/auth/csrf') | Out-Null; exit 0 } catch { Start-Sleep -Seconds 2 } }; exit 1"
if errorlevel 1 goto CRM_MUET

echo.
echo ============================================================
echo   CRM UBIPHARM installe et demarre.
echo ============================================================
echo   Sur ce poste : http://localhost:%PORT%
echo   Premiere connexion : compte admin, mot de passe Admin@2026,
echo   que le CRM fait changer des la premiere connexion.
echo.
echo   Les deux services redemarrent seuls avec Windows :
echo   "UBIPHARM CRM" et "UBIPHARM CRM - WhatsApp" (services.msc).
echo   Journaux : %DONNEES%\logs
goto FIN

REM ---------------------------------------------------------------------------
REM  :INSTALLER_SERVICE <exe sans extension> <identifiant du service>
REM ---------------------------------------------------------------------------
:INSTALLER_SERVICE
sc query "%~2" >nul 2>&1
if errorlevel 1 goto INSTALLER_SERVICE_NEUF
echo  Service %~2 deja present : arret et remplacement...
"%APP%\%~1.exe" stop >nul 2>&1
"%APP%\%~1.exe" uninstall
if errorlevel 1 exit /b 1
REM Windows libere le nom du service quelques secondes apres la suppression
powershell -NoProfile -Command "Start-Sleep -Seconds 3"
:INSTALLER_SERVICE_NEUF
echo  Installation du service %~2...
"%APP%\%~1.exe" install
if errorlevel 1 exit /b 1
exit /b 0

:PAS_ADMIN
echo.
echo  Ce script doit etre lance en administrateur :
echo  clic droit sur installer.bat ^> Executer en tant qu'administrateur.
goto FIN

:PAQUET_INCOMPLET
echo  ERREUR : le dossier application est incomplet (Java, Node ou services absents).
echo  Extrayez a nouveau le paquet UBIPHARM-CRM-*.zip en entier.
goto FIN

:ENV_MANQUANT
echo  ERREUR : %DONNEES%\wa-web.env est absent.
echo  Copiez-le depuis %APP%\modeles et reportez-y le jeton
echo  crm.whatsapp.jeton de application-prod.yml (WA_WEB_TOKEN=...).
goto FIN

:CONFIG_INCOMPLETE
echo  application-prod.yml contient encore A_RENSEIGNER :
echo  renseignez le mot de passe de la base, puis relancez installer.bat.
start "" notepad "%DONNEES%\application-prod.yml"
goto FIN

:CRM_MUET
echo.
echo  Le CRM ne repond pas encore. Consultez le journal :
echo    %DONNEES%\logs\crm.log  (et crm-service.err.log)
echo  Cause frequente : mot de passe de la base incorrect ou MariaDB arretee.
echo  Le service retente seul le demarrage ; corrigez puis redemarrez-le
echo  depuis services.msc.
goto FIN

:ECHEC
echo.
echo  ERREUR : l'installation s'est interrompue (voir les messages ci-dessus).
goto FIN

:FIN
echo.
pause
endlocal
