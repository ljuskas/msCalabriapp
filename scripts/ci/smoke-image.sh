#!/usr/bin/env bash
# Smoke de la imagen JVM sin PostgreSQL.
#
# Valida únicamente /health/live y /api/v1/service-info. No exige
# /health/ready: el datasource está activo y sin base de datos ese check
# responde DOWN, lo cual es esperado en este smoke.
set -euo pipefail

image="${1:?Uso: smoke-image.sh <imagen>}"
port="${SMOKE_PORT:-18080}"
container="mscalabriapp-smoke-${GITHUB_RUN_ID:-local}-${RANDOM}"

cleanup() {
  docker rm --force "${container}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

for cmd in docker curl; do
  if ! command -v "${cmd}" >/dev/null 2>&1; then
    echo "Falta ${cmd} para ejecutar el smoke de la imagen." >&2
    exit 1
  fi
done

# Reproduce las restricciones del pod: usuario no-root, rootfs de solo lectura,
# /tmp escribible en tmpfs, sin capabilities y sin privilege escalation.
docker run --detach \
  --name "${container}" \
  --publish "127.0.0.1:${port}:8080" \
  --read-only \
  --tmpfs /tmp:rw,noexec,nosuid,size=64m \
  --user 10001:10001 \
  --cap-drop ALL \
  --security-opt no-new-privileges \
  "${image}" >/dev/null

ready=0
for _ in $(seq 1 60); do
  if curl --fail --silent --show-error "http://127.0.0.1:${port}/health/live" >/dev/null 2>&1; then
    ready=1
    break
  fi
  running="$(docker inspect --format '{{.State.Running}}' "${container}" 2>/dev/null || echo false)"
  if [ "${running}" != "true" ]; then
    echo "El contenedor terminó antes de que /health/live estuviera disponible." >&2
    docker logs "${container}" >&2 || true
    exit 1
  fi
  sleep 1
done

if [ "${ready}" -ne 1 ]; then
  echo "Timeout esperando /health/live en http://127.0.0.1:${port}." >&2
  docker logs "${container}" >&2 || true
  exit 1
fi

live="$(curl --fail --silent --show-error "http://127.0.0.1:${port}/health/live")"
info="$(curl --fail --silent --show-error "http://127.0.0.1:${port}/api/v1/service-info")"

if ! printf '%s' "${live}" | grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"'; then
  echo "Respuesta inesperada de /health/live: ${live}" >&2
  exit 1
fi

if ! printf '%s' "${info}" | grep -Eq '"name"[[:space:]]*:[[:space:]]*"msCalabriapp"'; then
  echo "Respuesta inesperada de /api/v1/service-info: ${info}" >&2
  exit 1
fi

if ! printf '%s' "${info}" | grep -Eq '"displayName"[[:space:]]*:[[:space:]]*"ClabriApp"'; then
  echo "Respuesta inesperada de /api/v1/service-info: ${info}" >&2
  exit 1
fi

echo "Smoke OK: /health/live UP y /api/v1/service-info responde para ${image}."
