#!/usr/bin/env bash

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
PROJECT_ROOT="$SCRIPT_DIR"
SERVICE_NAME="service-engine"
CONTAINER_JAR_PATH="/opt/datascalpel/app.jar"

if [[ -t 1 ]]; then
    COLOR_CYAN=$'\033[36m'
    COLOR_GREEN=$'\033[32m'
    COLOR_YELLOW=$'\033[33m'
    COLOR_RED=$'\033[31m'
    COLOR_RESET=$'\033[0m'
else
    COLOR_CYAN=""
    COLOR_GREEN=""
    COLOR_YELLOW=""
    COLOR_RED=""
    COLOR_RESET=""
fi

trim_whitespace() {
    local value="$1"
    value="${value#"${value%%[![:space:]]*}"}"
    value="${value%"${value##*[![:space:]]}"}"
    printf '%s' "$value"
}

write_step() {
    printf '\n%s==> %s%s\n' "$COLOR_CYAN" "$1" "$COLOR_RESET"
}

write_success() {
    printf '%s%s%s\n' "$COLOR_GREEN" "$1" "$COLOR_RESET"
}

write_warning() {
    printf '%s警告：%s%s\n' "$COLOR_YELLOW" "$1" "$COLOR_RESET" >&2
}

die() {
    printf '\n%s部署失败：%s%s\n' "$COLOR_RED" "$1" "$COLOR_RESET" >&2
    exit 1
}

ssh_remote() {
    local command_text="$1"
    ssh \
        -n \
        -o BatchMode=yes \
        -o ConnectTimeout=10 \
        "$SSH_TARGET" \
        "${command_text//$'\r'/}"
}

local_sha256() {
    local file_path="$1"
    if command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "$file_path" | awk '{print $1}'
    else
        sha256sum "$file_path" | awk '{print $1}'
    fi
}

wait_container_healthy() {
    local container_name="$1"
    local timeout_seconds="${2:-180}"
    local wait_command=""

    IFS= read -r -d '' wait_command <<'REMOTE' || true
set -eu
deadline=$(($(date +%s) + __TIMEOUT__))
last_status=
while [ "$(date +%s)" -lt "$deadline" ]; do
    status=$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' '__CONTAINER__' 2>/dev/null || printf missing)
    if [ "$status" != "$last_status" ]; then
        printf '容器状态：%s\n' "$status"
        last_status=$status
    fi
    case "$status" in
        healthy) exit 0 ;;
        unhealthy|exited|dead) exit 1 ;;
    esac
    sleep 3
done
printf '容器健康检查超时，最后状态：%s\n' "$last_status" >&2
exit 1
REMOTE

    wait_command="${wait_command//__TIMEOUT__/$timeout_seconds}"
    wait_command="${wait_command//__CONTAINER__/$container_name}"
    ssh_remote "$wait_command"
}

cleanup_temporary_jar() {
    if ! ssh_remote "rm -f '$REMOTE_TEMPORARY_JAR'"; then
        write_warning "清理远端临时 JAR 失败：$REMOTE_TEMPORARY_JAR"
    fi
}

handle_deployment_failure() {
    local deployment_error="$1"
    local rollback_temporary_jar

    printf '\n%s更新失败：%s%s\n' "$COLOR_RED" "$deployment_error" "$COLOR_RESET" >&2
    if ! ssh_remote "docker logs --tail 200 '$CONTAINER_NAME'"; then
        write_warning "读取 Service Engine 日志失败"
    fi

    if [[ "$SWAPPED" == "true" ]]; then
        write_step "自动恢复旧 JAR 并重建容器"
        rollback_temporary_jar="$REMOTE_JAR_PATH.rollback-$BUILD_TAG"

        if ! ssh_remote "set -eu
cp -a '$REMOTE_BACKUP_JAR' '$rollback_temporary_jar'
mv -f '$rollback_temporary_jar' '$REMOTE_JAR_PATH'
cd '$COMPOSE_DIRECTORY'
docker compose up -d --force-recreate '$SERVICE_NAME'"; then
            cleanup_temporary_jar
            die "更新失败且自动回滚失败。原始错误：$deployment_error；回滚错误：恢复旧 Service Engine JAR 失败"
        fi

        if ! wait_container_healthy "$CONTAINER_NAME" 180; then
            cleanup_temporary_jar
            die "更新失败且自动回滚失败。原始错误：$deployment_error；回滚错误：旧版本容器未恢复健康"
        fi
        printf '%s已恢复旧版本：%s%s\n' "$COLOR_YELLOW" "$REMOTE_BACKUP_JAR" "$COLOR_RESET"
    fi

    cleanup_temporary_jar
    die "$deployment_error"
}

MODE="$(trim_whitespace "${1:-deploy}")"
MODE="$(printf '%s' "$MODE" | tr '[:upper:]' '[:lower:]')"

SSH_TARGET="$(trim_whitespace "${DATASCALPEL_SERVICE_ENGINE_SSH_TARGET:-}")"
if [[ -z "$SSH_TARGET" ]]; then
    SSH_TARGET="linux5"
fi

COMPOSE_DIRECTORY="$(trim_whitespace "${DATASCALPEL_SERVICE_ENGINE_COMPOSE_DIR:-}")"
if [[ -z "$COMPOSE_DIRECTORY" ]]; then
    COMPOSE_DIRECTORY="/root/docker-compose/datascalpel-service-engine"
fi
while [[ "$COMPOSE_DIRECTORY" != "/" && "$COMPOSE_DIRECTORY" == */ ]]; do
    COMPOSE_DIRECTORY="${COMPOSE_DIRECTORY%/}"
done

if [[ "$MODE" != "deploy" && "$MODE" != "check" ]]; then
    die "不支持的参数：$MODE（支持：deploy、check）"
fi
if [[ ! "$SSH_TARGET" =~ ^[a-zA-Z0-9_.@][a-zA-Z0-9_.@-]*$ ]]; then
    die "SSH 目标包含不支持的字符"
fi
if [[ ! "$COMPOSE_DIRECTORY" =~ ^/[a-zA-Z0-9_./-]+$ || "$COMPOSE_DIRECTORY" == *".."* ]]; then
    die "Compose 目录必须是不包含 .. 的 Linux 绝对路径"
fi

for required_command in ssh scp awk tr; do
    if ! command -v "$required_command" >/dev/null 2>&1; then
        die "找不到命令：$required_command"
    fi
done
if ! command -v shasum >/dev/null 2>&1 && ! command -v sha256sum >/dev/null 2>&1; then
    die "找不到 SHA-256 工具：需要 shasum 或 sha256sum"
fi

MAVEN_WRAPPER="$PROJECT_ROOT/mvnw"
if [[ ! -x "$MAVEN_WRAPPER" ]]; then
    die "找不到可执行的 Maven Wrapper：$MAVEN_WRAPPER"
fi

write_step "检查 $SSH_TARGET 上的 Compose 服务"
INSPECTION_COMMAND=""
IFS= read -r -d '' INSPECTION_COMMAND <<'REMOTE' || true
set -eu
cd '__COMPOSE__'
test -f docker-compose.yml -o -f compose.yaml
docker compose config --services | grep -Fx '__SERVICE__' >/dev/null
container_id=$(docker compose ps -q '__SERVICE__')
test -n "$container_id"
container_name=$(docker inspect --format '{{.Name}}' "$container_id" | sed 's#^/##')
jar_path=$(docker inspect --format '{{range .Mounts}}{{println .Destination .Source}}{{end}}' "$container_id" | grep -F '__CONTAINER_JAR__ ' | cut -d ' ' -f 2-)
test -n "$jar_path"
test -f "$jar_path"
jar_hash=$(sha256sum "$jar_path" | cut -d ' ' -f 1)
health=$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container_id")
printf '%s\n%s\n%s\n%s\n' "$container_name" "$jar_path" "$jar_hash" "$health"
REMOTE
INSPECTION_COMMAND="${INSPECTION_COMMAND//__COMPOSE__/$COMPOSE_DIRECTORY}"
INSPECTION_COMMAND="${INSPECTION_COMMAND//__SERVICE__/$SERVICE_NAME}"
INSPECTION_COMMAND="${INSPECTION_COMMAND//__CONTAINER_JAR__/$CONTAINER_JAR_PATH}"

if ! INSPECTION="$(ssh_remote "$INSPECTION_COMMAND")"; then
    die "读取远端 Service Engine 状态失败"
fi

INSPECTION_LINES=()
while IFS= read -r line; do
    INSPECTION_LINES+=("$line")
done <<< "$INSPECTION"
if [[ "${#INSPECTION_LINES[@]}" -ne 4 ]]; then
    die "无法识别远端检查结果：$INSPECTION"
fi

CONTAINER_NAME="$(trim_whitespace "${INSPECTION_LINES[0]}")"
REMOTE_JAR_PATH="$(trim_whitespace "${INSPECTION_LINES[1]}")"
CURRENT_REMOTE_HASH="$(trim_whitespace "${INSPECTION_LINES[2]}")"
CURRENT_STATUS="$(trim_whitespace "${INSPECTION_LINES[3]}")"

if [[ ! "$CONTAINER_NAME" =~ ^[a-zA-Z0-9_.-]+$ ]]; then
    die "识别到的容器名不安全：$CONTAINER_NAME"
fi
if [[ ! "$REMOTE_JAR_PATH" =~ ^/data/[a-zA-Z0-9_./-]+\.jar$ || "$REMOTE_JAR_PATH" == *".."* ]]; then
    die "识别到的远端 JAR 路径不安全：$REMOTE_JAR_PATH"
fi
if [[ ! "$CURRENT_REMOTE_HASH" =~ ^[a-f0-9]{64}$ ]]; then
    die "识别到的远端 SHA-256 无效：$CURRENT_REMOTE_HASH"
fi

REMOTE_APP_DIRECTORY="${REMOTE_JAR_PATH%/*}"

printf 'Compose：%s\n' "$COMPOSE_DIRECTORY"
printf '服务：%s\n' "$SERVICE_NAME"
printf '容器：%s\n' "$CONTAINER_NAME"
printf 'JAR：%s\n' "$REMOTE_JAR_PATH"
printf '当前 SHA-256：%s\n' "$CURRENT_REMOTE_HASH"
printf '当前状态：%s\n' "$CURRENT_STATUS"

if [[ "$MODE" == "check" ]]; then
    printf '\n'
    write_success "检查通过，未构建、上传或重启服务。"
    exit 0
fi

write_step "使用工程 Maven Wrapper 构建 Service Engine"
if ! (
    cd "$PROJECT_ROOT" &&
        "$MAVEN_WRAPPER" \
            -pl data-scalpel-service-engine \
            -am \
            package \
            -DskipTests
); then
    die "Service Engine Maven 构建失败"
fi

TARGET_DIRECTORY="$PROJECT_ROOT/data-scalpel-service-engine/target"
LOCAL_JAR=""
for candidate in "$TARGET_DIRECTORY"/data-scalpel-service-engine-*.jar; do
    [[ -f "$candidate" ]] || continue
    case "$candidate" in
        *-sources.jar | *-javadoc.jar) continue ;;
    esac
    if [[ -z "$LOCAL_JAR" || "$candidate" -nt "$LOCAL_JAR" ]]; then
        LOCAL_JAR="$candidate"
    fi
done
if [[ -z "$LOCAL_JAR" ]]; then
    die "构建完成后未找到 Service Engine 可执行 JAR"
fi

if ! LOCAL_HASH="$(local_sha256 "$LOCAL_JAR")"; then
    die "计算本地 JAR SHA-256 失败"
fi
if [[ ! "$LOCAL_HASH" =~ ^[a-f0-9]{64}$ ]]; then
    die "本地 JAR SHA-256 无效：$LOCAL_HASH"
fi

printf '本地 JAR：%s\n' "$LOCAL_JAR"
printf '本地 SHA-256：%s\n' "$LOCAL_HASH"
if [[ "$LOCAL_HASH" == "$CURRENT_REMOTE_HASH" ]]; then
    printf '\n'
    write_success "远端已是相同版本，无需更新。"
    exit 0
fi

BUILD_TAG="$(date '+%Y%m%d-%H%M%S')"
REMOTE_TEMPORARY_JAR="$REMOTE_JAR_PATH.upload-$BUILD_TAG"
REMOTE_BACKUP_DIRECTORY="$REMOTE_APP_DIRECTORY/backups"
REMOTE_BACKUP_JAR="$REMOTE_BACKUP_DIRECTORY/data-scalpel-service-engine-$BUILD_TAG-${CURRENT_REMOTE_HASH:0:12}.jar"
SWAPPED="false"

write_step "上传新 JAR 并校验 SHA-256"
if ! ssh_remote "set -eu
mkdir -p '$REMOTE_BACKUP_DIRECTORY'
test -w '$REMOTE_APP_DIRECTORY'
rm -f '$REMOTE_TEMPORARY_JAR'"; then
    handle_deployment_failure "准备远端上传目录失败"
fi
if ! scp \
    -o BatchMode=yes \
    -o ConnectTimeout=10 \
    "$LOCAL_JAR" \
    "$SSH_TARGET:$REMOTE_TEMPORARY_JAR"; then
    handle_deployment_failure "上传 Service Engine JAR 失败"
fi
if ! UPLOADED_HASH="$(ssh_remote "set -eu; sha256sum '$REMOTE_TEMPORARY_JAR' | cut -d ' ' -f 1")"; then
    handle_deployment_failure "读取上传文件校验值失败"
fi
UPLOADED_HASH="$(trim_whitespace "$UPLOADED_HASH")"
if [[ "$UPLOADED_HASH" != "$LOCAL_HASH" ]]; then
    handle_deployment_failure "上传文件 SHA-256 不一致，本地 $LOCAL_HASH，远端 $UPLOADED_HASH"
fi

write_step "备份旧版本并原子替换 JAR"
if ! ssh_remote "set -eu
cp -a '$REMOTE_JAR_PATH' '$REMOTE_BACKUP_JAR'
chown --reference='$REMOTE_JAR_PATH' '$REMOTE_TEMPORARY_JAR'
chmod --reference='$REMOTE_JAR_PATH' '$REMOTE_TEMPORARY_JAR'
if command -v chcon >/dev/null 2>&1; then
    chcon --reference='$REMOTE_JAR_PATH' '$REMOTE_TEMPORARY_JAR' || true
fi
mv -f '$REMOTE_TEMPORARY_JAR' '$REMOTE_JAR_PATH'"; then
    handle_deployment_failure "替换远端 Service Engine JAR 失败"
fi
SWAPPED="true"

write_step "重建 Service Engine 容器"
if ! ssh_remote "set -eu
cd '$COMPOSE_DIRECTORY'
docker compose up -d --force-recreate '$SERVICE_NAME'"; then
    handle_deployment_failure "Docker Compose 更新失败"
fi

write_step "等待 Service Engine 健康"
if ! wait_container_healthy "$CONTAINER_NAME" 180; then
    handle_deployment_failure "容器在 180s 内未变为 healthy"
fi

if ! DEPLOYED_HASH="$(ssh_remote "set -eu; sha256sum '$REMOTE_JAR_PATH' | cut -d ' ' -f 1")"; then
    handle_deployment_failure "读取已部署 JAR 校验值失败"
fi
DEPLOYED_HASH="$(trim_whitespace "$DEPLOYED_HASH")"
if [[ "$DEPLOYED_HASH" != "$LOCAL_HASH" ]]; then
    handle_deployment_failure "部署后 JAR SHA-256 不一致，期望 $LOCAL_HASH，实际 $DEPLOYED_HASH"
fi

cleanup_temporary_jar

printf '\n'
write_success "Service Engine 更新成功。"
printf '容器：%s\n' "$CONTAINER_NAME"
printf '新 SHA-256：%s\n' "$DEPLOYED_HASH"
printf '旧版本备份：%s\n' "$REMOTE_BACKUP_JAR"
