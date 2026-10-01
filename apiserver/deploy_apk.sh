#!/usr/bin/env bash
# 构建 Android APK 并部署到 apiserver 下载目录（data/apk/）。
#
# 用法:
#   ./deploy_apk.sh [更新说明]             # 构建 + 部署
#   ./deploy_apk.sh --no-build [更新说明]  # 只部署（用已有构建产物）
#
# 部署后手机浏览器访问 http://<局域网IP>:8000/apk 下载；
# App 内升级接口查 GET /api/apk/latest 比较 versionCode。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ANDROID_DIR="$ROOT/android"
APK_DIR="$SCRIPT_DIR/data/apk"
APK_NAME="cantonese-lyrics.apk"
JAVA_HOME="${JAVA_HOME:-/home/loomz/jdk/jdk-17.0.12}"

NO_BUILD=0
CHANGELOG=""
for arg in "$@"; do
    case "$arg" in
        --no-build) NO_BUILD=1 ;;
        *) CHANGELOG="$arg" ;;
    esac
done

if [[ "$NO_BUILD" -eq 0 ]]; then
    echo "==> 构建 APK..."
    (cd "$ANDROID_DIR" && JAVA_HOME="$JAVA_HOME" ./gradlew assembleDebug)
fi

SRC="$ANDROID_DIR/app/build/outputs/apk/debug/app-debug.apk"
if [[ ! -f "$SRC" ]]; then
    echo "错误: 找不到构建产物: $SRC" >&2
    echo "（如已构建过，请用 --no-build）" >&2
    exit 1
fi

mkdir -p "$APK_DIR"
cp "$SRC" "$APK_DIR/$APK_NAME"

# versionCode / versionName 从 build.gradle.kts 读取
VERSION_CODE=$(grep -oE 'versionCode = [0-9]+' "$ANDROID_DIR/app/build.gradle.kts" | grep -oE '[0-9]+')
VERSION_NAME=$(sed -nE 's/.*versionName = "([^"]+)".*/\1/p' "$ANDROID_DIR/app/build.gradle.kts")
SIZE=$(stat -c%s "$APK_DIR/$APK_NAME")
SHA256=$(sha256sum "$APK_DIR/$APK_NAME" | awk '{print $1}')
UPDATED_AT=$(( $(date +%s%N) / 1000000 ))

python3 - "$APK_DIR/latest.json" "$VERSION_CODE" "$VERSION_NAME" "$SIZE" "$SHA256" "$UPDATED_AT" "$CHANGELOG" <<'PY'
import json, sys
path, code, name, size, sha, ts, changelog = sys.argv[1:8]
with open(path, "w", encoding="utf-8") as f:
    json.dump(
        {
            "versionCode": int(code),
            "versionName": name,
            "size": int(size),
            "sha256": sha,
            "updatedAt": int(ts),
            "changelog": changelog,
            "url": "/api/apk/download",
        },
        f,
        ensure_ascii=False,
        indent=2,
    )
    f.write("\n")
PY

echo "==> 已部署: $APK_DIR/$APK_NAME (versionCode=$VERSION_CODE, versionName=$VERSION_NAME)"
echo "==> 手机下载页:   http://<局域网IP>:8000/apk"
echo "==> 直接下载链接: http://<局域网IP>:8000/api/apk/download"
