#!/usr/bin/env bash
# File 04 Hướng B: deploy & chạy daemon thủ công từ máy tính (không tự động hoá trong app).
# Thiết bị phải đã được `adb connect` (USB hoặc Wireless Debugging) tới máy tính.
#
#   ./scripts/adb_deploy.sh
set -euo pipefail

ABI="$(adb shell getprop ro.product.cpu.abi | tr -d '\r')"
echo "Device ABI: $ABI"

case "$ABI" in
  arm64-v8a)  SRC=mantisbuddy-arm64 ;;
  armeabi-v7a) SRC=mantisbuddy-armv7 ;;
  *) echo "ABI chưa hỗ trợ: $ABI"; exit 1 ;;
esac

echo ">> Push $SRC -> /data/local/tmp/mantisbuddy"
adb push "$SRC" /data/local/tmp/mantisbuddy
adb shell chmod 755 /data/local/tmp/mantisbuddy

echo ">> Start daemon (background, giữ quyền shell)"
adb shell "/data/local/tmp/mantisbuddy &"

echo "Done. Kiểm tra device ảo mới:"
echo "  adb shell getevent -i | grep -i mantisbuddy"
