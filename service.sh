#!/usr/bin/env bash
#
# Cantonese lyrics API server 控制脚本
#
# 用法:
#   ./service.sh start           启动（首次自动建 .venv 并安装依赖）
#   ./service.sh stop            停止
#   ./service.sh restart         重启
#   ./service.sh status          查看运行状态
#   ./service.sh logs            实时跟踪日志（Ctrl-C 退出）
#   ./service.sh logs -n 200     只看最近 200 行（不跟踪）
#
set -euo pipefail

# ── 路径（相对脚本自身定位，任意目录调用均可） ──
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$SCRIPT_DIR/apiserver"
VENV="$APP_DIR/.venv"
UVICORN="$VENV/bin/uvicorn"
LOG_DIR="$APP_DIR/logs"
LOG_FILE="$LOG_DIR/uvicorn.log"
PID_FILE="$LOG_DIR/uvicorn.pid"
HOST="${HOST:-0.0.0.0}"
PORT="${PORT:-8000}"
MODULE="main:app"

# ── 工具函数 ──
is_running() {
  [[ -f "$PID_FILE" ]] || return 1
  local pid
  pid="$(cat "$PID_FILE" 2>/dev/null || true)"
  [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null
}

# 服务本身不自动读 .env，这里按 .env.example 的说明用 shell 注入
load_env() {
  if [[ -f "$APP_DIR/.env" ]]; then
    set -a
    # shellcheck disable=SC1091
    source "$APP_DIR/.env"
    set +a
  fi
}

# 首次启动：缺 .venv 或 uvicorn 时，建环境并装依赖
ensure_venv() {
  if [[ -x "$UVICORN" ]]; then
    return 0
  fi
  echo ">> 首次启动：创建虚拟环境并安装依赖…"
  if [[ ! -d "$VENV" ]]; then
    python3 -m venv "$VENV"
  fi
  "$VENV/bin/pip" install --upgrade pip >/dev/null
  "$VENV/bin/pip" install -r "$APP_DIR/requirements.txt"
}

do_start() {
  if is_running; then
    echo "已在运行 (PID $(cat "$PID_FILE"))"
    return 0
  fi
  mkdir -p "$LOG_DIR"
  ensure_venv
  load_env
  cd "$APP_DIR"
  echo ">> 启动 uvicorn ${MODULE} @ ${HOST}:${PORT}"
  nohup "$UVICORN" "$MODULE" --host "$HOST" --port "$PORT" >>"$LOG_FILE" 2>&1 &
  local pid=$!
  echo "$pid" > "$PID_FILE"
  disown "$pid" 2>/dev/null || true

  # 等待端口就绪（最多约 20s）
  local i
  for i in $(seq 1 40); do
    if ! is_running; then
      echo "!! 进程启动后立即退出，日志: ${LOG_FILE}"
      return 1
    fi
    if curl -sf -m 2 "http://127.0.0.1:${PORT}/health" >/dev/null 2>&1; then
      echo ">> 已启动 (PID ${pid})，日志: ${LOG_FILE}"
      return 0
    fi
    sleep 0.5
  done
  echo ">> 进程已启动 (PID ${pid})，/health 尚未就绪，日志: ${LOG_FILE}"
}

do_stop() {
  if ! is_running; then
    echo "未在运行"
    rm -f "$PID_FILE"
    return 0
  fi
  local pid
  pid="$(cat "$PID_FILE")"
  echo ">> 停止 (PID ${pid})…"
  kill "$pid" 2>/dev/null || true
  local i
  for i in $(seq 1 20); do
    kill -0 "$pid" 2>/dev/null || break
    sleep 0.3
  done
  if kill -0 "$pid" 2>/dev/null; then
    echo ">> 优雅退出超时，强制 kill -9"
    kill -9 "$pid" 2>/dev/null || true
  fi
  rm -f "$PID_FILE"
  echo ">> 已停止"
}

do_status() {
  if is_running; then
    echo "运行中 (PID $(cat "$PID_FILE")) @ ${HOST}:${PORT}"
  else
    echo "未运行"
  fi
}

do_logs() {
  if [[ ! -f "$LOG_FILE" ]]; then
    echo "还没有日志（先 ./service.sh start）"
    return 0
  fi
  if [[ "${1:-}" == "-n" ]]; then
    tail -n "${2:-100}" "$LOG_FILE"
  else
    tail -n 100 -f "$LOG_FILE"
  fi
}

case "${1:-}" in
  start)   do_start ;;
  stop)    do_stop ;;
  restart) do_stop; do_start ;;
  status)  do_status ;;
  logs)    shift; do_logs "$@" ;;
  *)
    echo "用法: $0 {start|stop|restart|status|logs [-n N]}"
    exit 1
    ;;
esac
