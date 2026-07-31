#!/usr/bin/env bash
# ForgeOS 빌드 스크립트
set -e
cd "$(dirname "$0")/.."

mkdir -p out
find src/main/java -name "*.java" > /tmp/forgeos_sources.txt
javac -encoding UTF-8 -d out @/tmp/forgeos_sources.txt
rm -f /tmp/forgeos_sources.txt

echo "Build complete: out/"
