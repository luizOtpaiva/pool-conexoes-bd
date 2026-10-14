@echo off
REM Abre o painel em http://localhost:8080
chcp 65001 >nul
cd /d "%~dp0"
if not exist out mkdir out
javac -encoding UTF-8 -d out src\poolbd\*.java
if errorlevel 1 exit /b 1
echo Painel em http://localhost:8080
start "Pool de conexoes" java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp out poolbd.ServidorWeb
ping 127.0.0.1 -n 3 >nul
start "" http://localhost:8080
