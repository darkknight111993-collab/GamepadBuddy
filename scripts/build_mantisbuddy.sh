#!/usr/bin/env bash
# Build mantisbuddy daemon cho Android (file 05 - Bước 2).
# Yêu cầu: Android NDK đã cài. Thiết lập ANDROID_NDK rồi chạy script.
#
#   export ANDROID_NDK=/path/to/android-ndk
#   ./scripts/build_mantisbuddy.sh
#
# Trên Windows: chạy bằng Git Bash hoặc WSL (script dùng bash). Script tự chọn
# thư mục prebuilt toolchain đúng theo hệ điều hành (windows / linux / darwin).
set -euo pipefail

NDK="${ANDROID_NDK:?Thiết lập ANDROID_NDK trỏ tới thư mục NDK}"

HOST="$(uname -s)"
case "$HOST" in
  Linux*)  PREBUILT="linux-x86_64" ;;
  Darwin*) PREBUILT="$(uname -m | grep -q arm && echo darwin-arm64 || echo darwin-x86_64)" ;;
  *)       PREBUILT="windows-x86_64" ;;
esac
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/$PREBUILT/bin"

SRC="$(dirname "$0")/../app/src/main/cpp/mantisbuddy.c"
OUT_DIR="$(dirname "$0")/../app/src/main/assets/daemon"
mkdir -p "$OUT_DIR"

echo ">> Building arm64-v8a ..."
"$TOOLCHAIN/aarch64-linux-android26-clang" -o "$OUT_DIR/mantisbuddy-arm64" "$SRC" -static

echo ">> Building armeabi-v7a ..."
"$TOOLCHAIN/armv7a-linux-androideabi26-clang" -o "$OUT_DIR/mantisbuddy-armv7" "$SRC" -static

echo ">> Building x86_64 ..."
"$TOOLCHAIN/x86_64-linux-android26-clang" -o "$OUT_DIR/mantisbuddy-x86_64" "$SRC" -static

echo ">> Building x86 ..."
"$TOOLCHAIN/i686-linux-android26-clang" -o "$OUT_DIR/mantisbuddy-x86" "$SRC" -static

echo "OK: $OUT_DIR/mantisbuddy-{arm64,armv7,x86_64,x86}"
