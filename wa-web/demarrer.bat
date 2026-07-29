@echo off
REM ---------------------------------------------------------------------------
REM  Demarrage du service compagnon WhatsApp Web (UbiSmartCRM Pro).
REM  Double-cliquez sur ce fichier, ou lancez-le depuis une invite de commandes.
REM
REM  La configuration est lue dans le fichier .env situe dans ce dossier :
REM  plus besoin de retaper « set VAR=... » a chaque nouvelle fenetre.
REM ---------------------------------------------------------------------------
cd /d "%~dp0"
title Service WhatsApp Web - UbiSmartCRM Pro

if not exist ".env" (
    echo.
    echo  Aucun fichier .env trouve : creation a partir de .env.example
    echo.
    copy /y ".env.example" ".env" >nul
    echo  ^>^> Ouvrez .env et verifiez UBISENDER_CALLBACK et WA_WEB_TOKEN,
    echo     puis relancez ce script.
    echo.
    pause
    exit /b 1
)

if not exist "node_modules" (
    echo.
    echo  Installation des dependances (premiere execution)...
    echo.
    call npm install
    if errorlevel 1 (
        echo.
        echo  ECHEC de l'installation des dependances.
        pause
        exit /b 1
    )
)

echo.
echo  Demarrage du service...
echo.
node server.js

echo.
echo  Le service s'est arrete.
pause
