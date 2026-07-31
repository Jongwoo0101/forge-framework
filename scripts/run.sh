#!/usr/bin/env bash
# ForgeOS 실행 스크립트
set -e
cd "$(dirname "$0")/.."

if [ ! -d out ]; then
  echo "out/ 디렉터리가 없습니다. 먼저 ./scripts/build.sh 를 실행하세요."
  exit 1
fi

java -cp out forgeos.Main
