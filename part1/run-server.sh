#!/usr/bin/env bash
if ! command -v docker &> /dev/null; then
    echo "Öğrencilerden error mesajı: Docker kurulu değil!"
    exit 1
fi

if ! docker image inspect "${QUICHE_DOCKER_IMAGE:-quiche-proje}" &> /dev/null; then
    echo "Hata: quiche-proje image bulunamadı. Lütfen bize ulaşın, size docker imajını atalım."
    exit 1
fi

set -euo pipefail
IMAGE="${QUICHE_DOCKER_IMAGE:-quiche-proje}"

docker rm -f quiche-server 2>/dev/null || true

docker run -d --name quiche-server \
  -p 4433:4433/udp \
  -v "$(pwd)/bonus/src/main/resources/cert.crt:/certs/cert.crt" \
  -v "$(pwd)/bonus/src/main/resources/cert.key:/certs/cert.key" \
  "$IMAGE" \
  /quiche/target/debug/examples/server \
  --listen 0.0.0.0:4433 \
  --root /quiche \
  --cert /certs/cert.crt \
  --key /certs/cert.key \
  --no-retry

echo "Sunucu: UDP 4433 (container: quiche-server)"
docker ps --filter name=quiche-server
