@echo off
rem Fabrique le paquet Microsoft Store de hmusic : target\store\hmusic-<version>.msix
rem (a envoyer dans Partner Center ; c'est le Store qui le signe).
rem Avant le premier envoi : remplir packaging\store\store.properties avec les valeurs de Partner Center.
setlocal
cd /d "%~dp0"

rem --- 1. la version Windows autonome (meme contenu que le zip GitHub) ---
call "%~dp0dist.bat"
if errorlevel 1 exit /b 1
cd /d "%~dp0"

set VERSION=
for /f "tokens=2 delims==" %%v in ('findstr /b "version=" target\classes\hmusic\version.properties') do set VERSION=%%v
if "%VERSION%"=="" (
  echo Version introuvable dans target\classes\hmusic\version.properties
  exit /b 1
)

rem --- 2. l'outil makeappx (Windows SDK), telecharge une seule fois depuis NuGet (paquet officiel Microsoft) ---
set SDK=target\tools\sdk-buildtools
set MAKEAPPX=%SDK%\bin\10.0.26100.0\x64\makeappx.exe
if not exist "%MAKEAPPX%" (
  echo Telechargement de makeappx [Windows SDK Build Tools]...
  powershell -NoProfile -Command "$ErrorActionPreference='Stop'; $ProgressPreference='SilentlyContinue'; New-Item -ItemType Directory -Force '%SDK%' | Out-Null; Invoke-WebRequest 'https://api.nuget.org/v3-flatcontainer/microsoft.windows.sdk.buildtools/10.0.26100.4654/microsoft.windows.sdk.buildtools.10.0.26100.4654.nupkg' -OutFile '%SDK%\buildtools.zip'; Expand-Archive '%SDK%\buildtools.zip' '%SDK%' -Force"
  if errorlevel 1 exit /b 1
)

rem --- 3. dossier du paquet : l'application + logos + manifeste ---
if exist target\store rmdir /s /q target\store
xcopy /e /i /q /y target\dist\hmusic target\store\package >nul
xcopy /e /i /q /y packaging\store\Assets target\store\package\Assets >nul
powershell -NoProfile -ExecutionPolicy Bypass -File packaging\store\manifeste.ps1 -Version %VERSION% -Sortie target\store\package\AppxManifest.xml
if errorlevel 1 exit /b 1

rem --- 4. le paquet .msix ---
"%MAKEAPPX%" pack /o /d target\store\package /p target\store\hmusic-%VERSION%.msix
if errorlevel 1 exit /b 1

echo.
echo Paquet Microsoft Store pret : target\store\hmusic-%VERSION%.msix
echo   - a envoyer dans Partner Center (Packages) ; le Store le signe lui-meme
echo   - pour l'essayer avant : mode developpeur Windows active, puis dans PowerShell :
echo       Add-AppxPackage -Register target\store\package\AppxManifest.xml
