@echo off
rem Fabrique une version Windows autonome de hmusic (Java inclus, rien a installer) :
rem   target\dist\hmusic\hmusic.exe  et  target\hmusic-windows.zip (a publier sur GitHub Releases)
setlocal
cd /d "%~dp0"

call "%~dp0mvnw.cmd" -q -B package
if errorlevel 1 exit /b 1

rem --- dossier d'entree : le jar + la banque de sons ---
if exist target\jpackage-input rmdir /s /q target\jpackage-input
if exist target\dist rmdir /s /q target\dist
mkdir target\jpackage-input
copy /y target\hmusic.jar target\jpackage-input\ >nul
xcopy /e /i /q /y soundfonts target\jpackage-input\soundfonts >nul

rem --- application + mini-Java (seulement les modules utiles) ---
jpackage --type app-image --name hmusic --app-version 2.0 ^
  --description "Musique selon ton humeur" --vendor "Luca De Chiara" ^
  --input target\jpackage-input --main-jar hmusic.jar --main-class hmusic.Main ^
  --icon packaging\hmusic.ico --dest target\dist ^
  --java-options "--enable-native-access=ALL-UNNAMED" ^
  --add-modules java.base,java.desktop,java.net.http,java.sql,java.compiler,jdk.unsupported,jdk.localedata ^
  --jlink-options "--strip-debug --no-header-files --no-man-pages --include-locales=fr,en"
if errorlevel 1 exit /b 1

rem --- archive zip ---
if exist target\hmusic-windows.zip del target\hmusic-windows.zip
powershell -NoProfile -Command "Compress-Archive -Path 'target\dist\hmusic' -DestinationPath 'target\hmusic-windows.zip'"
if errorlevel 1 exit /b 1

echo.
echo Version Windows prete :
echo   - a tester : target\dist\hmusic\hmusic.exe
echo   - a publier sur GitHub (Releases) : target\hmusic-windows.zip
