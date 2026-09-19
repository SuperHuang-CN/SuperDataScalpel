#!/usr/bin/env bash

# linux69 测试环境入口。应用以前台进程运行；PostgreSQL/MinIO 使用 linux5
# 上的隔离资源，Kafka/Kong 由 linux69 的 Portainer Stack 管理。

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUNTIME_ROOT="${DATASCALPEL_TEST69_RUNTIME_ROOT:-/data/datascalpel-test69}"
RUNTIME_ENV="${DATASCALPEL_TEST69_ENV_FILE:-$RUNTIME_ROOT/config/runtime.env}"
PREPARE_ONLY=false
MAVEN_THREADS="${DATASCALPEL_MAVEN_THREADS:-1C}"

for ((index = 1; index <= $#; index++)); do
  argument="${!index}"
  case "$argument" in
    --prepare)
      PREPARE_ONLY=true
      ;;
    --threads)
      next_index=$((index + 1))
      if (( next_index <= $# )); then
        MAVEN_THREADS="${!next_index}"
      fi
      ;;
    --threads=*)
      MAVEN_THREADS="${argument#*=}"
      ;;
  esac
done

if ! ip -4 -o address show scope global 2>/dev/null | grep -qE '[[:space:]]10[.]0[.]0[.]69/'; then
  echo "start-local-test69.sh 只能在 IP 为 10.0.0.69 的测试服务器运行。" >&2
  exit 1
fi

if [[ ! -f "$RUNTIME_ENV" ]]; then
  echo "未找到 test69 私密配置：$RUNTIME_ENV" >&2
  exit 1
fi

runtime_mode="$(stat -c '%a' "$RUNTIME_ENV")"
if (( (8#$runtime_mode & 077) != 0 )); then
  echo "test69 私密配置权限必须为 600：$RUNTIME_ENV（当前 $runtime_mode）" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "$RUNTIME_ENV"
set +a

required_secrets=(
  DATASCALPEL_DB_PASSWORD
  DATASCALPEL_ENGINE_DB_PASSWORD
  KONG_PG_PASSWORD
  DATASCALPEL_FILE_STORAGE_ACCESS_KEY
  DATASCALPEL_FILE_STORAGE_SECRET_KEY
  DATASCALPEL_ADMIN_PASSWORD
  DATASCALPEL_JWT_SECRET
  DATASCALPEL_ENGINE_MANAGEMENT_TOKEN
  DATASCALPEL_SERVICE_ENGINE_CREDENTIAL_KEY
  DATASCALPEL_DATA_SOURCE_CREDENTIAL_KEY
  DATASCALPEL_MCP_CREDENTIAL_KEY
  DATASCALPEL_TASK_ENGINE_TOKEN
  DATASCALPEL_COMPUTE_ENGINE_CREDENTIAL_KEY
  DATASCALPEL_TASK_DISPATCHER_TOKEN
  DATASCALPEL_DSH_BRIDGE_KEY
  DATASCALPEL_DSH_CREDENTIAL_KEY
)
for variable_name in "${required_secrets[@]}"; do
  if [[ -z "${!variable_name:-}" ]]; then
    echo "test69 私密配置缺少：$variable_name" >&2
    exit 1
  fi
done

required_commands=(java javac node pnpm curl docker nc openssl ss)
for command_name in "${required_commands[@]}"; do
  command -v "$command_name" >/dev/null 2>&1 || {
    echo "linux69 缺少运行命令：$command_name" >&2
    exit 1
  }
done

java_major="$(java -version 2>&1 | sed -nE '1s/.*version "([0-9]+).*/\1/p')"
node_major="$(node --version | sed -E 's/^v([0-9]+).*/\1/')"
pnpm_version="$(pnpm --version)"
if [[ "$java_major" != "21" ]]; then
  echo "test69 需要 JDK 21，当前 Java 主版本为 ${java_major:-未知}。" >&2
  exit 1
fi
if [[ ! "$node_major" =~ ^[0-9]+$ ]] || (( node_major < 22 )); then
  echo "test69 需要 Node.js 22 或更高版本，当前为 $(node --version)。" >&2
  exit 1
fi
if [[ "$pnpm_version" != "9.9.0" ]]; then
  echo "test69 固定使用 pnpm 9.9.0，当前为 $pnpm_version。" >&2
  exit 1
fi

if [[ ! -s "$ROOT_DIR/settings-superhuang.xml" ]]; then
  echo "缺少 Maven 私有配置：$ROOT_DIR/settings-superhuang.xml" >&2
  exit 1
fi

umask 077
mkdir -p \
  "$RUNTIME_ROOT/runtime/task-dispatcher" \
  "$RUNTIME_ROOT/runtime/task-streaming-checkpoints" \
  "$RUNTIME_ROOT/runtime/file-parsing"

export DATASCALPEL_START_ENVIRONMENT_NAME="test69 测试"
export DATASCALPEL_START_PROFILE="test69"
export DATASCALPEL_START_CONFIG="$ROOT_DIR/config/application-test69.yml"
export DATASCALPEL_START_BACKEND_BIND_ADDRESS="0.0.0.0"
export DATASCALPEL_START_BACKEND_INTERNAL_URL="http://127.0.0.1:18080"
export DATASCALPEL_START_ENGINE_BIND_ADDRESS="0.0.0.0"
export DATASCALPEL_START_ENGINE_ADMIN_URL="http://127.0.0.1:8081"
export DATASCALPEL_START_ENGINE_RUNTIME_URL="http://10.0.0.69:8081"
export DATASCALPEL_START_TASK_ENGINE_HOST="127.0.0.1"
export DATASCALPEL_START_TASK_ENGINE_URL="http://127.0.0.1:18091"
export DATASCALPEL_START_DISPATCHER_BIND_ADDRESS="127.0.0.1"
export DATASCALPEL_START_DISPATCHER_URL="http://127.0.0.1:18092"
export DATASCALPEL_START_FRONTEND_HOST="0.0.0.0"
export DATASCALPEL_START_ENGINE_DISPLAY_NAME="linux69 测试服务引擎"
export DATASCALPEL_START_ENGINE_DESCRIPTION="由 start-local-test69.sh 自动登记的 linux69 测试服务引擎。"
export DATASCALPEL_START_ENGINE_ACCESS_POLICY_JSON='{"allowCidrs":["127.0.0.1/32","::1/128","10.0.0.0/24","172.30.69.0/24"],"denyCidrs":[]}'
export DATASCALPEL_START_COMPUTE_ENGINE_DESCRIPTION="由 start-local-test69.sh 管理的 linux69 Docker Spark 测试计算引擎"

export BACKEND_PORT="18080"
export ENGINE_PORT="8081"
export TASK_ENGINE_PORT="18091"
export DISPATCHER_PORT="18092"
export FRONTEND_PORT="18887"
export DATASCALPEL_LOCAL_ENGINE_CODE="test69_engine"
export DATASCALPEL_LOCAL_COMPUTE_ENGINE_NAME="linux69 Docker 测试计算引擎"
export DATASCALPEL_ADMIN_USERNAME="test69admin"

export DATASCALPEL_DB_URL="jdbc:postgresql://10.0.0.5:5432/datascalpel_test69"
export DATASCALPEL_DB_USERNAME="datascalpel_test69"
export DATASCALPEL_ENGINE_DB_URL="jdbc:postgresql://10.0.0.5:5432/datascalpel_engine_test69"
export DATASCALPEL_ENGINE_DB_USERNAME="datascalpel_engine_test69"
export DATASCALPEL_TASK_DISPATCHER_DB_URL="$DATASCALPEL_DB_URL"
export DATASCALPEL_TASK_DISPATCHER_DB_SCHEMA="dispatcher_test69"

export DATASCALPEL_KAFKA_BOOTSTRAP_SERVERS="10.0.0.69:19094"
export DATASCALPEL_EXECUTION_KAFKA_BOOTSTRAP_SERVERS="$DATASCALPEL_KAFKA_BOOTSTRAP_SERVERS"
export DATASCALPEL_EXECUTION_KAFKA_CONSUMER_GROUP="data-scalpel-admin-execution-test69"
export DATASCALPEL_LOCAL_COMMAND_TOPIC="datascalpel.execution.command.test69"
export DATASCALPEL_LOCAL_RUNNER_EVENT_TOPIC="datascalpel.runner.event.test69"
export DATASCALPEL_LOCAL_ADMIN_EVENT_TOPIC="datascalpel.execution.event.test69"
export DATASCALPEL_LOCAL_INVALID_MESSAGE_TOPIC="datascalpel.execution.invalid-message.test69"

export DATASCALPEL_FILE_STORAGE_ENDPOINT="http://10.0.0.5:9000"
export DATASCALPEL_FILE_STORAGE_RUNNER_ENDPOINT="$DATASCALPEL_FILE_STORAGE_ENDPOINT"
export DATASCALPEL_FILE_STORAGE_REGION="us-east-1"
export DATASCALPEL_FILE_STORAGE_BUCKET="datascalpel-test69"
export DATASCALPEL_FILE_STORAGE_ROOT_PREFIX="data-scalpel"
export DATASCALPEL_FILE_STORAGE_PATH_STYLE_ACCESS="true"
export DATASCALPEL_FILE_PARSING_TEMPORARY_DIRECTORY="$RUNTIME_ROOT/runtime/file-parsing"

export DATASCALPEL_SERVICE_GATEWAY_PROVIDER="kong"
export DATASCALPEL_KONG_ADMIN_URL="http://127.0.0.1:18003"
export DATASCALPEL_KONG_PROXY_URL="http://10.0.0.69:18000"
export DATASCALPEL_GATEWAY_ACCESS_ENABLED="false"
export DATASCALPEL_DSH_ENABLED="true"
export DATASCALPEL_DSH_URL="http://10.0.0.69:13080"
export DATASCALPEL_PUBLIC_BASE_URL="http://10.0.0.69:18080"
export DATASCALPEL_MCP_PUBLIC_BASE_URL="$DATASCALPEL_PUBLIC_BASE_URL"

export DATASCALPEL_TASK_DISPATCHER_WORK_DIRECTORY="$RUNTIME_ROOT/runtime/task-dispatcher"
export DATASCALPEL_TASK_STREAMING_LOCAL_CHECKPOINT_DIRECTORY="$RUNTIME_ROOT/runtime/task-streaming-checkpoints"
export DATASCALPEL_TASK_DISPATCHER_DOCKER_IMAGE="eclipse-temurin:21-jdk"
export DATASCALPEL_TASK_DISPATCHER_DOCKER_MEMORY="4g"
export DATASCALPEL_TASK_DISPATCHER_DOCKER_CPUS="2"
export DATASCALPEL_MAVEN_THREADS="$MAVEN_THREADS"

port_conflicts_with_bind_address() {
  local bind_address="$1"
  local port="$2"
  local listener

  while IFS= read -r listener; do
    [[ "$listener" == *":$port" ]] || continue
    if [[ "$bind_address" == "0.0.0.0" \
      || "$listener" == "0.0.0.0:$port" \
      || "$listener" == "*:$port" \
      || "$listener" == "[::]:$port" \
      || "$listener" == ":::$port" \
      || "$listener" == "$bind_address:$port" ]]; then
      return 0
    fi
  done < <(ss -ltnH | awk '{print $4}')
  return 1
}

check_application_port() {
  local name="$1"
  local bind_address="$2"
  local port="$3"

  if port_conflicts_with_bind_address "$bind_address" "$port"; then
    echo "test69 $name 监听地址已被占用：$bind_address:$port" >&2
    exit 1
  fi
}

if [[ "$PREPARE_ONLY" == false ]]; then
  docker info >/dev/null 2>&1 || {
    echo "Docker Daemon 不可用。" >&2
    exit 1
  }

  check_application_port "服务引擎" "$DATASCALPEL_START_ENGINE_BIND_ADDRESS" "$ENGINE_PORT"
  check_application_port "后端" "$DATASCALPEL_START_BACKEND_BIND_ADDRESS" "$BACKEND_PORT"
  check_application_port "Task Engine" "$DATASCALPEL_START_TASK_ENGINE_HOST" "$TASK_ENGINE_PORT"
  check_application_port "Task Dispatcher" "$DATASCALPEL_START_DISPATCHER_BIND_ADDRESS" "$DISPATCHER_PORT"
  check_application_port "前端" "$DATASCALPEL_START_FRONTEND_HOST" "$FRONTEND_PORT"

  nc -z -w 5 10.0.0.5 5432 || { echo "linux5 PostgreSQL 不可用。" >&2; exit 1; }
  curl --fail --silent --show-error --max-time 5 \
    "$DATASCALPEL_FILE_STORAGE_ENDPOINT/minio/health/live" >/dev/null || {
    echo "linux5 MinIO 不可用。" >&2
    exit 1
  }
  nc -z -w 5 10.0.0.69 19094 || { echo "test69 Kafka 不可用。" >&2; exit 1; }
  curl --fail --silent --show-error --max-time 5 http://127.0.0.1:18100/status >/dev/null || {
    echo "test69 Kong 不可用。" >&2
    exit 1
  }
  nc -z -w 5 10.0.0.69 13080 || { echo "test69 DSH 不可用。" >&2; exit 1; }
fi

cd "$ROOT_DIR"
if [[ ! -f data-scalpel-ui/node_modules/.modules.yaml \
  || data-scalpel-ui/pnpm-lock.yaml -nt data-scalpel-ui/node_modules/.modules.yaml ]]; then
  echo "正在安装 test69 前端依赖…"
  pnpm --dir data-scalpel-ui install --frozen-lockfile
fi

TASK_RUNNER_JAR="$ROOT_DIR/data-scalpel-task-engine/target/data-scalpel-task-engine-0.1.0-SNAPSHOT-runner-local.jar"
if [[ ! -f "$TASK_RUNNER_JAR" ]] || [[ -n "$(find \
  data-scalpel-task-engine/src/main data-scalpel-task-engine/pom.xml \
  data-scalpel-filegdb/src/main data-scalpel-filegdb/pom.xml \
  data-scalpel-filegdb-s3/src/main data-scalpel-filegdb-s3/pom.xml \
  data-scalpel-shapefile/src/main data-scalpel-shapefile/pom.xml \
  data-scalpel-shapefile-s3/src/main data-scalpel-shapefile-s3/pom.xml \
  -type f -newer "$TASK_RUNNER_JAR" -print -quit)" ]]; then
  echo "正在准备文件解析模块的 Maven test-jar 依赖（只编译，不运行测试）…"
  ./mvnw -q -T "$MAVEN_THREADS" \
    -pl data-scalpel-filegdb,data-scalpel-shapefile \
    install -DskipTests
  echo "正在构建 test69 Task Runner Uber JAR…"
  ./mvnw -q -T "$MAVEN_THREADS" -pl data-scalpel-task-engine -am \
    package -Dmaven.test.skip=true
fi
export DATASCALPEL_TASK_RUNNER_JAR="$TASK_RUNNER_JAR"

exec "$ROOT_DIR/start-local-dev.sh" "$@"
