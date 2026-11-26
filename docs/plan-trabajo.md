# Plan de trabajo — 23.3 CI/CD, imagen JVM y despliegue LocalEmu

**Rama:** `feature/23-3-ci-cd-localemu`, basada en `main` con el PR #3 (23.5) integrado.
**Fecha de verificación:** 2026-11-25
**Alcance:** subtarea 23.3: CI en runners GitHub-hosted, publicación en GHCR con
`GITHUB_TOKEN`, despliegue solo tras push a `main` en un runner self-hosted dedicado,
imagen JVM Java 17, manifiestos de Deployment/Service y bootstrap RBAC del runner. La base
23.5 ya integrada se conserva documentada en la Parte II.
**Estado:** implementación versionable completa, aprobada y recogida en un commit local con el
asunto `ci(deploy): añade CI en GitHub-hosted y despliegue seguro en LocalEmu`. No hubo push,
PR ni merge; no se registró el runner, no se publicó ninguna imagen y no se aplicó nada al
clúster (tampoco el bootstrap RBAC). No se incluye SHA porque el coordinador hará `amend` del
mismo commit.
**Mantenimiento:** este plan debe actualizarse en cada commit futuro de la rama.

## Parte I — Tarea 23.3 (entrega actual)

### A. Decisiones y límites del encargo

El usuario aprobó:

- un paquete **GHCR que debe ser público** para `msCalabriapp`;
- un **runner nuevo dedicado** en este servidor para el despliegue;
- la rama `feature/23-3-ci-cd-localemu`, basada en `main` con el PR #3 ya integrado.

El encargo es solo la parte **versionable**. No se registró el runner, no se creó ni cambió
ningún recurso de GitHub, no se publicó ninguna imagen, no se aplicó nada al clúster y no
se leyeron valores del Secret `clabriapp-postgresql`. Tampoco se tocó
`proyHerapp_v2/status.md` ni ningún archivo fuera de `msCalabriapp`.

El texto antiguo de `proyHerapp_v2/status.md` que proponía un *namespace* de preview por PR
y despliegue desde el PR queda **reemplazado** por este diseño:

- **PR hacia `main`:** solo build, pruebas e imagen/smoke en runners GitHub-hosted. Nunca
  self-hosted, nunca despliegue, nunca secretos.
- **Push/merge a `main`:** publicación de la imagen desde un runner GitHub-hosted y, después,
  despliegue en el runner self-hosted dedicado.
- **Sin previews por PR.** No se comparte el entorno estable ni se crean namespaces
  efímeros. La decisión anterior queda sin efecto para este repositorio.

### B. CI en pull requests y push a `main`

Workflow: [`.github/workflows/ci.yml`](../.github/workflows/ci.yml).

- Se dispara con `pull_request` hacia `main` y con `push` a `main`.
- Todos los jobs usan `ubuntu-latest` (GitHub-hosted). El workflow **no contiene ningún job
  self-hosted**, no usa `pull_request_target` y no recibe secretos.
- Pasos: `./mvnw -B -ntp verify`, `docker build` de la imagen JVM y smoke sin PostgreSQL.
- Permisos: `contents: read`.
- Concurrencia de CI con `cancel-in-progress: true` (no hay despliegues en este workflow).
- Un PR de fork se queda en build/test sobre runner GitHub-hosted. No hay ningún camino
  desde `pull_request` al runner dedicado ni al clúster.

### C. Publicación en GHCR

Workflow: [`.github/workflows/publish-deploy.yml`](../.github/workflows/publish-deploy.yml),
job `publish`.

- Se dispara **solo** con `push` a `main`; no hay `workflow_dispatch`.
- Corre en `ubuntu-latest` (GitHub-hosted).
- Permisos del job: `contents: read` y `packages: write`.
- La política de Actions del repositorio debe permitir que el `GITHUB_TOKEN` solicite
  `packages: write`; no se cambió en este encargo.
- Autenticación con `${{ secrets.GITHUB_TOKEN }}`; **no se usa ningún PAT**.
- Imagen: `ghcr.io/ljuskas/mscalabriapp:sha-${GITHUB_SHA}`.
- Solo se publica la etiqueta inmutable `sha-<commit>`; no se publica `latest` ni otra
  etiqueta mutable.
- Antes de publicar repite `./mvnw -B -ntp verify`, construye la imagen y ejecuta el mismo
  smoke sin PostgreSQL. La imagen que se publica es la que se probó en ese job.

Acciones de terceros fijadas a SHA oficiales comprobados con `git ls-remote` contra los
repositorios oficiales:

| Acción | Versión | SHA fijado |
| --- | --- | --- |
| `actions/checkout` | v4.4.0 | `11d5960a326750d5838078e36cf38b85af677262` |
| `actions/setup-java` | v4.9.1 | `cf277c60eb25467037889841efdb72551f06f6c3` |

### D. Runner self-hosted y despliegue

Job `deploy` del mismo workflow.

- Runner: `runs-on: [self-hosted, clabriapp-local-main]`, con la etiqueta exacta
  `clabriapp-local-main`.
- Condiciones explícitas:
  - `github.repository == 'ljuskas/msCalabriapp'`;
  - `github.event_name == 'push'`;
  - `github.ref == 'refs/heads/main'`.
- Concurrencia del workflow: `cancel-in-progress: false`. Un push nuevo espera y no cancela
  un despliegue en curso. Timeout del job: 15 minutos.
- Usa `kubectl` y el kubeconfig preinstalado en
  `/etc/mscalabriapp-runner/kubeconfig`. **No usa Docker ni el socket de Docker.**
- Antes de tocar el clúster comprueba que existen `kubectl` y `curl`, que el kubeconfig es
  legible y que el SHA tiene 40 caracteres hexadecimales.
- **Preflight anónimo de GHCR** con
  [`scripts/ci/preflight-ghcr.sh`](../scripts/ci/preflight-ghcr.sh): pide un token anónimo,
  consulta el manifiesto de `sha-${GITHUB_SHA}` y exige `HTTP 200`. Se hace antes de
  cualquier comando `kubectl` que hable con el clúster.
- Si el paquete es privado (`401/403`) o la imagen no aparece (`404`), el job termina con
  un mensaje claro que indica cambiar la visibilidad del paquete a pública y reintentar. No
  se aplica nada al clúster.
- Solo después del preflight comprueba el acceso al namespace existente `clabriapp`, copia
  los YAML de `k8s/app/` a un directorio temporal, sustituye `__GITHUB_SHA__` por el SHA
  real, aplica con `kubectl apply -k`, verifica la imagen del Deployment y espera el rollout.
  También falla si `kubectl auth can-i get secrets -n clabriapp` responde `yes`: el
  kubeconfig del runner debe corresponder a la ServiceAccount sin acceso a Secrets.

#### Prerrequisito del runner

El runner **no está registrado** en este encargo. Para habilitarlo hace falta:

- etiqueta exacta `clabriapp-local-main`;
- `kubectl` y `curl` instalados;
- `KUBECONFIG=/etc/mscalabriapp-runner/kubeconfig` presente y legible por el usuario del
  runner;
- bootstrap RBAC de [`k8s/bootstrap/`](../k8s/bootstrap/) aplicado por el operador;
- salida a `ghcr.io` para el preflight anónimo;
- **sin Docker ni socket de Docker**.

#### Prerrequisito de visibilidad de GHCR

La visibilidad de un paquete de GHCR es independiente de la del repositorio. Un paquete
nuevo puede nacer privado aunque el repositorio sea público. Antes del primer despliegue el
operador debe cambiar la visibilidad:

```text
GitHub > Packages > mscalabriapp > Package settings > Change visibility > Public
```

El primer run de publicación puede terminar en el preflight si el paquete sigue privado;
hay que cambiar la visibilidad y volver a ejecutar el workflow desde Actions. En este
encargo no se publicó la imagen ni se cambió la visibilidad.

### E. Imagen JVM y smoke

- [`Dockerfile`](../Dockerfile): imagen de runtime basada en
  `eclipse-temurin:17-jre-jammy`. No compila dentro de la imagen: copia
  `target/quarkus-app/` generado por Maven.
- Escucha en `0.0.0.0:8080` de forma explícita.
- Usuario no-root `10001:10001`.
- Sin Maven, fuentes, `.git` ni cachés.
- [`.dockerignore`](../.dockerignore) excluye todo salvo `Dockerfile` y
  `target/quarkus-app/`.
- [`scripts/ci/smoke-image.sh`](../scripts/ci/smoke-image.sh) arranca el contenedor con
  rootfs de solo lectura, `/tmp` en tmpfs, `--cap-drop ALL`, sin privilege escalation y
  usuario `10001:10001`. Comprueba **solo** `/health/live` y `/api/v1/service-info`.
  **No exige `/health/ready`**, porque el datasource está activo y sin PostgreSQL ese check
  responde `DOWN`.

### F. Manifiestos de Kubernetes y RBAC

- [`k8s/app/deployment.yaml`](../k8s/app/deployment.yaml): Deployment en el namespace
  existente `clabriapp`; no se crea `Namespace`.
- [`k8s/app/service.yaml`](../k8s/app/service.yaml): Service `ClusterIP`, puerto 80 hacia
  el puerto `http` (8080).
- [`k8s/app/kustomization.yaml`](../k8s/app/kustomization.yaml): solo Deployment y Service.
- Imagen con el marcador `ghcr.io/ljuskas/mscalabriapp:sha-__GITHUB_SHA__`; el workflow lo
  sustituye por `GITHUB_SHA` en una copia temporal.
- Las cinco claves del Secret se mapean explícitamente con `secretKeyRef`:
  `host → DB_HOST`, `port → DB_PORT`, `database → DB_NAME`, `username → DB_USERNAME`,
  `password → DB_PASSWORD`. No se usa `envFrom`, no se crea otro Secret y no se versiona
  ningún valor.
- Probes: `/health/started` (comprobado en local: SmallRye Health de Quarkus 3.40.2 lo
  expone y respondió `200`), `/health/live` y `/health/ready`.
- `securityContext`: no-root, `allowPrivilegeEscalation: false`,
  `readOnlyRootFilesystem: true`, `capabilities.drop: [ALL]`, `seccompProfile:
  RuntimeDefault`, `automountServiceAccountToken: false`; `/tmp` con `emptyDir` en memoria.
- Recursos: requests `100m`/`256Mi`; limits `500m`/`512Mi`.
- [`k8s/bootstrap/deployer-rbac.yaml`](../k8s/bootstrap/deployer-rbac.yaml): ServiceAccount
  `clabriapp-deployer`, Role y RoleBinding namespace-scoped para Deployment/Service y
  lectura de pods/ReplicaSets. **No hay ninguna regla sobre Secrets**: el runner no tiene
  `get` sobre Secrets. No se aplicó.
- El bootstrap no se aplica desde el workflow de despliegue ni desde este encargo.

### G. Documentación y reemplazo del texto anterior

Se actualizan `README.md`, `CONTRIBUTING.md`, `.github/pull_request_template.md` y este
plan con:

- CI de PR en GitHub-hosted y despliegue solo después de `main`;
- runner, etiqueta y prerrequisitos de kubeconfig/`kubectl`/`curl`;
- GHCR, `GITHUB_TOKEN`, etiqueta `sha` y paso inicial de visibilidad;
- permisos mínimos y RBAC sin acceso a Secrets;
- comandos de verificación y riesgos.

`proyHerapp_v2/status.md` **no se editó**. Su texto anterior de previews/deploy desde PR
queda reemplazado por el diseño de esta rama: PR solo CI en GitHub-hosted; deploy solo
después del push/merge a `main`.

### H. Verificaciones ejecutadas y no ejecutadas

Ejecutadas en el entorno del worker:

- `./mvnw -B -ntp verify`: **BUILD SUCCESS**, 9 pruebas, 0 fallos, 0 errores, 0 omitidas.
- `docker build --build-arg GIT_SHA="$(git rev-parse HEAD)" -t mscalabriapp:local .`:
  **correcto**; imagen con `User=10001:10001`, puerto `8080` y entrypoint JVM.
- `./scripts/ci/smoke-image.sh mscalabriapp:local`: **correcto**; `/health/live` `UP` y
  `/api/v1/service-info` con `msCalabriapp`/`ClabriApp`, sin PostgreSQL.
- Parseo de YAML de workflows y manifiestos con PyYAML: **correcto**.
- Comprobación estructural de workflows: CI solo `ubuntu-latest`; deploy solo con etiqueta
  `clabriapp-local-main` y condiciones de repositorio, `push` y `refs/heads/main`.
- `git status --short --branch`: rama `feature/23-3-ci-cd-localemu`; solo aparecen los
  archivos esperados de 23.3.
- `git diff --check`: sin errores de espacios ni conflictos.
- Revisión completa del diff y de los archivos nuevos. Escaneo de patrones de secretos
  (claves privadas, PAT, tokens de GitHub/AWS/Slack, asignaciones de password/token): solo
  aparecen nombres de claves, variables o placeholders; **ningún valor real**. No se
  imprimieron coincidencias sensibles.
- Sin archivos modificados fuera de `msCalabriapp` por este encargo.

No disponibles en el worker (no se instalaron herramientas de sistema):

- `actionlint`: **no disponible**.
- `kubeconform`: **no disponible**.
- `kubectl`: **no disponible** en el host.

No ejecutadas, por límite explícito del encargo:

- workflows en GitHub;
- registro del runner;
- publicación o descarga autenticada de la imagen;
- aplicación de manifiestos o bootstrap RBAC al clúster;
- lectura de valores del Secret.

### I. Riesgos y límites

1. **Primer despliegue bloqueado por visibilidad.** El paquete GHCR puede nacer privado.
   El preflight fallará y no aplicará nada; hay que cambiar la visibilidad a pública y
   reintentar. No se debe interpretar ese fallo como un fallo de la aplicación.
2. **Runner no registrado.** El workflow de despliegue no puede ejecutarse hasta que exista
   un runner con la etiqueta exacta y el kubeconfig en la ruta prevista.
3. **Runner self-hosted y repositorio público.** Un runner self-hosted no debe quedar
   expuesto a PR de forks. El diseño separa CI y deploy y no usa self-hosted en
   `pull_request`, pero la configuración de GitHub debe exigir aprobación para workflows de
   forks y limitar quién puede modificar workflows.
4. **Kubeconfig y RBAC.** El kubeconfig es un secreto operativo fuera de Git; debe tener
   permisos restringidos y rotación. El Role no concede Secrets, pero el operador debe
   verificar con `kubectl auth can-i get secrets -n clabriapp` que el runner no los lee.
5. **Etiqueta `sha` inmutable por convención.** Nadie debe sobrescribir
   `sha-${GITHUB_SHA}`. No hay firma de imagen, SBOM ni verificación por digest en este
   alcance; quedan para seguridad/observabilidad.
6. **Dependencia de PostgreSQL compartido.** `/health/ready` depende del Service
   `clabriapp-postgresql` y del gateway k3d. Si cambia la red o el gateway, el pod no estará
   listo. Sin TLS y con el puerto 5432 expuesto, son límites ya documentados en 23.5.
7. **Acciones fijadas a SHA.** Hay que revisar y actualizar los SHA periódicamente; no se
   usan tags flotantes en los workflows.
8. **Sin `workflow_dispatch`.** Los reintentos se hacen re-ejecutando el run desde Actions,
   no con un disparador manual del workflow.
9. **Read-only rootfs y `/tmp`.** El pod y el smoke usan rootfs de solo lectura con `/tmp`
   en tmpfs. Si una futura versión de Quarkus necesita escribir en otra ruta, habrá que
   añadir un volumen explícito, no relajar el rootfs.
10. **Sin despliegue real en este encargo.** No se afirma que CI esté configurado en GitHub,
    que el runner esté instalado, que el paquete sea público ni que el despliegue haya
    ocurrido.
11. **Imagen base por etiqueta.** `eclipse-temurin:17-jre-jammy` se referencia por tag, no
    por digest. Para una cadena de suministro más estricta habría que fijar el digest y
    añadir firma/procedencia; no forma parte de este alcance.

### J. Mensaje del commit local creado

El commit local fue aprobado y creado con este asunto:

```text
ci(deploy): añade CI en GitHub-hosted y despliegue seguro en LocalEmu

Añade workflows de CI para pull_request y push a main con verify, imagen JVM y smoke sin base de datos. Publica en GHCR con GITHUB_TOKEN y etiqueta sha, y despliega solo tras push a main en un runner self-hosted dedicado con preflight anónimo de imagen. Incluye Deployment, Service y bootstrap RBAC sin acceso a Secrets; no registra el runner, no publica imágenes ni aplica recursos.
```

No se incluye SHA porque el coordinador hará `amend` del mismo commit. No hubo push, PR ni
merge; no se registró el runner, no se publicó imagen ni se aplicó nada al clúster.

## Parte II — Base integrada 23.5

## 1. Decisión aprobada

No se despliega otro servidor PostgreSQL en EKS ni se copian o migran datos de login. Se
reutiliza el contenedor PostgreSQL existente del host, que ya usa Heraap, y la base de datos
existente `heraap`.

El aislamiento se hace **en la misma base** con:

- schema `clabriapp`;
- rol exclusivo `clabriapp_app`, con acceso mínimo a ese schema;
- Secret de Kubernetes `clabriapp-postgresql`, creado fuera de Git.

No se leen ni modifican las tablas, filas, credenciales o sesiones de login. No se toca
`proyHerapp_v2/infra/compose.yml` ni los demás contenedores o volúmenes. No se usa AWS real.

## 2. Arquitectura y ruta de conexión

```text
pod en namespace clabriapp
  -> Service clabriapp-postgresql:5432 (ClusterIP, sin selector)
  -> Endpoints clabriapp-postgresql (IP manual)
  -> 172.28.0.1:5432 (gateway de la red Docker k3d-localemu-eks-clabriapp)
  -> puerto 5432 publicado por el contenedor PostgreSQL del host
  -> base heraap, schema clabriapp, rol clabriapp_app
```

El Service no tiene selector porque el destino no es un pod, sino el gateway de la red
Docker del clúster. El `Endpoints` se declara a mano y lleva el mismo nombre que el
Service.

### Advertencia de red

`172.28.0.1` es el gateway de la red `k3d-localemu-eks-clabriapp` (`172.28.0.0/16`). Si se
elimina y recrea el clúster o la red k3d, el gateway puede cambiar y el Endpoints dejará de
funcionar. Hay que revisar:

```bash
docker network inspect k3d-localemu-eks-clabriapp \
  --format '{{range .IPAM.Config}}subnet={{.Subnet}} gateway={{.Gateway}}{{println}}{{end}}'
```

y actualizar `k8s/postgresql-shared/endpoints.yaml` si cambia. No se debe asumir que esa IP
es portable a otro host, a EKS real ni a otra red.

## 3. Objetos y manifiestos

| Objeto | Estado | Detalle |
| --- | --- | --- |
| Base `heraap` | Ya existía | No se recreó ni se migró |
| Schema `clabriapp` | Creado | En `heraap`; propietario `clabriapp_app` |
| Rol `clabriapp_app` | Creado | `LOGIN`, sin atributos administrativos, sin memberships |
| Secret `clabriapp-postgresql` | Creado fuera de Git | `Opaque`; claves `username`, `password`, `database`, `host`, `port` |
| Service `clabriapp-postgresql` | Creado | `ClusterIP`, puerto `5432`, sin selector |
| Endpoints `clabriapp-postgresql` | Creado | `172.28.0.1:5432` |
| Pod `clabriapp-postgres-smoke` | Creado y eliminado | Solo ejecutó `SELECT current_database(), current_user;` |
| Datasource Quarkus | Configurado | Driver JDBC PostgreSQL + Agroal; sin ORM, repositorios ni migraciones |

Manifiestos versionables:

- `k8s/postgresql-shared/service.yaml`
- `k8s/postgresql-shared/endpoints.yaml`
- `k8s/postgresql-shared/kustomization.yaml`
- `k8s/postgresql-shared/smoke-pod.yaml` (pod efímero de verificación)
- `k8s/postgresql-shared/README.md`

**No hay ningún Secret versionado.** La contraseña no se imprimió, no se pasó como
argumento de proceso, no se escribió en archivos del repo ni en task files. El Secret se
creó por `stdin`.

### 3.1 Dependencias y configuración del datasource

La segunda fase de 23.5 añade **solo** la integración JDBC. Las versiones se heredan del
`quarkus-bom` 3.40.2, sin versión explícita en el POM:

| Dependencia | Versión | Origen |
| --- | --- | --- |
| `io.quarkus:quarkus-jdbc-postgresql` | 3.40.2 | `quarkus-bom` |
| `io.quarkus:quarkus-agroal` | 3.40.2 | Transitiva del driver JDBC |
| `org.postgresql:postgresql` | 42.7.14 | Transitiva, gestionada por el BOM |

No se añadieron Hibernate ORM, Panache, Flyway ni Liquibase. Tampoco se creó código Java de
persistencia: el único check nuevo es el de datasource que Agroal registra automáticamente
con SmallRye Health.

Configuración aplicada en `src/main/resources/application.properties`:

```properties
quarkus.datasource.db-kind=postgresql
quarkus.datasource.username=${DB_USERNAME:}
quarkus.datasource.password=${DB_PASSWORD:}
quarkus.datasource.jdbc.url=jdbc:postgresql://${DB_HOST:127.0.0.1}:${DB_PORT:5432}/${DB_NAME:heraap}?currentSchema=clabriapp
quarkus.datasource.health.enabled=true
quarkus.devservices.enabled=false
quarkus.datasource.devservices.enabled=false
%test.quarkus.datasource.active=false
```

- `currentSchema=clabriapp` fija el schema por defecto. El rol `clabriapp_app` ya tiene
  `search_path=clabriapp, pg_catalog`; la aplicación no modifica roles ni schemas.
- `quarkus.devservices.enabled=false` y `quarkus.datasource.devservices.enabled=false`
  desactivan Dev Services en todos los perfiles. La aplicación nunca crea un contenedor
  PostgreSQL adicional.
- `%test.quarkus.datasource.active=false` desactiva el datasource solo en `test`: no hay
  conexión, no se requiere base y el check no se registra. Las pruebas unitarias y
  `@QuarkusTest` siguen siendo autocontenidas.
- En el perfil real, `/health/ready` incluye `Database connections health check`; el check
  abre una conexión de validación y no ejecuta consultas sobre tablas ni filas.

Mapeo de las claves del Secret a las variables de entorno. `envFrom` no renombra claves, por
lo que el Deployment versionado en `k8s/app/deployment.yaml` declara cada variable con
`secretKeyRef`:

| Clave del Secret | Variable | Destino |
| --- | --- | --- |
| `host` | `DB_HOST` | host del JDBC URL |
| `port` | `DB_PORT` | puerto del JDBC URL |
| `database` | `DB_NAME` | base del JDBC URL |
| `username` | `DB_USERNAME` | `quarkus.datasource.username` |
| `password` | `DB_PASSWORD` | `quarkus.datasource.password` |

En el clúster, `DB_HOST` debe ser el valor del Secret (`host`), que apunta al Service
`clabriapp-postgresql`. Para el smoke local se usa `DB_HOST=127.0.0.1`, porque el puerto
`5432` del host está publicado. La app **no está desplegada**: el Deployment, la imagen y los
workflows están versionados en 23.3, pero no se aplicaron al clúster en este encargo.

## 4. Persistencia

El contenedor PostgreSQL existente usa un bind mount del host:

```text
/DATA/AppData/postgresql/data -> /var/lib/postgresql/data (rw)
```

Eso es la persistencia ya existente del servidor compartido; el schema `clabriapp` vive en
esa misma base. **No se reinició el contenedor PostgreSQL** porque el alcance prohíbe tocar
los contenedores y volúmenes existentes. Por tanto, la persistencia se verificó por
metadatos del montaje y por la existencia del schema en `heraap`, no con una prueba de
reinicio. No se creó ni migró ningún dato de negocio.

Se comprobó además que el schema no quedó en la base equivocada:

- `schema_heraap=1`
- `schema_postgres=0`

## 5. Privilegios y aislamiento

Verificación por metadatos, sin leer filas ni tablas de negocio:

| Comprobación | Resultado |
| --- | --- |
| `rolcanlogin` | `true` |
| `rolsuper` | `false` |
| `rolcreatedb` | `false` |
| `rolcreaterole` | `false` |
| `rolreplication` | `false` |
| `rolbypassrls` | `false` |
| Memberships del rol | `0` |
| `CONNECT` en `heraap` | `true` |
| `CREATE` en `heraap` | `false` |
| `TEMP` en `heraap` | `true` (heredado de `PUBLIC`; solo tablas temporales de sesión) |
| `USAGE` en `clabriapp` | `true` |
| `CREATE` en `clabriapp` | `true` |
| `CREATE` en `public` | `false` |
| Privilegios sobre tablas de `public` | `SELECT=0`, `INSERT=0`, `UPDATE=0`, `DELETE=0` |
| Privilegios sobre secuencias de `public` | `0` |
| Objetos propios fuera de `clabriapp` | `0` |
| `search_path` del rol en `heraap` | `clabriapp, pg_catalog` |

### Limitación de PostgreSQL sobre `public`

`clabriapp_app` conserva `USAGE` sobre el schema `public` porque PostgreSQL lo concede a
`PUBLIC` por defecto. Tampoco se puede revocar `TEMP` sin afectar a `PUBLIC`; `TEMP` solo
permite crear tablas temporales de la propia sesión. No se revocó porque hacerlo desde
`PUBLIC` afectaría a los demás roles de Heraap y podría romper el login; el alcance prohíbe
modificar los objetos y permisos existentes. La `USAGE` no concede acceso a datos: el rol
tiene **0 privilegios sobre las tablas y secuencias de `public`**, y su `search_path`
apunta a `clabriapp`.

Si en el futuro se exige eliminar también la `USAGE` heredada, la alternativa sería una
base de datos separada o revisar los privilegios de `PUBLIC` con una ventana de migración
aprobada; no forma parte de 23.5.

## 6. Verificaciones ejecutadas

### 6.1 Metadatos previos

- La base `heraap` existía.
- El schema `clabriapp` no existía.
- El rol `clabriapp_app` no existía.
- El Secret `clabriapp-postgresql` no existía en el namespace `clabriapp`.
- El rol administrador local es superusuario y puede crear roles y schemas.
- El puerto `5432` ya escuchaba en el host y el gateway de k3d era `172.28.0.1`.

No hubo conflicto ni se sobrescribió ningún objeto existente.

### 6.2 Creación segura

- Contraseña aleatoria de 64 caracteres hexadecimales generada en memoria con `openssl`.
- Rol y schema creados en una transacción; el schema se creó finalmente en `heraap`.
- Incidencia corregida: la primera creación del schema se ejecutó contra la base
  `postgres` por un error de destino. Se detectó antes de crear objetos, se comprobó que el
  schema estaba vacío, se eliminó y se recreó en `heraap`. El rol y la contraseña no
  cambiaron y no se tocó ningún dato de negocio.
- Secret creado con `kubectl create -f -` por `stdin`, sin `last-applied-configuration`.
- Si la creación del Secret hubiera fallado, el rol y el schema se revertían; no fue
  necesario.

### 6.3 Service, Endpoints y conexión

- `kubectl apply` del Service: `service/clabriapp-postgresql created`.
- `kubectl apply` del Endpoints: `endpoints/clabriapp-postgresql created`.
- Endpoints observado: `172.28.0.1:5432`.
- Pod efímero con la referencia al Secret y la imagen `postgres:17.4` (importada al
  containerd de k3d desde la imagen local, sin `pull` de red; el manifiesto usa
  `imagePullPolicy: Never`).
- Resultado del `psql`:

```text
heraap|clabriapp_app
```

- El pod terminó en fase `Succeeded` y se eliminó con
  `kubectl delete pod clabriapp-postgres-smoke`.
- No se consultó ninguna tabla, fila, credencial o sesión de login.

### 6.4 Estado final de metadatos

- Service `clabriapp-postgresql`: `ClusterIP`, puerto `5432`, selector `<none>`.
- Endpoints `clabriapp-postgresql`: `172.28.0.1:5432`.
- Secret `clabriapp-postgresql`: `Opaque`, 5 claves; no se leyó su contenido.
- No quedó ningún pod de prueba en el namespace.

### 6.5 Pruebas y empaquetado JVM

- `./mvnw test`: **BUILD SUCCESS**; 9 pruebas ejecutadas, 0 fallos, 0 errores, 0 omitidas.
  El perfil `test` activó `%test.quarkus.datasource.active=false`: no se conectó a
  PostgreSQL, no se creó ningún contenedor y el health check de datasource no se registró.
- `./mvnw package -DskipTests`: **BUILD SUCCESS**; se generó
  `target/quarkus-app/quarkus-run.jar`. El paquete incluye `quarkus-jdbc-postgresql`
  3.40.2, `quarkus-agroal` 3.40.2, `org.postgresql:postgresql` 42.7.14 y
  `io.agroal:agroal-*` 3.2.1, todas gestionadas por el BOM o transitivas.

### 6.6 Smoke test local del datasource

- Proceso empaquetado: `java -Dquarkus.http.port=18080 -jar target/quarkus-app/quarkus-run.jar`.
- Entorno: `DB_HOST=127.0.0.1`, `DB_PORT=5432`, `DB_NAME=heraap`; `DB_USERNAME` y
  `DB_PASSWORD` se leyeron del Secret `clabriapp-postgresql` en memoria, sin imprimirlos ni
  escribirlos a disco. El log temporal no contenía la contraseña.
- Se esperó a `/health/ready`; respondió `HTTP 200` con:

```json
{
  "status": "UP",
  "checks": [
    {
      "name": "Database connections health check",
      "status": "UP",
      "data": {
        "<default>": "UP"
      }
    }
  ]
}
```

- El check de datasource respondió `UP` y abrió una conexión de validación. **No se
  consultaron tablas ni filas, no se escribió en la base y no se ejecutó SQL de negocio.**
- No se creó ningún pod, contenedor ni Dev Service: antes y después del smoke había los
  mismos 28 contenedores. No se modificaron roles, schema, Secret, Service, Endpoints ni
  contenedores.
- El proceso Quarkus se detuvo al terminar; el puerto `18080` quedó libre y no quedó ningún
  proceso `quarkus-run.jar`.

### 6.7 Revisión final del árbol

- `git status --short --branch`: rama `feature/23-5-postgresql-shared`; el alcance de 23.5
  (`README.md`, `pom.xml`, `application.properties`, `docs/` y `k8s/`) queda en un único
  commit local, sin cambios fuera de `msCalabriapp`.
- `git diff --check`: sin errores de espacios ni conflictos.
- `git diff` revisado completo, incluidos los archivos nuevos: solo la dependencia JDBC, la
  configuración del datasource, los manifiestos de Service/Endpoints/smoke pod y la
  documentación. No hay código de dominio, ORM ni migraciones.
- Búsqueda literal de la contraseña real del Secret en el árbol de trabajo: no encontrada.
  No aparecen `stringData` con valores, `password=` con valor real, claves privadas ni
  `.env`. Los logs del smoke fueron temporales y no contenían la contraseña.
- El único commit local de la entrega usa el mensaje aprobado. No se hizo push, PR, merge ni
  rebase.

## 7. Límites y riesgos

1. **Sin migración de datos.** No se copiaron tablas ni filas de login. El login de Heraap
   sigue intacto y no se modificó su esquema.
2. **Sin TLS.** El datasource JDBC no configura TLS ni verificación de certificado; el smoke
   pod de la primera fase usó `PGSSLMODE=disable`. No es una configuración de producción;
   TLS y `verify-full` quedan para una tarea de seguridad.
3. **IP del gateway frágil.** Si se recrea la red k3d, `172.28.0.1` puede cambiar. El
   Endpoints es específico de este host y de este laboratorio.
4. **Puerto 5432 expuesto.** El contenedor existente publica `5432` en todas las interfaces
   del host. Es una condición previa que no se modificó en 23.5; debe restringirse en una
   tarea de seguridad.
5. **Sin ORM, repositorios ni migraciones.** El microservicio ya tiene driver JDBC y
   datasource validado, pero no tiene ORM, adaptadores/repositorios ni migraciones. La
   persistencia de negocio sigue en 23.9. La aplicación **no está desplegada**: la imagen y
   los manifiestos de 23.3 están versionados, pero no se publicaron ni se aplicaron; el
   clúster de estas pruebas es LocalEmu/k3s.
6. **Persistencia sin prueba de reinicio.** No se reinició el contenedor compartido por el
   límite explícito de no tocar contenedores/volúmenes. La persistencia se respalda en el
   bind mount existente.
7. **`Endpoints` es un recurso legacy.** Se usó el recurso solicitado. Si se migra a
   `EndpointSlice`, mantener el mismo nombre de Service y revisar la selección.
8. **`USAGE` heredada sobre `public`.** Ver la limitación de la sección 5; no permite leer ni
   escribir tablas de login.
9. **Imagen de prueba.** `postgres:17.4` se importó al clúster desde la imagen local de
   Docker; no se hizo `pull` de red. La imagen queda en el containerd del laboratorio.
10. **Secret estándar de Kubernetes.** Es un `Opaque` (base64, no cifrado en reposo). El
    acceso depende del RBAC del clúster. El bootstrap de 23.3 no concede `get` sobre Secrets
    al runner de despliegue; la aplicación consume el Secret vía kubelet con
    `secretKeyRef`. Revisar el cifrado en reposo si el entorno lo exige.
11. **Perfil `test` sin datasource.** Las pruebas unitarias no validan la conexión real
    porque el datasource está inactivo. La conexión se validó en el smoke local; cuando se
    despliegue la app, el readiness del clúster deberá confirmar el mismo check. No se debe
    interpretar el `UP` de `./mvnw test` como una prueba de red al PostgreSQL.

## 8. Estado de 23.5 y aceptación

La decisión del usuario sustituye dos aceptaciones originales de la tarea 23:

- “PostgreSQL en el clúster” pasa a “PostgreSQL existente del host accesible desde el
  clúster mediante Service/Endpoint”.
- “Migración de datos desde el PostgreSQL actual” pasa a “sin migración; login intacto”.

| Criterio del alcance aprobado | Evidencia | Estado |
| --- | --- | --- |
| Reutilizar el PostgreSQL y la base existentes | Contenedor `postgresql` y base `heraap` verificados | Cumple |
| Schema y rol aislados | `clabriapp` con propietario `clabriapp_app` | Cumple |
| Rol con privilegios mínimos | 0 privilegios sobre tablas/secuencias de `public` | Cumple con la limitación de `USAGE` heredada |
| Secret fuera de Git | `Opaque` con 5 claves; no versionado | Cumple |
| Service/Endpoint hacia `172.28.0.1:5432` | Objetos aplicados y Endpoints observado | Cumple |
| Conexión desde un pod con el Secret | `heraap` / `clabriapp_app` | Cumple |
| Solo `SELECT current_database(), current_user` | Comando del smoke test | Cumple |
| Limpieza del pod | Pod eliminado; no quedan pods de prueba | Cumple |
| Sin migración de datos ni cambios al login | Solo se creó schema/rol nuevos; `public` sin cambios | Cumple |
| Persistencia | Bind mount `/DATA/AppData/postgresql/data`; sin reinicio por límite | Cumple con la limitación documentada |
| Manifiestos sin secretos | Revisión de `k8s/` y `git status` | Cumple |
| Documentación | README y este plan | Cumple |
| Pruebas locales | `./mvnw test`: 9/9 en verde | Cumple |
| Driver JDBC PostgreSQL del BOM | `quarkus-jdbc-postgresql` sin versión explícita; BOM 3.40.2 | Cumple |
| Datasource por variables `DB_*` | `application.properties` y smoke local | Cumple |
| Schema por defecto `clabriapp` | `currentSchema=clabriapp`; `search_path` del rol | Cumple |
| Dev Services desactivados | `quarkus.devservices.enabled=false` y `quarkus.datasource.devservices.enabled=false` | Cumple |
| Tests sin base de datos | `%test.quarkus.datasource.active=false`; `./mvnw test` 9/9 | Cumple |
| Health check real del datasource | `/health/ready` 200; `Database connections health check` `UP` | Cumple |
| Sin endpoint que lea datos | Sin código Java nuevo; solo health check | Cumple |
| Sin ORM, repositorios ni migraciones | `pom.xml` sin Hibernate/Flyway/Liquibase; sin código de persistencia | Cumple |

**Conclusión:** 23.5 queda completa en el alcance aprobado, incluida la aceptación que
faltaba: la aplicación Quarkus tiene driver JDBC, datasource configurado por variables de
entorno y health check real validado localmente. La aceptación original de “PostgreSQL
dentro del clúster” sigue sustituida por “PostgreSQL existente del host accesible desde el
clúster”, y la de “migración de datos” por “sin migración; login intacto”, según la decisión
del usuario. ORM, repositorios/adaptadores y migraciones quedan para 23.9. La aplicación
**no está desplegada en EKS**; el smoke es local y la conectividad de red desde el namespace
se verificó con el pod de la primera fase. El coordinador debe reflejar esta actualización
en el `status.md` central.

## 9. Tareas dependientes

- **23.3 (CI/CD y Deployment):** implementado en esta rama como parte versionable. El
  Secret no se genera en el workflow ni queda en logs; el kubeconfig del runner se
  provisiona fuera de Git. El Deployment inyecta las cinco variables `DB_*` mediante
  `secretKeyRef` (las claves del Secret no coinciden con los nombres de las variables). El
  bootstrap RBAC es namespace-scoped y **no concede `get` sobre Secrets**; no hace falta un
  `ClusterRole`. No hay previews por PR: PR solo CI en GitHub-hosted y deploy solo después
  del push a `main`. **La app no se ha desplegado en este encargo.**
- **23.6 (andamiaje Quarkus):** no requiere la base todavía; puede avanzar en paralelo.
- **23.9 (persistencia y migraciones):** añadir ORM (si se aprueba), adaptadores y
  repositorios, Flyway o Liquibase, y pruebas Testcontainers. El driver y el datasource ya
  están resueltos en 23.5; no hay que duplicarlos. Las migraciones deben apuntar al schema
  `clabriapp` y no tocar `public`, `heraap` ni las tablas de login.
- **23.10 (seguridad):** TLS, restricción del puerto `5432`, rotación del Secret, políticas
  de red y revisión de la `USAGE` heredada sobre `public`.
- **23.11 (observabilidad):** métricas del pool, alertas y redacción de logs sin
  credenciales. El health/readiness de PostgreSQL ya está incluido y validado en 23.5.
- **Coordinación:** actualizar el `status.md` central con el resumen de la sección 10 y
  corregir el criterio global “PostgreSQL operativo en el clúster” para reflejar la
  arquitectura aprobada.

## 10. Resumen para el status central

> **23.5 — PostgreSQL compartido (rama `feature/23-5-postgresql-shared`):** se reutiliza el
> contenedor PostgreSQL del host y la base `heraap`; no se despliega otro PostgreSQL ni se
> migran datos de login. Se creó el schema `clabriapp` y el rol exclusivo `clabriapp_app`
> con `CONNECT` en `heraap`, `USAGE`/`CREATE` propios solo en `clabriapp` y 0 privilegios
> sobre tablas/secuencias de `public` (la `USAGE` sobre `public` es la heredada de `PUBLIC` y
> no da acceso a datos). El Secret `clabriapp-postgresql` existe fuera de Git; el
> Service/Endpoint `clabriapp-postgresql` publica `172.28.0.1:5432`. Un pod efímero con el
> Secret ejecutó solo `SELECT current_database(), current_user;` y devolvió
> `heraap|clabriapp_app`; el pod se eliminó. La segunda fase añade a la aplicación Quarkus la
> extensión `quarkus-jdbc-postgresql` (BOM 3.40.2) y el datasource Agroal, configurado con
> `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME` y `DB_PASSWORD`, schema `clabriapp` y Dev
> Services desactivados. En `test` el datasource está inactivo; el smoke local con
> `DB_HOST=127.0.0.1` y credenciales del Secret en memoria devolvió `/health/ready` 200 con
> `Database connections health check` `UP`, sin consultar tablas ni escribir en la base. No
> hay ORM, repositorios ni migraciones (23.9); la app no está desplegada y los manifiestos e
> imagen de 23.3 están versionados, no aplicados. Límites: sin TLS, sin migración, `5432`
> expuesto en el host y `USAGE` heredada sobre `public` sin acceso a datos. La entrega de
> 23.5 queda en un único commit local con el mensaje aprobado; no se hizo push, PR, merge ni
> rebase.

## 11. Mensaje de commit de 23.5 (histórico)

El usuario aprobó este mensaje para el único commit local de la entrega 23.5, ya integrado
en `main` mediante el PR #3:

```text
feat(db): conecta ClabriApp al PostgreSQL compartido

Prepara el schema clabriapp y un rol dedicado en heraap, con Service y Endpoint de Kubernetes y Secret fuera de Git. Añade el driver JDBC y configura el datasource Quarkus con variables DB_* y health check. No migra datos de Heraap ni añade ORM o migraciones (23.9).
```

El commit local usa exactamente este título y este cuerpo. No se hizo push, PR, merge ni
rebase.
