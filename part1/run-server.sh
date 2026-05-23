#!/usr/bin/env bash
# Kısım I -- Docker quiche UDP sunucusu (tam QUIC/HTTP/3)
set -euo pipefail
IMAGE="${QUICHE_DOCKER_IMAGE:-quiche-proje}"

docker rm -f quiche-server 2>/dev/null || true
docker run -d --name quiche-server -w /quiche/quiche \
  -p 4433:4433/udp "$IMAGE" \
  /quiche/target/debug/examples/server

echo "Sunucu: UDP 4433 (container: quiche-server)"
docker ps --filter name=quiche-server
