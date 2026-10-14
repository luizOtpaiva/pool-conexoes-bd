@echo off
REM Uso: compilar_e_executar.bat [1|2|3|todos] [--resumo]
chcp 65001 >nul
cd /d "%~dp0"
if not exist out mkdir out
javac -encoding UTF-8 -d out src\poolbd\*.java
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp out poolbd.Main %*
