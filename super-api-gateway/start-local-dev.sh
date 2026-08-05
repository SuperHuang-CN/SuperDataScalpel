#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOCAL_CONFIG="$ROOT_DIR/config/application-local.yml"
UI_DIR="$ROOT_DIR/super-api-gateway-ui"
SERVER_PID=""
UI_PID=""

cleanup() {
  [[ -n "$UI_PID" ]] && kill "$UI_PID" 2>/dev/null || true
  [[ -n "$SERVER_PID" ]] && kill "$SERVER_PID" 2>/dev/null || true
  [[ -n "$UI_PID" ]] && wait "$UI_PID" 2>/dev/null || true
  [[ -n "$SERVER_PID" ]] && wait "$SERVER_PID" 2>/dev/null || true
}

trap cleanup EXIT INT TERM

command -v java >/dev/null 2>&1 || { echo "需要 JDK 21。"; exit 1; }
command -v pnpm >/dev/null 2>&1 || { echo "需要 pnpm 9+。"; exit 1; }

if [[ ! -f "$LOCAL_CONFIG" ]]; then
  echo "未找到 $LOCAL_CONFIG，请复制 config/application-local.example.yml 后填写。"
  exit 1
fi

cd "$ROOT_DIR"

if [[ ! -d "$UI_DIR/node_modules" ]]; then
  echo "正在安装独立管理 UI 依赖…"
  (cd "$UI_DIR" && pnpm install)
fi

echo "正在启动 Super API Gateway Server：http://localhost:19000"
./mvnw -q spring-boot:run \
  -Dspring-boot.run.profiles=local \
  "-Dspring-boot.run.arguments=--spring.config.additional-location=optional:file:$LOCAL_CONFIG" &
SERVER_PID=$!

echo "正在启动 Super API Gateway UI：http://localhost:19080"
(cd "$UI_DIR" && pnpm dev --host 127.0.0.1) &
UI_PID=$!

wait "$SERVER_PID" "$UI_PID"
