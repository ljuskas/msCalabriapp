#!/usr/bin/env bash
# Preflight anónimo de una imagen de GHCR.
#
# Comprueba que el paquete se puede descargar sin credenciales antes de tocar el
# clúster. La visibilidad de un paquete GHCR es independiente de la del
# repositorio: un paquete nuevo puede ser privado aunque el repo sea público.
set -euo pipefail

repo="${1:?Uso: preflight-ghcr.sh <owner/repo> <tag>}"
tag="${2:?Uso: preflight-ghcr.sh <owner/repo> <tag>}"

if ! command -v curl >/dev/null 2>&1; then
  echo "::error::Falta curl para el preflight anónimo de GHCR."
  exit 1
fi

token_url="https://ghcr.io/token"
token_json="$(curl --fail --silent --show-error \
  --retry 3 --retry-delay 2 \
  --get \
  --data-urlencode "service=ghcr.io" \
  --data-urlencode "scope=repository:${repo}:pull" \
  "${token_url}")" || {
  echo "::error::No se pudo obtener un token anónimo de GHCR para ${repo}. Revisa la conectividad del runner."
  exit 1
}

token="$(printf '%s' "${token_json}" |
  sed -n 's/.*"token"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')"
if [ -z "${token}" ]; then
  echo "::error::GHCR no devolvió un token anónimo para ${repo}."
  exit 1
fi

manifest_url="https://ghcr.io/v2/${repo}/manifests/${tag}"
accept="application/vnd.oci.image.index.v1+json,application/vnd.docker.distribution.manifest.list.v2+json,application/vnd.oci.image.manifest.v1+json,application/vnd.docker.distribution.manifest.v2+json"

for attempt in 1 2 3 4 5 6; do
  status="$(curl --silent --show-error --output /dev/null --write-out '%{http_code}' \
    --retry 2 --retry-delay 2 \
    --header "Authorization: Bearer ${token}" \
    --header "Accept: ${accept}" \
    "${manifest_url}")" || status="000"

  case "${status}" in
    200)
      echo "Preflight OK: ${repo}:${tag} se puede descargar de forma anónima."
      exit 0
      ;;
    401|403)
      echo "::error::El paquete GHCR ${repo} no es público (HTTP ${status}). Cambia la visibilidad del paquete a pública y vuelve a ejecutar el despliegue. No se aplicó nada al clúster."
      echo "Ruta: GitHub > Packages > ${repo##*/} > Package settings > Change visibility > Public."
      exit 1
      ;;
    404)
      echo "Intento ${attempt}/6: ${repo}:${tag} todavía no aparece en GHCR (HTTP 404). Reintentando..."
      sleep 5
      ;;
    *)
      echo "::error::El preflight anónimo de GHCR falló con HTTP ${status} para ${repo}:${tag}. No se aplicó nada al clúster."
      exit 1
      ;;
  esac
done

echo "::error::La imagen ${repo}:${tag} no está disponible de forma anónima tras esperar. Verifica la publicación, la etiqueta sha y que el paquete sea público. No se aplicó nada al clúster."
exit 1
