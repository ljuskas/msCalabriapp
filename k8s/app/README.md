# Despliegue de msCalabriapp

Manifiestos versionables del Deployment y del Service ClusterIP de la
aplicación. El namespace `clabriapp` **ya existe** y no se crea aquí. El Secret
`clabriapp-postgresql` también existe fuera de Git y no se versiona.

> **No aplicado en la tarea 23.3.** Estos manifiestos están preparados para que
> el workflow de despliegue los aplique después de un push a `main`, en el
> runner self-hosted dedicado. En este encargo no se tocó el clúster.

## Marcador de imagen

`deployment.yaml` usa:

```text
ghcr.io/ljuskas/mscalabriapp:sha-__GITHUB_SHA__
```

El workflow copia los YAML de `k8s/app/` a un directorio temporal, sustituye
`__GITHUB_SHA__` por el SHA completo del commit y aplica esa copia. El
manifiesto versionado no contiene una etiqueta mutable como `latest`. Si el
marcador no se sustituye, el workflow falla antes de aplicar.

## Aplicación manual equivalente

Solo para revisión o recuperación operada por una persona con permisos. No se
ejecutó en este encargo:

```bash
export GITHUB_SHA="<sha completo de 40 caracteres>"
tmp="$(mktemp -d)"
mkdir -p "${tmp}/app"
cp k8s/app/deployment.yaml k8s/app/service.yaml k8s/app/kustomization.yaml "${tmp}/app/"
sed -i "s/__GITHUB_SHA__/${GITHUB_SHA}/g" "${tmp}/app/deployment.yaml"

kubectl apply -k "${tmp}/app"
kubectl -n clabriapp rollout status deployment/mscalabriapp --timeout=180s
rm -rf "${tmp}"
```

Antes de aplicar conviene renderizar y revisar:

```bash
kubectl kustomize "${tmp}/app"
```

## Decisiones

- **Imagen:** GHCR, etiqueta inmutable `sha-<commit>`. El paquete debe ser
  público porque el pod no usa `imagePullSecrets`.
- **Service:** `ClusterIP`, puerto `80` hacia el puerto `http` (8080) del pod.
- **Secret:** se mapean explícitamente las cinco claves del Secret existente a
  `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME` y `DB_PASSWORD`. No se usa
  `envFrom` porque las claves no coinciden con esos nombres. No se crea ni se
  lee ningún valor del Secret desde el workflow.
- **Probes:** `/health/started` (verificado en Quarkus 3.40.2),
  `/health/live` y `/health/ready`. Readiness incluye el datasource; sin
  PostgreSQL el pod queda `NotReady`, que es el comportamiento esperado.
- **Security context:** usuario no-root `10001`, `allowPrivilegeEscalation:
  false`, `readOnlyRootFilesystem: true`, `capabilities.drop: [ALL]`,
  `seccompProfile: RuntimeDefault` y `automountServiceAccountToken: false`.
- **/tmp:** `emptyDir` en memoria de 64 MiB porque el rootfs es de solo lectura.
- **Recursos:** requests `100m`/`256Mi` y limits `500m`/`512Mi`.

## Prerrequisito de visibilidad de GHCR

La visibilidad de un paquete de GHCR es independiente de la del repositorio. La
primera publicación puede crear el paquete como privado aunque el repositorio
sea público. Antes del primer despliegue hay que cambiarlo a público:

```text
GitHub > Packages > mscalabriapp > Package settings > Change visibility > Public
```

El workflow hace un preflight anónimo antes de tocar el clúster. Si el paquete
sigue privado, el job de despliegue termina con un mensaje que indica cambiar
la visibilidad y reintentar; no aplica nada.

## RBAC del runner

El runner no usa permisos de administrador. El bootstrap está en
[`k8s/bootstrap/`](../bootstrap/) y no se aplica desde este directorio ni desde
el workflow. Su Role no concede `get` sobre Secrets.
