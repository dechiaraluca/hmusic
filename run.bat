@echo off
rem Compile (rapide si rien n'a change) puis lance hmusic
cd /d "%~dp0"
call "%~dp0build.bat" || exit /b 1
start "" javaw -jar "%~dp0target\hmusic.jar"
