@echo off
REM Met a jour le CRM installe a partir de cette copie du depot (en administrateur).
net session >nul 2>&1
if errorlevel 1 (
  echo  Lancez ce fichier en administrateur : clic droit ^> Executer en tant qu'administrateur.
  pause
  exit /b 1
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0mettre-a-jour.ps1" %*
pause
