@echo off
REM ---------------------------------------------------------------------------
REM  Arret et suppression des services Windows du CRM UBIPHARM.
REM  A lancer en administrateur. Le dossier donnees (configuration, journaux,
REM  session WhatsApp) et la base MariaDB sont conserves : relancez
REM  installer.bat pour reinstaller, par exemple apres une mise a jour.
REM ---------------------------------------------------------------------------
setlocal
cd /d "%~dp0"
title Desinstallation du CRM UBIPHARM

net session >nul 2>&1
if errorlevel 1 goto PAS_ADMIN

call :RETIRER crm-service ubipharm-crm
call :RETIRER wa-web-service ubipharm-crm-whatsapp
echo.
echo  Services arretes et supprimes. Le dossier donnees et la base sont conserves.
goto FIN

:RETIRER
sc query "%~2" >nul 2>&1
if errorlevel 1 (
  echo  Service %~2 absent.
  exit /b 0
)
echo  Arret du service %~2...
"%~dp0%~1.exe" stop
"%~dp0%~1.exe" uninstall
exit /b 0

:PAS_ADMIN
echo  Ce script doit etre lance en administrateur :
echo  clic droit sur desinstaller.bat ^> Executer en tant qu'administrateur.
goto FIN

:FIN
echo.
pause
endlocal
