# Acceso al PostgreSQL compartido del host

Estos manifiestos publican, dentro del clúster LocalEmu `clabriapp`, un `Service`
estable para llegar al PostgreSQL **ya existente** en el host. No despliegan un
servidor PostgreSQL en Kubernetes ni copian datos.

## Decisión de arquitectura (23.5)

- Se reutiliza el contenedor PostgreSQL del host que ya usa Heraap.
- Se reutiliza la base de datos existente `heraap`.
- El aislamiento se hace con el schema `clabriapp` y el rol exclusivo
  `clabriapp_app`, no con otra base ni otro servidor.
- No se migran datos de login, credenciales ni sesiones.
- El rol `clabriapp_app` recibe `CONNECT` en `heraap` y `USAGE`/`CREATE` en
  `clabriapp`; no recibe privilegios sobre tablas ni secuencias de `public`.
  No se modifican los schemas `public` ni `heraap`, ni sus objetos.
- La contraseña y el resto de credenciales viven en el Secret de Kubernetes
  `clabriapp-postgresql`, creado fuera de Git. **Este repositorio no contiene
  ningún Secret ni valor real.**

## Ruta de conexión

```text
pod (namespace clabriapp)
  -> Service clabriapp-postgresql:5432 (ClusterIP, sin selector)
  -> Endpoints clabriapp-postgresql (IP manual)
  -> 172.28.0.1:5432 (gateway de la red Docker k3d)
  -> puerto 5432 publicado por el contenedor PostgreSQL del host
  -> base heraap, schema clabriapp, rol clabriapp_app
```

El Service no usa selector porque su destino no es un pod de Kubernetes: es el
gateway de la red Docker del clúster. Por eso el `Endpoints` se declara a mano y
debe llevar el mismo nombre que el Service.

## Advertencia sobre la IP del gateway

`172.28.0.1` es el gateway de la red Docker `k3d-localemu-eks-clabriapp`
(subred `172.28.0.0/16`). **Si se elimina y recrea el clúster o la red k3d, el
gateway puede cambiar** y el `Endpoints` dejará de funcionar.

Comprobación del gateway actual (solo metadatos de red):

```bash
docker network inspect k3d-localemu-eks-clabriapp \
  --format '{{range .IPAM.Config}}subnet={{.Subnet}} gateway={{.Gateway}}{{println}}{{end}}'
```

Si cambia, actualizar `endpoints.yaml` con el nuevo gateway y volver a aplicar.
Comprobar también que el contenedor PostgreSQL siga publicando `5432` en el
host. No se debe asumir que esta IP es portable a otro host, a EKS real ni a
otra red.

## Aplicación

Con `kubectl` apuntando al clúster LocalEmu:

```bash
kubectl apply -k k8s/postgresql-shared/
```

`kustomization.yaml` incluye solo el Service y el Endpoints; el smoke pod no se
aplica por accidente. También se pueden aplicar los dos manifiestos por
separado:

```bash
kubectl apply -f k8s/postgresql-shared/service.yaml
kubectl apply -f k8s/postgresql-shared/endpoints.yaml
```

No usar `kubectl apply -f k8s/postgresql-shared/` sobre el directorio completo:
intentaría interpretar `kustomization.yaml`, el README y el smoke pod.

El Secret **no** se aplica desde este directorio. Debe existir previamente en el
namespace `clabriapp`, con las claves:

| Clave | Contenido |
| --- | --- |
| `username` | `clabriapp_app` |
| `password` | contraseña aleatoria, fuera de Git |
| `database` | `heraap` |
| `host` | `clabriapp-postgresql.clabriapp.svc.cluster.local` (DNS del Service) |
| `port` | `5432` |

La creación del Secret se hace por un canal que no deja la contraseña en
argumentos, historial, logs ni archivos versionados. No se incluye ningún
ejemplo con valor real.

## Verificación

La prueba de aceptación de 23.5 se ejecuta desde un pod efímero que referencia el
Secret y ejecuta únicamente:

```sql
SELECT current_database(), current_user;
```

No se consultan tablas, filas ni datos de login. El manifiesto está en
`smoke-pod.yaml`. La imagen `postgres:17.4` debe estar en el containerd del
clúster; si no está, se puede importar desde la imagen local de Docker sin
hacer `pull` de red:

```bash
docker save postgres:17.4 \
  | docker exec -i k3d-localemu-eks-clabriapp-server-0 ctr -n k8s.io images import -
```

Ejecución y limpieza:

```bash
kubectl apply -f k8s/postgresql-shared/smoke-pod.yaml
# esperar a que la fase sea Succeeded
kubectl -n clabriapp logs clabriapp-postgres-smoke
kubectl -n clabriapp delete pod clabriapp-postgres-smoke
```

Resultado verificado: `heraap|clabriapp_app`, fase `Succeeded` y pod eliminado.
Para comprobar el estado sin leer el contenido del Secret:

```bash
kubectl -n clabriapp get service clabriapp-postgresql
kubectl -n clabriapp get endpoints clabriapp-postgresql
kubectl -n clabriapp get secret clabriapp-postgresql   # solo metadatos
```

## Límites

- Diseñado y verificado solo para el laboratorio LocalEmu de este host.
- No hay TLS ni migración de datos; el login de Heraap sigue intacto.
- No se toca `proyHerapp_v2/infra/compose.yml` ni los demás contenedores o
  volúmenes.
- No se usa AWS real.
- `Endpoints` es el recurso solicitado para 23.5; si en el futuro se migra a
  `EndpointSlice`, mantener el mismo nombre de Service y revisar la selección.
