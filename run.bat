@echo off
REM ---------------------------------------------------------------
REM  Builds and starts Kindred. Run from anywhere; it switches to
REM  this folder so config\kindred.conf and exports\ are found.
REM ---------------------------------------------------------------
cd /d "%~dp0"
call build.bat
if errorlevel 1 (
    pause
    exit /b 1
)
java -cp "out;lib\*" com.kindred.app.KindredApp
pause
