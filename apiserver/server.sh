#!/usr/bin/env bash
# 管理 apiserver（uvicorn）进程：start / restart / stop / status / logs。
#
# 用法:
#   ./server.sh start      # 后台启动（已在跑则提示）
#   ./server.sh stop       # 停止（等待进程退出，必要时 SIGKILL）
#   ./server.sh restart    # stop + start
#   ./server.sh status     # 查看运行状态（含 PID / 端口探活）
#   ./server.sh logs [n]   # tail 日志（默认 50 行，-f 跟随请用 ./server.sh logs 100 然后 Ctrl+C 前它是静态的）
#
# 进程管理：PID 文件 logs/uvicorn.pid；日志 logs/uvicorn.log（追加）。
# 服务: http://0.0.0.0:8000 （health: GET /health）
set -u

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

PYTHON=".venv/bin/python"
LOG="logs/uvicorn.log"
PID_FILE="logs/uvicorn.pid"
HOST="${HOST:-0.0.0.0}"
PORT="${PORT:-8000}"

mkdir -p logs

# ── 内部工具 ──────────────────────────────────────────────────────

# 取 pid 文件里的 PID；文件缺失/为空/内容非数字输出空。
read_pid() {
    if [[ -f "$PID_FILE" ]]; then
        local p
        p="$(cat "$PID_FILE" 2>/dev/null | tr -d '[:space:]')"
        if [[ "$p" =~ ^[0-9]+$ ]]; then
            echo "$p"
        fi
    fi
}

# PID 是否为本脚本能管理的 uvicorn 主进程（存在且 cmdline 含 uvicorn）。
is_running() {
    local p
    p="$(read_pid)"
    [[ -n "$p" ]] && kill -0 "$p" 2>/dev/null \
        && tr '\0' ' ' < "/proc/$p/cmdline" 2>/dev/null | grep -q uvicorn
}

wait_gone() {
    local p="$1" i
    for i in $(seq 1 50); do   # 最多 ~5s
        if ! kill -0 "$p" 2>/dev/null; then
            return 0
        fi
        sleep 0.1
    done
    return 1
}

# ── 子命令 ────────────────────────────────────────────────────────

do_start() {
    if is_running; then
        echo "==> 已在运行 (PID $(read_pid))，如需重启请: $0 restart"
        exit 0
    fi
    # 残留 PID 文件清理
    if [[ -n "$(read_pid)" ]]; then
        rm -f "$PID_FILE"
    fi
    if [[ ! -x "$PYTHON" ]]; then
        echo "错误: 找不到 $PYTHON（请先创建 venv 并 pip install -r requirements.txt）" >&2
        exit 1
    fi
    echo "==> 启动 apiserver: http://$HOST:$PORT"
    nohup "$PYTHON" -m uvicorn main:app --host "$HOST" --port "$PORT" >> "$LOG" 2>&1 &
    local p=$!
    echo "$p" > "$PID_FILE"

    # 探活：最多等 10s，/health 返回 ok 才算成功
    local i
    for i in $(seq 1 20); do
        if curl -sf -m 2 "http://127.0.0.1:$PORT/health" >/dev/null 2>&1; then
            echo "==> 已启动 (PID $p)，健康检查通过"
            return 0
        fi
        if ! kill -0 "$p" 2>/dev/null; then
            echo "错误: 进程启动后立即退出，最近日志：" >&2
            tail -20 "$LOG" >&2
            rm -f "$PID_FILE"
            exit 1
        fi
        sleep 0.5
    done
    echo "警告: 进程在跑 (PID $p) 但 /health 10s 内未就绪，请查日志: $0 logs" >&2
}

do_stop() {
    local p
    p="$(read_pid)"
    if [[ -z "$p" ]] || ! kill -0 "$p" 2>/dev/null; then
        echo "==> 未在运行"
        rm -f "$PID_FILE"
        return 0
    fi
    echo "==> 停止 (PID $p)..."
    kill "$p" 2>/dev/null
    if ! wait_gone "$p"; then
        echo "==> 5s 未退出，强制 SIGKILL"
        kill -9 "$p" 2>/dev/null
        wait_gone "$p" || true
    fi
    rm -f "$PID_FILE"
    echo "==> 已停止"
}

do_status() {
    if is_running; then
        local p
        p="$(read_pid)"
        echo "==> 运行中 (PID $p, 端口 $PORT)"
        if curl -sf -m 2 "http://127.0.0.1:$PORT/health" >/dev/null 2>&1; then
            echo "==> 健康检查: OK (GET /health)"
        else
            echo "==> 健康检查: 失败（进程在但 /health 无响应），请查日志: $0 logs"
        fi
    else
        echo "==> 未运行"
        exit 3   # 约定: 3 = not running（方便脚本化判断）
    fi
}

do_logs() {
    local n="${1:-50}"
    tail -n "$n" "$LOG"
}

case "${1:-}" in
    start)   do_start ;;
    stop)    do_stop ;;
    restart) do_stop; do_start ;;
    status)  do_status ;;
    logs)    shift; do_logs "$@" ;;
    *)
        echo "用法: $0 {start|stop|restart|status|logs [行数]}"
        exit 1
        ;;
esac
