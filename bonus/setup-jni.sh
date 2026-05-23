#!/usr/bin/env bash
# Builds libquiche_jni for macOS (one-time). Requires: brew install rust cmake
set -euo pipefail

if ! command -v cargo >/dev/null; then
  echo "Rust yok. Kur: brew install rust"
  exit 1
fi
if ! command -v cmake >/dev/null; then
  echo "CMake yok. Kur: brew install cmake"
  exit 1
fi

BSSL_CMAKE="${HOME}/.cargo/registry/src/index.crates.io-"*/quiche-0.5.1/deps/boringssl/CMakeLists.txt
for f in ${BSSL_CMAKE}; do
  if [[ -f "$f" ]] && grep -q 'VERSION 3.0' "$f" 2>/dev/null; then
    sed -i '' 's/cmake_minimum_required(VERSION 3.0)/cmake_minimum_required(VERSION 3.5)/' "$f"
    echo "Patched $f for CMake 4.x"
  fi
done

if [[ ! -d /tmp/quiche4j ]]; then
  git clone --depth 1 https://github.com/kachayev/quiche4j.git /tmp/quiche4j
fi

export CARGO_TARGET_DIR=/tmp/quiche4j-native/build
mkdir -p "$CARGO_TARGET_DIR"

cd /tmp/quiche4j
cargo build --release --manifest-path quiche4j-jni/Cargo.toml

mkdir -p /tmp/quiche4j-native/release
cp "$CARGO_TARGET_DIR/release/libquiche_jni.dylib" /tmp/quiche4j-native/release/

echo ""
echo "Tamam. Şunu export et:"
echo "  export QUICHE_JNI_LIB=/tmp/quiche4j-native/release"
ls -la /tmp/quiche4j-native/release/libquiche_jni.dylib
