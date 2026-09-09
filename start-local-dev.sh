#!/usr/bin/env bash

# 面向日常开发：只执行 compile，然后按 IDE Application Run Configuration 的方式
# 以 target/classes 和 Maven 运行时依赖组成的 classpath 启动各个 main 方法。
# Maven 编译默认按 CPU 核数并行；classpath 解析、服务启动和健康检查也会并发执行。
# 可通过 --threads 4、--threads 1C 或 DATASCALPEL_MAVEN_THREADS 调整 Maven 并行度。
#
# Task Runner 是独立的 Uber JAR，由 Task Dispatcher 通过 Docker 启动。
# 本地开发启动统一使用 maven.test.skip=true，既不执行测试，也不编译测试源码；
# 避免协议开发期主代码已经调整、历史测试尚未同步时阻塞本地联调。

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PREPARE_ONLY=false
MAVEN_THREADS="${DATASCALPEL_MAVEN_THREADS:-1C}"
MAVEN_SKIP_TESTS_ARGUMENT="-Dmaven.test.skip=true"

# 本地开发进程统一直连，不继承终端或代理软件注入的代理配置。
unset http_proxy https_proxy all_proxy no_proxy
unset HTTP_PROXY HTTPS_PROXY ALL_PROXY NO_PROXY
unset npm_config_proxy npm_config_https_proxy
DIRECT_JAVA_OPTIONS=(
  "-Djava.net.useSystemProxies=false"
  "-Djava.net.preferIPv4Stack=true"
  "-Dhttp.nonProxyHosts=localhost|127.*|[::1]|10.*|*.superhuang.cn|*.superhuang.net"
)
DIRECT_JAVA_OPTIONS_TEXT="${DIRECT_JAVA_OPTIONS[*]}"
export MAVEN_OPTS="${MAVEN_OPTS:+$MAVEN_OPTS }$DIRECT_JAVA_OPTIONS_TEXT"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --prepare)
      PREPARE_ONLY=true
      shift
      ;;
    --threads)
      if [[ $# -lt 2 || -z "$2" ]]; then
        echo "--threads 需要提供 Maven 并行度，例如 4 或 1C。"
        exit 1
      fi
      MAVEN_THREADS="$2"
      shift 2
      ;;
    --threads=*)
      MAVEN_THREADS="${1#*=}"
      if [[ -z "$MAVEN_THREADS" ]]; then
        echo "--threads 需要提供 Maven 并行度，例如 4 或 1C。"
        exit 1
      fi
      shift
      ;;
    *)
      echo "用法：$0 [--prepare] [--threads <线程数|每核线程数C>]"
      exit 1
      ;;
  esac
done
if [[ ! "$MAVEN_THREADS" =~ ^([1-9][0-9]*|([1-9][0-9]*([.][0-9]+)?|0[.][0-9]*[1-9][0-9]*)C)$ ]]; then
  echo "Maven 并行度无效：${MAVEN_THREADS}；请使用 4、1C 或 0.5C 这类格式。"
  exit 1
fi

LOCAL_CONFIG="$ROOT_DIR/config/application-local.yml"
SPRING_LOCAL_ARGUMENTS=(
  "--spring.profiles.active=local"
  "--spring.config.additional-location=optional:file:$LOCAL_CONFIG"
)
BACKEND_PORT="${BACKEND_PORT:-18080}"
ENGINE_PORT="${ENGINE_PORT:-8081}"
TASK_ENGINE_PORT="${TASK_ENGINE_PORT:-18091}"
TASK_ENGINE_URL="http://127.0.0.1:$TASK_ENGINE_PORT"
DISPATCHER_PORT="${DISPATCHER_PORT:-18092}"
DISPATCHER_URL="http://127.0.0.1:$DISPATCHER_PORT"
FRONTEND_PORT="${FRONTEND_PORT:-18887}"
SERVICE_STARTUP_TIMEOUT_SECONDS="${DATASCALPEL_LOCAL_SERVICE_STARTUP_TIMEOUT_SECONDS:-300}"
ENGINE_CODE="${DATASCALPEL_LOCAL_ENGINE_CODE:-local_engine}"
ENGINE_MANAGEMENT_TOKEN="${DATASCALPEL_ENGINE_MANAGEMENT_TOKEN:-change-me-engine-management-token}"
SERVICE_ENGINE_CREDENTIAL_KEY="${DATASCALPEL_SERVICE_ENGINE_CREDENTIAL_KEY:-MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=}"
MCP_CREDENTIAL_KEY="${DATASCALPEL_MCP_CREDENTIAL_KEY:-ZGF0YXNjYWxwZWwtbWNwLWxvY2FsLWRldi1rZXktdjE=}"
TASK_ENGINE_TOKEN="${DATASCALPEL_TASK_ENGINE_TOKEN:-change-me-task-engine-token}"
DISPATCHER_TOKEN="${DATASCALPEL_TASK_DISPATCHER_TOKEN:-change-me-task-dispatcher-token}"
DISPATCHER_WORK_DIR="${DATASCALPEL_TASK_DISPATCHER_WORK_DIRECTORY:-$ROOT_DIR/.local/task-dispatcher}"
COMPUTE_ENGINE_CREDENTIAL_KEY="${DATASCALPEL_COMPUTE_ENGINE_CREDENTIAL_KEY:-MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=}"
COMPUTE_ENGINE_NAME="${DATASCALPEL_LOCAL_COMPUTE_ENGINE_NAME:-本地 Docker 计算引擎}"
KAFKA_BOOTSTRAP_SERVERS="${DATASCALPEL_KAFKA_BOOTSTRAP_SERVERS:-home.superhuang.net:9094}"
KAFKA_STARTUP_TIMEOUT_SECONDS="${DATASCALPEL_KAFKA_STARTUP_TIMEOUT_SECONDS:-120}"
COMMAND_TOPIC="${DATASCALPEL_LOCAL_COMMAND_TOPIC:-datascalpel.execution.command.local}"
RUNNER_EVENT_TOPIC="${DATASCALPEL_LOCAL_RUNNER_EVENT_TOPIC:-datascalpel.runner.event.local}"
ADMIN_EVENT_TOPIC="${DATASCALPEL_LOCAL_ADMIN_EVENT_TOPIC:-datascalpel.execution.event}"
INVALID_MESSAGE_TOPIC="${DATASCALPEL_LOCAL_INVALID_MESSAGE_TOPIC:-datascalpel.execution.invalid-message.v1}"
FILE_STORAGE_ENDPOINT="${DATASCALPEL_FILE_STORAGE_ENDPOINT:-http://home.superhuang.net:9000}"
FILE_STORAGE_RUNNER_ENDPOINT="${DATASCALPEL_FILE_STORAGE_RUNNER_ENDPOINT:-$FILE_STORAGE_ENDPOINT}"
FILE_STORAGE_REGION="${DATASCALPEL_FILE_STORAGE_REGION:-us-east-1}"
FILE_STORAGE_BUCKET="${DATASCALPEL_FILE_STORAGE_BUCKET:-datascalpel}"
FILE_STORAGE_ROOT_PREFIX="${DATASCALPEL_FILE_STORAGE_ROOT_PREFIX:-data-scalpel}"
FILE_STORAGE_ACCESS_KEY="${DATASCALPEL_FILE_STORAGE_ACCESS_KEY:-minioadmin}"
FILE_STORAGE_SECRET_KEY="${DATASCALPEL_FILE_STORAGE_SECRET_KEY:-Gistack@123}"
ADMIN_DB_URL="${DATASCALPEL_DB_URL:-jdbc:postgresql://home.superhuang.net:5432/datascalpel}"
ADMIN_DB_USERNAME="${DATASCALPEL_DB_USERNAME:-datascalpel}"
ADMIN_DB_PASSWORD="${DATASCALPEL_DB_PASSWORD:-DataScalpel@123}"
DEFAULT_DISPATCHER_DB_URL="$(
  printf '%s' "$ADMIN_DB_URL" \
    | sed -E 's#(jdbc:postgresql://[^/]+/)[^?]+#\1datascalpel#'
)"
DISPATCHER_DB_URL="${DATASCALPEL_TASK_DISPATCHER_DB_URL:-$DEFAULT_DISPATCHER_DB_URL}"
DISPATCHER_DB_SCHEMA="${DATASCALPEL_TASK_DISPATCHER_DB_SCHEMA:-dispatcher}"
TASK_RUNNER_JAR="${DATASCALPEL_TASK_RUNNER_JAR:-$ROOT_DIR/data-scalpel-task-engine/target/data-scalpel-task-engine-0.1.0-SNAPSHOT-runner-local.jar}"
TASK_ENGINE_CONFIG="$ROOT_DIR/data-scalpel-task-engine/src/main/distribution/conf/task-engine.properties"
TASK_ENGINE_LOG_CONFIG="$ROOT_DIR/data-scalpel-task-engine/src/main/distribution/conf/log4j2.properties"
ADMIN_USERNAME="${DATASCALPEL_ADMIN_USERNAME:-admin}"
ADMIN_PASSWORD="${DATASCALPEL_ADMIN_PASSWORD:-admin123456}"
if [[ ! "$SERVICE_STARTUP_TIMEOUT_SECONDS" =~ ^[1-9][0-9]*$ ]]; then
  echo "DATASCALPEL_LOCAL_SERVICE_STARTUP_TIMEOUT_SECONDS 必须是正整数秒数。"
  exit 1
fi
BACKEND_PID=""
ENGINE_PID=""
TASK_ENGINE_PID=""
DISPATCHER_PID=""
FRONTEND_PID=""
ADMIN_ACCESS_TOKEN=""
STOPPED=false
BACKGROUND_JOB_PIDS=()
BACKGROUND_JOB_NAMES=()

cleanup() {
  if [[ "$STOPPED" == true ]]; then
    return
  fi
  STOPPED=true

  if [[ -z "$FRONTEND_PID" && -z "$BACKEND_PID" && -z "$ENGINE_PID" && -z "$TASK_ENGINE_PID" && -z "$DISPATCHER_PID" \
    && ${#BACKGROUND_JOB_PIDS[@]} -eq 0 ]]; then
    return
  fi

  echo
  echo "正在停止 DataScalpel 本地进程…"
  [[ -n "$FRONTEND_PID" ]] && kill "$FRONTEND_PID" 2>/dev/null || true
  [[ -n "$BACKEND_PID" ]] && kill "$BACKEND_PID" 2>/dev/null || true
  [[ -n "$ENGINE_PID" ]] && kill "$ENGINE_PID" 2>/dev/null || true
  [[ -n "$DISPATCHER_PID" ]] && kill "$DISPATCHER_PID" 2>/dev/null || true
  [[ -n "$TASK_ENGINE_PID" ]] && kill "$TASK_ENGINE_PID" 2>/dev/null || true
  if (( ${#BACKGROUND_JOB_PIDS[@]} > 0 )); then
    for pid in "${BACKGROUND_JOB_PIDS[@]}"; do
      kill "$pid" 2>/dev/null || true
    done
  fi
  [[ -n "$FRONTEND_PID" ]] && wait "$FRONTEND_PID" 2>/dev/null || true
  [[ -n "$BACKEND_PID" ]] && wait "$BACKEND_PID" 2>/dev/null || true
  [[ -n "$ENGINE_PID" ]] && wait "$ENGINE_PID" 2>/dev/null || true
  [[ -n "$DISPATCHER_PID" ]] && wait "$DISPATCHER_PID" 2>/dev/null || true
  [[ -n "$TASK_ENGINE_PID" ]] && wait "$TASK_ENGINE_PID" 2>/dev/null || true
  if (( ${#BACKGROUND_JOB_PIDS[@]} > 0 )); then
    for pid in "${BACKGROUND_JOB_PIDS[@]}"; do
      wait "$pid" 2>/dev/null || true
    done
  fi
}

trap cleanup EXIT INT TERM

command -v java >/dev/null 2>&1 || { echo "未找到 Java，请先安装并启用 JDK 21。"; exit 1; }
command -v pnpm >/dev/null 2>&1 || { echo "未找到 pnpm，请先安装 pnpm。"; exit 1; }
command -v curl >/dev/null 2>&1 || { echo "未找到 curl，无法完成本地服务配置。"; exit 1; }

if [[ ! -f "$LOCAL_CONFIG" ]]; then
  echo "未找到本地配置：$LOCAL_CONFIG"
  exit 1
fi

if [[ ! "$ENGINE_CODE" =~ ^[A-Za-z][A-Za-z0-9_]{0,63}$ ]]; then
  echo "DATASCALPEL_LOCAL_ENGINE_CODE 必须匹配 [A-Za-z][A-Za-z0-9_]{0,63}。"
  exit 1
fi

if [[ ! -f "$TASK_ENGINE_CONFIG" || ! -f "$TASK_ENGINE_LOG_CONFIG" ]]; then
  echo "未找到 Task Engine 开发配置文件。"
  exit 1
fi

cd "$ROOT_DIR"

echo "正在并行编译后端（Maven 线程：${MAVEN_THREADS}，不执行 package）…"
./mvnw -q -T "$MAVEN_THREADS" \
  "$MAVEN_SKIP_TESTS_ARGUMENT" \
  -pl data-scalpel-admin,data-scalpel-service-engine,data-scalpel-task-engine,data-scalpel-task-dispatcher \
  -am compile

reactor_runtime_classpath() {
  local application_module="$1"
  local marker="__DATASCALPEL_CLASSPATH__${application_module}="
  local end_marker="__DATASCALPEL_CLASSPATH_END__"
  local reactor_output
  local classpath

  echo "正在从 Maven Reactor 解析 $application_module 的运行时 classpath…" >&2
  # 必须通过 -am 使用当前 Reactor 的模块 POM 和 target/classes。
  # 单独 dependency:build-classpath 会读取本地仓库的旧快照 POM，即使替换
  # 项目 JAR 为 target/classes，也仍会漏掉当前源码新增的传递依赖。
  # exec:exec 会输出带边界标记的 classpath；这里保持单线程，避免并行 Reactor 日志插入标记内容。
  if ! reactor_output="$(./mvnw -q \
    "$MAVEN_SKIP_TESTS_ARGUMENT" \
    -pl "$application_module" \
    -am \
    compile \
    exec:exec \
    -Dexec.executable=/usr/bin/printf \
    '-Dexec.args=__DATASCALPEL_CLASSPATH__%s=%s__DATASCALPEL_CLASSPATH_END__\n ${project.artifactId} %classpath' \
    -Dexec.classpathScope=runtime)"; then
    printf '%s\n' "$reactor_output" | sed -n '/^\[ERROR\]/p' >&2
    echo "$application_module 的 Reactor 运行时 classpath 解析失败。" >&2
    return 1
  fi

  if [[ "$reactor_output" != *"$marker"* ]]; then
    echo "Maven Reactor 未返回 $application_module 的运行时 classpath。" >&2
    return 1
  fi
  classpath="${reactor_output#*"$marker"}"
  classpath="${classpath%%"$end_marker"*}"
  if [[ -z "$classpath" ]]; then
    echo "$application_module 的 Reactor 运行时 classpath 为空。" >&2
    return 1
  fi

  printf '%s' "$classpath"
}

start_classpath_job() {
  local name="$1"
  local output_file="$2"
  local resolver="$3"
  shift 3

  "$resolver" "$@" >"$output_file" &
  BACKGROUND_JOB_PIDS+=("$!")
  BACKGROUND_JOB_NAMES+=("$name")
}

wait_for_background_jobs() {
  local failed=false
  local index
  local pid

  for ((index = 0; index < ${#BACKGROUND_JOB_PIDS[@]}; index++)); do
    pid="${BACKGROUND_JOB_PIDS[$index]}"
    if ! wait "$pid"; then
      echo "${BACKGROUND_JOB_NAMES[$index]}失败。" >&2
      failed=true
    fi
  done
  BACKGROUND_JOB_PIDS=()
  BACKGROUND_JOB_NAMES=()

  [[ "$failed" == false ]]
}

ADMIN_CLASSPATH_FILE="$ROOT_DIR/data-scalpel-admin/target/dev-launch-classpath.txt"
ENGINE_CLASSPATH_FILE="$ROOT_DIR/data-scalpel-service-engine/target/dev-launch-classpath.txt"
TASK_ENGINE_CLASSPATH_FILE="$ROOT_DIR/data-scalpel-task-engine/target/dev-launch-classpath.txt"
DISPATCHER_CLASSPATH_FILE="$ROOT_DIR/data-scalpel-task-dispatcher/target/dev-launch-classpath.txt"

echo "正在并行解析各应用的运行时 classpath…"
start_classpath_job "Admin 运行时 classpath 解析" "$ADMIN_CLASSPATH_FILE" \
  reactor_runtime_classpath data-scalpel-admin
start_classpath_job "Service Engine 运行时 classpath 解析" "$ENGINE_CLASSPATH_FILE" \
  reactor_runtime_classpath data-scalpel-service-engine
start_classpath_job "Task Engine 运行时 classpath 解析" "$TASK_ENGINE_CLASSPATH_FILE" \
  reactor_runtime_classpath data-scalpel-task-engine
start_classpath_job "Task Dispatcher 运行时 classpath 解析" "$DISPATCHER_CLASSPATH_FILE" \
  reactor_runtime_classpath data-scalpel-task-dispatcher

load_runtime_classpaths() {
  local file
  for file in \
    "$ADMIN_CLASSPATH_FILE" \
    "$ENGINE_CLASSPATH_FILE" \
    "$TASK_ENGINE_CLASSPATH_FILE" \
    "$DISPATCHER_CLASSPATH_FILE"; do
    if [[ ! -s "$file" ]]; then
      echo "运行时 classpath 文件不存在或为空：$file" >&2
      return 1
    fi
  done

  ADMIN_CLASSPATH="$(<"$ADMIN_CLASSPATH_FILE")"
  ENGINE_CLASSPATH="$(<"$ENGINE_CLASSPATH_FILE")"
  TASK_ENGINE_CLASSPATH="$(<"$TASK_ENGINE_CLASSPATH_FILE")"
  DISPATCHER_CLASSPATH="$(<"$DISPATCHER_CLASSPATH_FILE")"
}

if [[ "$PREPARE_ONLY" == true ]]; then
  wait_for_background_jobs
  load_runtime_classpaths
  echo "开发运行所需的 classpath 已准备完成；未启动任何进程。"
  exit 0
fi

command -v docker >/dev/null 2>&1 || { echo "未找到 Docker CLI，无法运行 Spark Canvas 任务。"; exit 1; }
docker info >/dev/null 2>&1 || { echo "Docker Daemon 不可用，请先启动 Docker。"; exit 1; }
command -v nc >/dev/null 2>&1 || { echo "未找到 nc，无法检查 Kafka Broker。"; exit 1; }

KAFKA_FIRST="${KAFKA_BOOTSTRAP_SERVERS%%,*}"
KAFKA_HOST="${KAFKA_FIRST%:*}"
KAFKA_PORT="${KAFKA_FIRST##*:}"
KAFKA_STARTUP_DEADLINE=$((SECONDS + KAFKA_STARTUP_TIMEOUT_SECONDS))
until nc -z -w 5 "$KAFKA_HOST" "$KAFKA_PORT" >/dev/null 2>&1; do
  if (( SECONDS >= KAFKA_STARTUP_DEADLINE )); then
    break
  fi
  sleep 2
done
nc -z -w 5 "$KAFKA_HOST" "$KAFKA_PORT" >/dev/null 2>&1 || {
  echo "Kafka Broker 不可用：$KAFKA_FIRST"
  exit 1
}
curl --fail --silent --show-error "$FILE_STORAGE_ENDPOINT/minio/health/live" >/dev/null || {
  echo "MinIO 不可用：$FILE_STORAGE_ENDPOINT"
  exit 1
}

if [[ ! -f "$TASK_RUNNER_JAR" ]]; then
  echo "未找到 Task Runner Uber JAR：$TASK_RUNNER_JAR"
  echo "首次运行真实 Canvas 任务或修改 Runner 后，请单独执行："
  echo "  ./mvnw -pl data-scalpel-task-engine -am package -Dmaven.test.skip=true"
  exit 1
fi
if [[ -n "$(find \
  "$ROOT_DIR/data-scalpel-task-engine/src" \
  "$ROOT_DIR/data-scalpel-task-engine/pom.xml" \
  "$ROOT_DIR/data-scalpel-filegdb/src" \
  "$ROOT_DIR/data-scalpel-filegdb/pom.xml" \
  "$ROOT_DIR/data-scalpel-filegdb-s3/src" \
  "$ROOT_DIR/data-scalpel-filegdb-s3/pom.xml" \
  "$ROOT_DIR/data-scalpel-shapefile/src" \
  "$ROOT_DIR/data-scalpel-shapefile/pom.xml" \
  "$ROOT_DIR/data-scalpel-shapefile-s3/src" \
  "$ROOT_DIR/data-scalpel-shapefile-s3/pom.xml" \
  -type f -newer "$TASK_RUNNER_JAR" -print -quit)" ]]; then
  echo "Task Runner Uber JAR 已过期：$TASK_RUNNER_JAR"
  echo "请先重新执行："
  echo "  ./mvnw -pl data-scalpel-task-engine -am package -Dmaven.test.skip=true"
  exit 1
fi
mkdir -p "$DISPATCHER_WORK_DIR"

# classpath 解析与 Docker、Kafka、MinIO、Runner 检查并行进行，到真正启动进程前再汇合。
wait_for_background_jobs
load_runtime_classpaths

export DATASCALPEL_ENGINE_MANAGEMENT_TOKEN="$ENGINE_MANAGEMENT_TOKEN"
export DATASCALPEL_SERVICE_ENGINE_CREDENTIAL_KEY="$SERVICE_ENGINE_CREDENTIAL_KEY"
export DATASCALPEL_MCP_CREDENTIAL_KEY="$MCP_CREDENTIAL_KEY"
export DATASCALPEL_TASK_ENGINE_TOKEN="$TASK_ENGINE_TOKEN"
export DATASCALPEL_COMPUTE_ENGINE_CREDENTIAL_KEY="$COMPUTE_ENGINE_CREDENTIAL_KEY"
export DATASCALPEL_TASK_DISPATCHER_TOKEN="$DISPATCHER_TOKEN"
export DATASCALPEL_EXECUTION_KAFKA_BOOTSTRAP_SERVERS="$KAFKA_BOOTSTRAP_SERVERS"
export DATASCALPEL_EXECUTION_KAFKA_ADMIN_EVENT_TOPICS="$ADMIN_EVENT_TOPIC"
export DATASCALPEL_EXECUTION_KAFKA_INVALID_MESSAGE_TOPIC="$INVALID_MESSAGE_TOPIC"
export DATASCALPEL_DB_URL="$ADMIN_DB_URL"
export DATASCALPEL_DB_USERNAME="$ADMIN_DB_USERNAME"
export DATASCALPEL_DB_PASSWORD="$ADMIN_DB_PASSWORD"
export DATASCALPEL_FILE_STORAGE_ENDPOINT="$FILE_STORAGE_ENDPOINT"
export DATASCALPEL_FILE_STORAGE_RUNNER_ENDPOINT="$FILE_STORAGE_RUNNER_ENDPOINT"
export DATASCALPEL_FILE_STORAGE_REGION="$FILE_STORAGE_REGION"
export DATASCALPEL_FILE_STORAGE_BUCKET="$FILE_STORAGE_BUCKET"
export DATASCALPEL_FILE_STORAGE_ROOT_PREFIX="$FILE_STORAGE_ROOT_PREFIX"
export DATASCALPEL_FILE_STORAGE_ACCESS_KEY="$FILE_STORAGE_ACCESS_KEY"
export DATASCALPEL_FILE_STORAGE_SECRET_KEY="$FILE_STORAGE_SECRET_KEY"
export DATASCALPEL_ADMIN_USERNAME="$ADMIN_USERNAME"
export DATASCALPEL_ADMIN_PASSWORD="$ADMIN_PASSWORD"

wait_for_health() {
  local name="$1"
  local health_url="$2"
  local pid="$3"
  local deadline=$((SECONDS + SERVICE_STARTUP_TIMEOUT_SECONDS))
  local http_status

  while (( SECONDS < deadline )); do
    if ! kill -0 "$pid" 2>/dev/null; then
      echo "$name 启动进程已退出。"
      return 1
    fi
    http_status="$(curl --silent \
      --connect-timeout 2 \
      --max-time 5 \
      --output /dev/null \
      --write-out '%{http_code}' \
      "$health_url" || true)"
    if [[ "$http_status" == "200" ]]; then
      return 0
    fi
    sleep 1
  done

  echo "$name 未能在约 ${SERVICE_STARTUP_TIMEOUT_SECONDS} 秒内就绪：$health_url（最后 HTTP 状态：${http_status:-000}）"
  return 1
}

start_health_check() {
  local name="$1"
  local health_url="$2"
  local pid="$3"

  wait_for_health "$name" "$health_url" "$pid" &
  BACKGROUND_JOB_PIDS+=("$!")
  BACKGROUND_JOB_NAMES+=("$name 健康检查")
}

json_escape() {
  local value="$1"
  value="${value//\\\\/\\\\\\\\}"
  value="${value//\"/\\\"}"
  value="${value//$'\n'/\\n}"
  value="${value//$'\r'/\\r}"
  value="${value//$'\t'/\\t}"
  printf '%s' "$value"
}

wait_for_admin_token() {
  local backend_url="$1"
  local login_response curl_output token
  local http_status curl_exit last_result
  local attempt=0
  local deadline=$((SECONDS + 60))

  while (( SECONDS < deadline )); do
    attempt=$((attempt + 1))
    if curl_output="$(curl --silent \
      --connect-timeout 2 \
      --max-time 5 \
      --request POST \
      --header 'Content-Type: application/json' \
      --data "{\"username\":\"$(json_escape "$ADMIN_USERNAME")\",\"password\":\"$(json_escape "$ADMIN_PASSWORD")\"}" \
      --write-out $'\n%{http_code}' \
      "$backend_url/api/v1/auth/login")"; then
      http_status="${curl_output##*$'\n'}"
      login_response="${curl_output%$'\n'*}"
      case "$http_status" in
        200)
          token="$(printf '%s' "$login_response" \
            | sed -nE 's/.*"accessToken"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/p')"
          if [[ -n "$token" ]]; then
            printf '%s' "$token"
            return 0
          fi
          echo "管理员登录接口返回 HTTP 200，但响应中没有 accessToken；请检查登录响应契约。" >&2
          return 1
          ;;
        401|403)
          echo "管理员登录被拒绝（HTTP $http_status）。请检查 DATASCALPEL_ADMIN_USERNAME、DATASCALPEL_ADMIN_PASSWORD，以及已有管理员账号是否启用。" >&2
          echo "注意：启动配置只会创建不存在的管理员，不会重置数据库中已有管理员的密码。" >&2
          return 1
          ;;
        400|404|405|415|422)
          echo "管理员登录请求不可用（HTTP $http_status）：$backend_url/api/v1/auth/login" >&2
          return 1
          ;;
        *)
          last_result="HTTP $http_status"
          ;;
      esac
    else
      curl_exit=$?
      case "$curl_exit" in
        7) last_result="无法连接后端" ;;
        28) last_result="请求在 5 秒内未完成" ;;
        *) last_result="curl 退出码 $curl_exit" ;;
      esac
    fi

    if (( attempt == 1 || attempt % 5 == 0 )); then
      echo "管理员登录暂未成功（第 $attempt 次，${last_result:-未知错误}），继续等待…" >&2
    fi
    if (( SECONDS < deadline )); then
      sleep 1
    fi
  done

  echo "本地管理员在约 60 秒内未能登录（最后结果：${last_result:-未知错误}）。" >&2
  return 1
}

configure_task_engine() {
  local backend_url="$1"
  local token="$2"
  local configuration_list configuration_id

  configuration_list="$(curl --fail --silent --show-error \
    --get \
    --header "Authorization: Bearer $token" \
    --data-urlencode 'search=configKey:"task.engine.base-url"' \
    --data-urlencode 'page=0' \
    --data-urlencode 'size=1' \
    "$backend_url/api/v1/system/configurations")"
  configuration_id="$(printf '%s' "$configuration_list" | sed -nE 's/.*"id":"([0-9a-fA-F-]{36})".*/\1/p')"
  if [[ -z "$configuration_id" ]]; then
    echo "系统配置中未找到 task.engine.base-url。"
    return 1
  fi

  echo "正在配置 Task Engine 地址：$TASK_ENGINE_URL"
  curl --fail --silent --show-error \
    --request POST \
    --header "Authorization: Bearer $token" \
    --header 'Content-Type: application/json' \
    --data "{\"configValue\":\"$(json_escape "$TASK_ENGINE_URL")\"}" \
    "$backend_url/api/v1/system/configurations/$configuration_id/actions/update" >/dev/null
}

register_local_engine() {
  local backend_url="http://localhost:$BACKEND_PORT"
  local token engine_list engine_id

  echo "正在等待本地管理员可登录…"
  if ! token="$(wait_for_admin_token "$backend_url")"; then
    return 1
  fi
  ADMIN_ACCESS_TOKEN="$token"

  echo "正在查询本地服务引擎：$ENGINE_CODE"
  engine_list="$(curl --fail --silent --show-error \
    --get \
    --header "Authorization: Bearer $token" \
    --data-urlencode "search=code:\"$ENGINE_CODE\"" \
    --data-urlencode 'page=0' \
    --data-urlencode 'size=1' \
    "$backend_url/api/v1/service-engines")"
  engine_id="$(printf '%s' "$engine_list" | sed -nE 's/.*"id":"([0-9a-fA-F-]{36})".*/\1/p')"

  local engine_payload
  engine_payload="{\"name\":\"本地开发服务引擎\",\"adminUrl\":\"http://localhost:$ENGINE_PORT\",\"runtimeUrl\":\"http://localhost:$ENGINE_PORT\",\"managementToken\":\"$(json_escape "$ENGINE_MANAGEMENT_TOKEN")\",\"enabled\":true,\"description\":\"由 start-local-dev.sh 自动登记，仅供本机开发测试。\"}"
  if [[ -z "$engine_id" ]]; then
    echo "正在登记本地服务引擎：$ENGINE_CODE"
    curl --fail --silent --show-error \
      --request POST \
      --header "Authorization: Bearer $token" \
      --header 'Content-Type: application/json' \
      --data "{\"code\":\"$ENGINE_CODE\",${engine_payload#\{}" \
      "$backend_url/api/v1/service-engines" >/dev/null
  else
    echo "正在更新已登记的本地服务引擎：$ENGINE_CODE"
    curl --fail --silent --show-error \
      --request POST \
      --header "Authorization: Bearer $token" \
      --header 'Content-Type: application/json' \
      --data "$engine_payload" \
      "$backend_url/api/v1/service-engines/$engine_id/actions/update" >/dev/null
  fi

  if [[ -z "$engine_id" ]]; then
    engine_list="$(curl --fail --silent --show-error \
      --get \
      --header "Authorization: Bearer $token" \
      --data-urlencode "search=code:\"$ENGINE_CODE\"" \
      --data-urlencode 'page=0' \
      --data-urlencode 'size=1' \
      "$backend_url/api/v1/service-engines")"
    engine_id="$(printf '%s' "$engine_list" | sed -nE 's/.*"id":"([0-9a-fA-F-]{36})".*/\1/p')"
  fi
  if [[ -z "$engine_id" ]]; then
    echo "本地服务引擎登记后未找到记录：$ENGINE_CODE"
    return 1
  fi

  echo "正在验证本地服务引擎连通性…"
  curl --fail --silent --show-error \
    --request POST \
    --header "Authorization: Bearer $token" \
    "$backend_url/api/v1/service-engines/$engine_id/actions/test" >/dev/null

  echo "正在同步本地服务引擎访问策略…"
  curl --fail --silent --show-error \
    --request POST \
    --header "Authorization: Bearer $token" \
    --header 'Content-Type: application/json' \
    --data '{"allowCidrs":["127.0.0.1/32","::1/128"],"denyCidrs":[]}' \
    "$backend_url/api/v1/service-engines/$engine_id/actions/update-access-policy" >/dev/null

  configure_task_engine "$backend_url" "$token"
}

register_local_compute_engine() {
  local backend_url="http://localhost:$BACKEND_PORT"
  local token="$ADMIN_ACCESS_TOKEN"
  local engine_list engine_id registration_state payload

  if [[ -z "$token" ]]; then
    echo "没有可用于登记计算引擎的管理员 Token。"
    return 1
  fi

  engine_list="$(curl --fail --silent --show-error \
    --get \
    --header "Authorization: Bearer $token" \
    --data-urlencode "search=name:\"$COMPUTE_ENGINE_NAME\"" \
    --data-urlencode 'page=0' \
    --data-urlencode 'size=1' \
    "$backend_url/api/v1/compute-engines")"
  engine_id="$(printf '%s' "$engine_list" | sed -nE 's/.*"id":"([0-9a-fA-F-]{36})".*/\1/p')"
  registration_state="$(printf '%s' "$engine_list" | sed -nE 's/.*"registrationState":"([A-Z_]+)".*/\1/p')"
  payload="{\"name\":\"$(json_escape "$COMPUTE_ENGINE_NAME")\",\"description\":\"由 start-local-dev.sh 管理的本地 Docker Spark 计算引擎\",\"dispatcherBaseUrl\":\"$DISPATCHER_URL\",\"accessToken\":\"$(json_escape "$DISPATCHER_TOKEN")\",\"expectedBackendType\":\"LOCAL_DOCKER\",\"commandTopic\":\"$(json_escape "$COMMAND_TOPIC")\",\"runnerEventTopic\":\"$(json_escape "$RUNNER_EVENT_TOPIC")\",\"adminEventTopic\":\"$(json_escape "$ADMIN_EVENT_TOPIC")\",\"maxQueuedExecutions\":20,\"maxConcurrentSubmissions\":2,\"maxInFlightApplications\":2}"

  if [[ -z "$engine_id" ]]; then
    echo "正在创建本地 Docker 计算引擎。"
    local create_response
    create_response="$(curl --fail --silent --show-error \
      --request POST \
      --header "Authorization: Bearer $token" \
      --header 'Content-Type: application/json' \
      --data "$payload" \
      "$backend_url/api/v1/compute-engines")"
    engine_id="$(printf '%s' "$create_response" | sed -nE 's/.*"id":"([0-9a-fA-F-]{36})".*/\1/p')"
    registration_state="INACTIVE"
  elif [[ "$registration_state" == "INACTIVE" || "$registration_state" == "ERROR" ]]; then
    echo "正在更新本地 Docker 计算引擎。"
    curl --fail --silent --show-error \
      --request POST \
      --header "Authorization: Bearer $token" \
      --header 'Content-Type: application/json' \
      --data "$payload" \
      "$backend_url/api/v1/compute-engines/$engine_id/actions/update" >/dev/null
  elif [[ "$registration_state" != "ACTIVE" ]]; then
    echo "本地计算引擎当前状态为 $registration_state，请先在管理页面完成 Drain/反注册。"
    return 1
  fi

  if [[ -z "$engine_id" ]]; then
    echo "本地计算引擎创建后未返回 ID。"
    return 1
  fi

  echo "正在验证本地 Docker 计算引擎连通性…"
  curl --fail --silent --show-error \
    --request POST \
    --header "Authorization: Bearer $token" \
    "$backend_url/api/v1/compute-engines/$engine_id/actions/test" >/dev/null
  if [[ "$registration_state" != "ACTIVE" ]]; then
    echo "正在激活本地 Docker 计算引擎。"
    curl --fail --silent --show-error \
      --request POST \
      --header "Authorization: Bearer $token" \
      "$backend_url/api/v1/compute-engines/$engine_id/actions/register" >/dev/null
  fi
}

echo "正在按 classpath 启动服务引擎：http://localhost:$ENGINE_PORT"
ENGINE_ENV=("DATASCALPEL_ENGINE_CODE=$ENGINE_CODE")
[[ -n "${DATASCALPEL_ENGINE_DB_URL:-}" ]] && ENGINE_ENV+=(
  "DATASCALPEL_ENGINE_DB_URL=$DATASCALPEL_ENGINE_DB_URL"
  "SPRING_DATASOURCE_URL=$DATASCALPEL_ENGINE_DB_URL"
)
[[ -n "${DATASCALPEL_ENGINE_DB_USERNAME:-}" ]] && ENGINE_ENV+=(
  "DATASCALPEL_ENGINE_DB_USERNAME=$DATASCALPEL_ENGINE_DB_USERNAME"
  "SPRING_DATASOURCE_USERNAME=$DATASCALPEL_ENGINE_DB_USERNAME"
)
[[ -n "${DATASCALPEL_ENGINE_DB_PASSWORD:-}" ]] && ENGINE_ENV+=(
  "DATASCALPEL_ENGINE_DB_PASSWORD=$DATASCALPEL_ENGINE_DB_PASSWORD"
  "SPRING_DATASOURCE_PASSWORD=$DATASCALPEL_ENGINE_DB_PASSWORD"
)
env "${ENGINE_ENV[@]}" java "${DIRECT_JAVA_OPTIONS[@]}" -cp "$ENGINE_CLASSPATH" \
  cn.superhuang.data.scalpel.engine.DataScalpelServiceEngineApplication \
  "${SPRING_LOCAL_ARGUMENTS[@]}" --server.port="$ENGINE_PORT" &
ENGINE_PID=$!

echo "正在按 classpath 启动 Task Engine：$TASK_ENGINE_URL"
DATASCALPEL_TASK_ENGINE_HOST="127.0.0.1" \
DATASCALPEL_TASK_ENGINE_PORT="$TASK_ENGINE_PORT" \
java "${DIRECT_JAVA_OPTIONS[@]}" -Dlog4j.configurationFile="$TASK_ENGINE_LOG_CONFIG" \
  -cp "$TASK_ENGINE_CLASSPATH" \
  cn.superhuang.datascalpel.taskengine.TaskEngineDaemon "$TASK_ENGINE_CONFIG" &
TASK_ENGINE_PID=$!

echo "正在按 classpath 启动 Task Dispatcher：$DISPATCHER_URL"
DATASCALPEL_TASK_DISPATCHER_PORT="$DISPATCHER_PORT" \
DATASCALPEL_TASK_DISPATCHER_BACKEND="LOCAL_DOCKER" \
DATASCALPEL_TASK_DISPATCHER_ENSURE_TOPICS="true" \
DATASCALPEL_TASK_DISPATCHER_RUNNER_JAR="$TASK_RUNNER_JAR" \
DATASCALPEL_TASK_DISPATCHER_WORK_DIRECTORY="$DISPATCHER_WORK_DIR" \
DATASCALPEL_KAFKA_BOOTSTRAP_SERVERS="$KAFKA_BOOTSTRAP_SERVERS" \
DATASCALPEL_KAFKA_RUNNER_BOOTSTRAP_SERVERS="$KAFKA_BOOTSTRAP_SERVERS" \
DATASCALPEL_FILE_STORAGE_ENDPOINT="$FILE_STORAGE_ENDPOINT" \
DATASCALPEL_FILE_STORAGE_RUNNER_ENDPOINT="$FILE_STORAGE_RUNNER_ENDPOINT" \
DATASCALPEL_FILE_STORAGE_REGION="$FILE_STORAGE_REGION" \
DATASCALPEL_FILE_STORAGE_BUCKET="$FILE_STORAGE_BUCKET" \
DATASCALPEL_FILE_STORAGE_ROOT_PREFIX="$FILE_STORAGE_ROOT_PREFIX" \
DATASCALPEL_FILE_STORAGE_ACCESS_KEY="$FILE_STORAGE_ACCESS_KEY" \
DATASCALPEL_FILE_STORAGE_SECRET_KEY="$FILE_STORAGE_SECRET_KEY" \
DATASCALPEL_DB_URL="$DISPATCHER_DB_URL" \
DATASCALPEL_TASK_DISPATCHER_DB_SCHEMA="$DISPATCHER_DB_SCHEMA" \
SPRING_DATASOURCE_URL="$DISPATCHER_DB_URL" \
SPRING_DATASOURCE_USERNAME="$ADMIN_DB_USERNAME" \
SPRING_DATASOURCE_PASSWORD="$ADMIN_DB_PASSWORD" \
java "${DIRECT_JAVA_OPTIONS[@]}" -cp "$DISPATCHER_CLASSPATH" \
  cn.superhuang.data.scalpel.dispatcher.TaskDispatcherApplication \
  "${SPRING_LOCAL_ARGUMENTS[@]}" &
DISPATCHER_PID=$!

echo "正在按 classpath 启动后端：http://localhost:$BACKEND_PORT"
java "${DIRECT_JAVA_OPTIONS[@]}" -cp "$ADMIN_CLASSPATH" \
  cn.superhuang.data.scalpel.admin.DataScalpelAdminApplication \
  "${SPRING_LOCAL_ARGUMENTS[@]}" --server.port="$BACKEND_PORT" &
BACKEND_PID=$!

echo "正在启动前端：http://localhost:$FRONTEND_PORT"
BACKEND_ORIGIN="http://localhost:$BACKEND_PORT" pnpm --dir "$ROOT_DIR/data-scalpel-ui" dev --port "$FRONTEND_PORT" &
FRONTEND_PID=$!

echo "正在并行等待各本地服务就绪…"
start_health_check "服务引擎" "http://localhost:$ENGINE_PORT/actuator/health" "$ENGINE_PID"
start_health_check "Task Engine" "$TASK_ENGINE_URL/health/ready" "$TASK_ENGINE_PID"
start_health_check "Task Dispatcher" "$DISPATCHER_URL/health/ready" "$DISPATCHER_PID"
start_health_check "后端" "http://localhost:$BACKEND_PORT/actuator/health" "$BACKEND_PID"
start_health_check "前端" "http://localhost:$FRONTEND_PORT" "$FRONTEND_PID"
wait_for_background_jobs

register_local_engine
register_local_compute_engine

echo "DataScalpel 前后端、服务引擎、Task Engine 与 Dispatcher 已启动（Java 服务使用 classpath，未执行完整 package），按 Ctrl+C 一起停止。"
wait "$ENGINE_PID" "$TASK_ENGINE_PID" "$DISPATCHER_PID" "$BACKEND_PID" "$FRONTEND_PID"
