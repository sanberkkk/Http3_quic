#!/usr/bin/env bash
# Kısım I -- multithread Java istemci (her olay Docker http3-client)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

mvn -q -f "$ROOT/pom.xml" -pl part3,part1 package -DskipTests

export QUICHE_DOCKER_IMAGE="${QUICHE_DOCKER_IMAGE:-quiche-proje}"
export QUICHE_TARGET_URL="${QUICHE_TARGET_URL:-https://host.docker.internal:4433/}"

cd "$ROOT"
exec java -jar part1/target/part1-1.0-SNAPSHOT.jar
