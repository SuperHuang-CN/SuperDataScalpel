#!/usr/bin/env bash

# Safely stop only the five application processes managed by start-local-test69.sh.
# Kafka, Kong, DSH, and the standalone Compute Engine container are intentionally
# outside this script's lifecycle.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUNTIME_ROOT="${DATASCALPEL_TEST69_RUNTIME_ROOT:-/data/datascalpel-test69}"
PID_FILE="$RUNTIME_ROOT/runtime/start-local-test69.pid"
STOP_TIMEOUT_SECONDS=60
EXPECTED_PROFILE="test69"
EXPECTED_CONFIG="$ROOT_DIR/config/application-test69.yml"

if (( $# != 0 )); then
  echo "用法：$0" >&2
  exit 2
fi

for command_name in awk grep ip kill readlink rm sleep; do
  command -v "$command_name" >/dev/null 2>&1 || {
    echo "未找到停止 test69 所需命令：$command_name" >&2
    exit 1
  }
done

if ! ip -4 -o address show scope global 2>/dev/null | grep -qE '[[:space:]]10[.]0[.]0[.]69/'; then
  echo "stop-local-test69.sh 只能在 IP 为 10.0.0.69 的测试服务器运行。" >&2
  exit 1
fi

process_is_alive() {
  local pid="$1"
  local state

  [[ -d "/proc/$pid" ]] || return 1
  kill -0 "$pid" 2>/dev/null || return 1
  state="$(awk '{print $3}' "/proc/$pid/stat" 2>/dev/null || true)"
  [[ "$state" != "Z" ]]
}

process_has_argument() {
  local pid="$1"
  local expected="$2"

  [[ -r "/proc/$pid/cmdline" ]] || return 1
  grep -Fzxq -- "$expected" "/proc/$pid/cmdline" 2>/dev/null
}

process_command_contains() {
  local pid="$1"
  local expected="$2"

  [[ -r "/proc/$pid/cmdline" ]] || return 1
  grep -Fzq -- "$expected" "/proc/$pid/cmdline" 2>/dev/null
}

process_has_environment() {
  local pid="$1"
  local expected="$2"

  [[ -r "/proc/$pid/environ" ]] || return 1
  grep -Fzxq -- "$expected" "/proc/$pid/environ" 2>/dev/null
}

process_uses_test69_environment() {
  local pid="$1"

  process_has_environment "$pid" "DATASCALPEL_START_PROFILE=$EXPECTED_PROFILE" \
    && process_has_environment "$pid" "DATASCALPEL_START_CONFIG=$EXPECTED_CONFIG"
}

process_cwd_is_root() {
  local pid="$1"
  local cwd

  cwd="$(readlink -f "/proc/$pid/cwd" 2>/dev/null || true)"
  [[ "$cwd" == "$ROOT_DIR" ]]
}

process_executable_is() {
  local pid="$1"
  local expected="$2"
  local executable

  executable="$(readlink -f "/proc/$pid/exe" 2>/dev/null || true)"
  [[ "${executable##*/}" == "$expected" ]]
}

is_test69_supervisor() {
  local pid="$1"

  process_is_alive "$pid" || return 1
  process_executable_is "$pid" "bash" || return 1

  if process_has_argument "$pid" "$ROOT_DIR/start-local-dev.sh"; then
    process_uses_test69_environment "$pid"
    return
  fi

  if process_has_argument "$pid" "$ROOT_DIR/start-local-test69.sh"; then
    process_cwd_is_root "$pid"
    return
  fi

  if process_has_argument "$pid" "./start-local-test69.sh"; then
    process_cwd_is_root "$pid"
    return
  fi

  return 1
}

is_test69_child() {
  local pid="$1"

  process_is_alive "$pid" || return 1
  process_uses_test69_environment "$pid" || return 1

  if process_executable_is "$pid" "java" \
    && { process_has_argument "$pid" "cn.superhuang.data.scalpel.engine.DataScalpelServiceEngineApplication" \
      || process_has_argument "$pid" "cn.superhuang.datascalpel.taskengine.TaskEngineDaemon" \
      || process_has_argument "$pid" "cn.superhuang.data.scalpel.dispatcher.TaskDispatcherApplication" \
      || process_has_argument "$pid" "cn.superhuang.data.scalpel.admin.DataScalpelAdminApplication"; }; then
    process_command_contains "$pid" "$ROOT_DIR/"
    return
  fi

  if process_executable_is "$pid" "node" \
    && process_has_argument "$pid" "18887" \
    && process_command_contains "$pid" "$ROOT_DIR/data-scalpel-ui" \
    && { process_command_contains "$pid" "pnpm" || process_command_contains "$pid" "vite"; }; then
    return 0
  fi

  return 1
}

add_unique_pid() {
  local pid="$1"
  local existing

  for existing in "${DISCOVERED_PIDS[@]}"; do
    [[ "$existing" == "$pid" ]] && return
  done
  DISCOVERED_PIDS+=("$pid")
}

scan_supervisors() {
  local proc_dir
  local pid

  DISCOVERED_PIDS=()
  for proc_dir in /proc/[0-9]*; do
    pid="${proc_dir##*/}"
    [[ "$pid" == "$$" ]] && continue
    is_test69_supervisor "$pid" && add_unique_pid "$pid"
  done
  return 0
}

scan_children() {
  local proc_dir
  local pid

  DISCOVERED_PIDS=()
  for proc_dir in /proc/[0-9]*; do
    pid="${proc_dir##*/}"
    [[ "$pid" == "$$" ]] && continue
    is_test69_child "$pid" && add_unique_pid "$pid"
  done
  return 0
}

terminate_pids() {
  local label="$1"
  shift
  local pid

  (( $# > 0 )) || return
  echo "正在停止 $label：$*"
  for pid in "$@"; do
    kill -TERM "$pid" 2>/dev/null || true
  done
}

wait_for_pids() {
  local deadline="$1"
  shift
  local pids=("$@")
  local pid
  local remaining=()

  while (( SECONDS < deadline )); do
    remaining=()
    for pid in "${pids[@]}"; do
      process_is_alive "$pid" && remaining+=("$pid")
    done
    (( ${#remaining[@]} == 0 )) && return 0
    sleep 1
  done

  remaining=()
  for pid in "${pids[@]}"; do
    process_is_alive "$pid" && remaining+=("$pid")
  done
  if (( ${#remaining[@]} > 0 )); then
    echo "等待进程优雅退出超时，仍在运行：${remaining[*]}" >&2
    return 1
  fi
}

cleanup_pid_file() {
  [[ -e "$PID_FILE" ]] || return 0
  rm -f -- "$PID_FILE"
}

declare -a DISCOVERED_PIDS=()
declare -a SUPERVISOR_PIDS=()
declare -a CHILD_PIDS=()

if [[ -e "$PID_FILE" ]]; then
  if [[ ! -f "$PID_FILE" || ! -r "$PID_FILE" ]]; then
    echo "无法读取 test69 PID 文件：$PID_FILE" >&2
    exit 1
  fi

  pid_file_content="$(< "$PID_FILE")"
  recorded_pid=""
  if [[ "$pid_file_content" =~ ^[[:space:]]*([1-9][0-9]*)[[:space:]]*$ ]]; then
    recorded_pid="${BASH_REMATCH[1]}"
  fi
  if [[ -n "$recorded_pid" ]] && process_is_alive "$recorded_pid"; then
    if is_test69_supervisor "$recorded_pid"; then
      SUPERVISOR_PIDS+=("$recorded_pid")
    else
      echo "忽略指向非 test69 启动进程的陈旧 PID 文件：$recorded_pid" >&2
    fi
  elif [[ -z "$recorded_pid" && -n "$pid_file_content" ]]; then
    echo "忽略内容无效的陈旧 PID 文件：$PID_FILE" >&2
  fi
fi

scan_supervisors
for pid in "${DISCOVERED_PIDS[@]}"; do
  add_to_supervisors=true
  for existing in "${SUPERVISOR_PIDS[@]}"; do
    if [[ "$existing" == "$pid" ]]; then
      add_to_supervisors=false
      break
    fi
  done
  [[ "$add_to_supervisors" == true ]] && SUPERVISOR_PIDS+=("$pid")
done

scan_children
CHILD_PIDS=("${DISCOVERED_PIDS[@]}")

if (( ${#SUPERVISOR_PIDS[@]} == 0 && ${#CHILD_PIDS[@]} == 0 )); then
  cleanup_pid_file
  echo "DataScalpel test69 五个应用未启动，无需停止。"
  exit 0
fi

deadline=$((SECONDS + STOP_TIMEOUT_SECONDS))
if (( ${#SUPERVISOR_PIDS[@]} > 0 )); then
  terminate_pids "test69 启动父进程" "${SUPERVISOR_PIDS[@]}"
  wait_for_pids "$deadline" "${SUPERVISOR_PIDS[@]}" || exit 1
fi

# A normally exiting supervisor removes its children through start-local-dev.sh's
# cleanup trap. Scan again only to handle a parent that had previously disappeared
# abnormally and left exact, test69-marked application processes behind.
scan_children
CHILD_PIDS=("${DISCOVERED_PIDS[@]}")
if (( ${#CHILD_PIDS[@]} > 0 )); then
  terminate_pids "残留的 test69 应用进程" "${CHILD_PIDS[@]}"
  wait_for_pids "$deadline" "${CHILD_PIDS[@]}" || exit 1
fi

scan_supervisors
SUPERVISOR_PIDS=("${DISCOVERED_PIDS[@]}")
scan_children
CHILD_PIDS=("${DISCOVERED_PIDS[@]}")
if (( ${#SUPERVISOR_PIDS[@]} > 0 || ${#CHILD_PIDS[@]} > 0 )); then
  echo "test69 仍有无法确认退出的应用进程，请人工检查。" >&2
  exit 1
fi

cleanup_pid_file
echo "DataScalpel test69 五个应用已停止；Kafka、Kong、DSH 和 Compute Engine 保持运行。"
