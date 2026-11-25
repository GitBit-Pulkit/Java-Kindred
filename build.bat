@echo off
REM ---------------------------------------------------------------
REM  Compiles Kindred (no Maven): every .java under src -> out\
REM  The JARs in lib\ are put on the classpath.
REM ---------------------------------------------------------------
cd /d "%~dp0"
if not exist out mkdir out
dir /s /b src\*.java > sources.txt
javac -encoding UTF-8 -cp "lib\*" -d out @sources.txt
set RESULT=%ERRORLEVEL%
del sources.txt
if not %RESULT%==0 (
    echo.
    echo BUILD FAILED
    exit /b 1
)
echo BUILD OK - classes are in out\
