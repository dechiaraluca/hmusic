@echo off
rem Compile hmusic avec Maven (telecharge automatiquement par le Maven Wrapper)
rem et produit target\hmusic.jar (dependances incluses).
setlocal
cd /d "%~dp0"
call "%~dp0mvnw.cmd" -q -B package
if errorlevel 1 exit /b 1
echo.
echo Compilation OK : target\hmusic.jar
