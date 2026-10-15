#!/usr/bin/env bash
# Uso: ./compilar_e_executar.sh [1|2|3|todos] [--resumo]
set -e
cd "$(dirname "$0")"
mkdir -p out
javac -encoding UTF-8 -d out src/poolbd/*.java
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp out poolbd.Main "$@"
