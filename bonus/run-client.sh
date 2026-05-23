#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
URL="${1:-https://127.0.0.1:4433/}"
SSLKEYLOGFILE="${SSLKEYLOGFILE:-$ROOT/logs/sslkeys.log}"
mkdir -p "$(dirname "$SSLKEYLOGFILE")"

mvn -q -f "$ROOT/pom.xml" -pl bonus package -DskipTests

if [[ -z "${QUICHE_JNI_LIB:-}" ]]; then
  if [[ -f /tmp/quiche4j-native/release/libquiche_jni.dylib ]]; then
    export QUICHE_JNI_LIB=/tmp/quiche4j-native/release
  else
    echo "QUICHE_JNI_LIB tanımlı değil. Önce: ./bonus/setup-jni.sh"
    exit 1
  fi
fi

export SSLKEYLOGFILE
exec java -Djava.library.path="$QUICHE_JNI_LIB" \
  -cp "$ROOT/bonus/target/bonus-1.0-SNAPSHOT.jar" \
  io.quiche4j.examples.Http3Client "$URL"
