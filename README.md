# msCalabriapp

Microservicio base de **ClabriApp**. Este repositorio contiene el bootstrap técnico de la
subtarea **23.1**: una aplicación Quarkus con arquitectura hexagonal, endpoint de salud y
pruebas base. Sobre `main` ya están integradas la política de ramas de **23.2** y la base
de datos compartida de **23.5**. La subtarea **23.3** añade la parte versionable de
CI/CD, imagen JVM, publicación en GHCR y despliegue en LocalEmu.

> **Estado:** el scaffold de **23.1**, la política de **23.2** (documentada en
> [`CONTRIBUTING.md`](CONTRIBUTING.md)) y la integración de **23.5** están en `main`. La
> aplicación incluye **driver JDBC PostgreSQL y datasource Agroal**, configurados por
> variables de entorno. No incluye ORM, repositorios ni migraciones: eso corresponde a
> **23.9**. La entrega de **23.3** deja **workflows, Dockerfile y manifiestos versionados**
> en el árbol de trabajo de la rama `feature/23-3-ci-cd-localemu`, pero en este encargo **no
> se ha registrado el runner, no se ha publicado ninguna imagen, no se ha desplegado nada,
> no se ha aplicado RBAC al clúster y no se ha configurado CI en GitHub**. Tampoco se ha
> cambiado la visibilidad del paquete GHCR y no se ha creado ningún commit.
>
> El detalle de las decisiones, las verificaciones y los pendientes está en
> [`docs/plan-trabajo.md`](docs/plan-trabajo.md).

## Propósito

Servir como punto de partida reproducible para el microservicio `msCalabriapp`, de marca
**ClabriApp**, manteniendo una separación estricta entre:

- **dominio**: modelo y reglas puras;
- **aplicación**: puertos de entrada/salida y casos de uso;
- **infraestructura**: adaptadores REST, persistencia en memoria y cableado.

El único endpoint funcional distinto de salud es una demostración mínima del cableado
(`/api/v1/service-info`). El endpoint de salud (`/health`) lo aporta Quarkus mediante
SmallRye Health.

## Requisitos

| Requisito | Detalle |
|---|---|
| JDK | **17 o superior** (probado con OpenJDK 17.0.21.1) |
| Maven | **No hace falta instalarlo**: se usa el Maven Wrapper versionado (`./mvnw`) |
| Red | Acceso a **Maven Central** en la primera ejecución para descargar Maven y dependencias |
| Utilidades | `bash`, `curl` o `wget`, y `unzip` para el wrapper |
| Docker | **Opcional** para desarrollo/pruebas; **necesario** solo para construir y probar la imagen JVM localmente |
| AWS / kubectl | **No requeridos** para desarrollo. El despliegue usa un runner dedicado con `kubectl` y kubeconfig preinstalados (ver más abajo) |

Versiones fijadas:

- Quarkus BOM: `3.40.2`
- `quarkus-jdbc-postgresql`: `3.40.2` (gestionada por el BOM)
- Driver PostgreSQL JDBC: `42.7.14` (transitivo, gestionado por el BOM)
- Java release: `17`
- Maven Wrapper: `3.3.4` (distribución `only-script`)
- Apache Maven: `3.9.16` (descargado por el wrapper con SHA-256 verificado)
- Maven Compiler Plugin: `3.16.0`
- Maven Surefire Plugin: `3.5.4`
- Maven Jar Plugin: `3.4.2`
- Maven Enforcer Plugin: `3.6.2`
- Maven Resources Plugin: `3.4.0`
- Maven Clean Plugin: `3.4.1`
- Maven Install / Deploy Plugin: `3.1.4`
- Imagen base JVM: `eclipse-temurin:17-jre-jammy`
- `actions/checkout`: `v4.4.0` fijada al SHA
  `11d5960a326750d5838078e36cf38b85af677262`
- `actions/setup-java`: `v4.9.1` fijada al SHA
  `cf277c60eb25467037889841efdb72551f06f6c3`

Se usa la última línea **3.x estable** de Quarkus (`3.40.2`) porque Quarkus 4 exige Java 21
y el entorno disponible tiene Java 17. La versión se verificó contra Maven Central antes de
fijarla.

## Arranque en desarrollo

Desde la raíz del repositorio:

```bash
./mvnw quarkus:dev
```

La aplicación queda disponible en:

- `http://localhost:8080/health`
- `http://localhost:8080/health/live`
- `http://localhost:8080/health/ready`
- `http://localhost:8080/api/v1/service-info`

Para cambiar el puerto:

```bash
./mvnw quarkus:dev -Dquarkus.http.port=9090
```

El datasource **no usa Dev Services**. Para que `/health/ready` esté `UP` en desarrollo hay
que definir `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME` y `DB_PASSWORD` apuntando a un
PostgreSQL accesible. Sin base, la aplicación arranca pero el readiness del datasource
aparecerá `DOWN`; no se levanta ningún contenedor automáticamente.

## Pruebas

```bash
./mvnw test
```

Las pruebas incluyen:

- pruebas unitarias puras de dominio y del caso de uso;
- pruebas `@QuarkusTest` del endpoint `/health` (global, liveness y readiness);
- prueba REST del endpoint `/api/v1/service-info` usando REST Assured.

El perfil `test` desactiva el datasource (`%test.quarkus.datasource.active=false`), así que
estas pruebas no requieren PostgreSQL ni Dev Services. El health check real del datasource
se valida aparte, con el proceso empaquetado y las variables de entorno del Secret.

Para empaquetar y ejecutar el artefacto:

```bash
./mvnw package
java -jar target/quarkus-app/quarkus-run.jar
```

## Imagen JVM (subtarea 23.3)

El [`Dockerfile`](Dockerfile) es una imagen **solo de runtime**. No compila dentro de la
imagen: espera que `target/quarkus-app/` se haya generado antes con Maven. Usa
`eclipse-temurin:17-jre-jammy`, escucha en `0.0.0.0:8080`, ejecuta como usuario no-root
(`10001:10001`) y no incluye Maven, fuentes ni el `.git`.

```bash
./mvnw -B -ntp verify
docker build --build-arg GIT_SHA="$(git rev-parse HEAD)" -t mscalabriapp:local .
./scripts/ci/smoke-image.sh mscalabriapp:local
```

El smoke arranca el contenedor con rootfs de solo lectura, `/tmp` en tmpfs, sin
capabilities y sin privilege escalation. Comprueba **solo** `/health/live` y
`/api/v1/service-info`. **No exige `/health/ready`**: el datasource está activo y, sin
PostgreSQL, readiness responde `DOWN`; eso es esperado y no es un fallo del smoke. El
workflow de CI ejecuta exactamente ese script.

`.dockerignore` excluye `.git`, cachés y todo el contenido que no sea el runtime
`target/quarkus-app/`. La etiqueta `org.opencontainers.image.revision` se rellena con
`GIT_SHA` cuando se pasa como `--build-arg`.

## CI/CD (subtarea 23.3)

Los workflows están versionados en el repositorio, pero **no se han ejecutado en GitHub ni
se han configurado como checks requeridos** en este encargo.

### Pull request hacia `main`

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) se dispara con `pull_request` y con
`push` a `main`. Todos sus jobs usan `ubuntu-latest` (**GitHub-hosted**). Hace:

1. `./mvnw -B -ntp verify`.
2. `docker build` de la imagen JVM.
3. Smoke sin PostgreSQL contra `/health/live` y `/api/v1/service-info`.

Este workflow **no contiene ningún job self-hosted**, no usa `pull_request_target`, no
recibe secretos y no despliega. Un PR de un fork se queda en build/test sobre runners
GitHub-hosted.

### Push/merge a `main`

[`.github/workflows/publish-deploy.yml`](.github/workflows/publish-deploy.yml) se dispara
**solo** con `push` a `main`; no tiene `workflow_dispatch`:

1. `publish` corre en `ubuntu-latest` (GitHub-hosted), repite `verify`, construye y prueba
   la imagen, y la publica en **GHCR** con `GITHUB_TOKEN` (`packages: write`) y la etiqueta
   inmutable `sha-${GITHUB_SHA}`. **No se usa ningún PAT.** Solo se publica esa etiqueta;
   no se publica `latest`.
2. `deploy` corre en un runner **self-hosted** con la etiqueta exacta
   `clabriapp-local-main`. Usa `kubectl` y el kubeconfig preinstalado en
   `/etc/mscalabriapp-runner/kubeconfig`. **No usa Docker ni el socket de Docker.**
3. Antes de tocar el clúster, `deploy` hace un **preflight anónimo** contra GHCR. Si el
   paquete es privado o la imagen no está disponible, el job termina con un mensaje claro
   y no aplica nada.

El job `deploy` tiene condiciones explícitas de repositorio
(`github.repository == 'ljuskas/msCalabriapp'`), evento (`github.event_name == 'push'`) y
ref (`github.ref == 'refs/heads/main'`). La concurrencia del workflow es
`cancel-in-progress: false`: un push nuevo espera y no cancela un despliegue en curso.

### Prerrequisito: runner dedicado

El runner **no está registrado** en este encargo. Para habilitarlo hace falta:

- registrarlo con la etiqueta exacta `clabriapp-local-main`;
- instalar `kubectl` y `curl`;
- provisionar fuera de Git el kubeconfig en `/etc/mscalabriapp-runner/kubeconfig`,
  legible por el usuario del runner y sin permisos de administrador;
- aplicar el bootstrap RBAC de [`k8s/bootstrap/`](k8s/bootstrap/) (no aplicado aquí);
- **no** instalar Docker ni montar el socket de Docker;
- permitir salida a `ghcr.io` para el preflight anónimo.

### Prerrequisito: visibilidad del paquete GHCR

La visibilidad de un paquete de GHCR es **independiente** de la del repositorio. La primera
publicación puede crear `mscalabriapp` como privado aunque el repositorio sea público. Antes
del primer despliegue hay que cambiarlo a público:

```text
GitHub > Packages > mscalabriapp > Package settings > Change visibility > Public
```

Hasta entonces, el preflight anónimo falla y el despliegue no aplica nada. El primer run de
`publish-deploy` puede terminar en ese punto; hay que cambiar la visibilidad y **volver a
ejecutar el workflow** desde Actions. No se ha cambiado esa visibilidad en este encargo.

### Permisos y secretos

- Workflows con `permissions: contents: read`; solo `publish` añade `packages: write`.
  La política de Actions del repositorio debe permitir solicitar ese permiso; no se cambió
  en este encargo.
- La publicación usa el `GITHUB_TOKEN` automático, nunca un PAT.
- No hay secretos de Kubernetes ni valores del Secret en el repositorio.
- El Deployment mapea las cinco claves del Secret existente con `secretKeyRef`; no crea
  Secrets ni usa `envFrom`.
- El bootstrap RBAC del runner no concede `get` sobre Secrets. Además, el job comprueba con
  `kubectl auth can-i get secrets` que el kubeconfig usado no pueda leerlos y falla si
  puede.
- Las acciones de terceros están fijadas a SHA oficiales comprobados:
  `actions/checkout` v4.4.0 y `actions/setup-java` v4.9.1.

### Riesgos y pendientes

- El primer despliegue puede fallar en el preflight si el paquete GHCR sigue privado; hay
  que cambiar la visibilidad y reintentar.
- El runner no está registrado y el kubeconfig no está instalado: el job `deploy` no puede
  ejecutarse todavía.
- Un runner self-hosted no debe quedar expuesto a PR de forks. GitHub debe exigir
  aprobación para workflows de forks y limitar quién puede modificar workflows.
- La etiqueta `sha` es inmutable por convención, no por firma ni por digest. No hay SBOM ni
  firma de imagen en este alcance.
- `/health/ready` depende del PostgreSQL compartido y del gateway k3d; sin base, el pod no
  recibe tráfico aunque `/health/live` esté `UP`.
- No hay `workflow_dispatch`: los reintentos se hacen re-ejecutando el run desde Actions.
- La imagen base se referencia por tag, no por digest.

Los manifiestos de la aplicación están en [`k8s/app/`](k8s/app/) y no se han aplicado al
clúster. El antiguo texto de `proyHerapp_v2/status.md` que proponía previews o despliegue
desde PR queda **reemplazado**: PR solo ejecuta CI en GitHub-hosted; el despliegue ocurre
solo después del push/merge a `main`. No se editó el otro repositorio.

## PostgreSQL compartido (subtarea 23.5)

Por decisión de arquitectura aprobada, **no se despliega un PostgreSQL dentro del clúster**
ni se migran los datos de login. Se reutiliza el contenedor PostgreSQL del host que ya usa
Heraap y la base existente `heraap`. El aislamiento se hace con:

- schema `clabriapp`, propiedad del rol `clabriapp_app`;
- rol `clabriapp_app` exclusivo, sin privilegios de datos sobre `public` ni sobre las
  tablas de login/sesión;
- Secret `clabriapp-postgresql` en el namespace `clabriapp`, creado fuera de Git.

La ruta de conexión es:

```text
pod -> Service clabriapp-postgresql:5432 -> Endpoints 172.28.0.1:5432
    -> gateway de la red Docker k3d -> PostgreSQL del host -> base heraap
```

Los manifiestos versionables están en
[`k8s/postgresql-shared/`](k8s/postgresql-shared/): Service, Endpoints, kustomization y un
pod efímero de verificación. **No hay ningún Secret versionado.** La IP `172.28.0.1` es el
gateway de la red `k3d-localemu-eks-clabriapp`; si se recrea esa red, hay que revisar el
gateway y actualizar `endpoints.yaml` (ver el README del directorio).

Verificación de aceptación ejecutada: un pod efímero que referencia el Secret ejecutó
únicamente `SELECT current_database(), current_user;` y devolvió `heraap|clabriapp_app`.
Después se eliminó el pod. No se consultaron filas ni tablas de negocio.

### Integración de la aplicación (23.5)

La aplicación incluye la extensión `quarkus-jdbc-postgresql`, cuya versión gestiona el BOM
`3.40.2`. La extensión aporta el driver PostgreSQL y el pool Agroal. **No** se añadieron
Hibernate ORM, repositorios, Flyway ni migraciones; esos elementos siguen en la subtarea
23.9. Tampoco se creó ningún endpoint que lea datos: el único check nuevo es el de
datasource que SmallRye Health/Agroal publica en `/health/ready`.

Configuración en `src/main/resources/application.properties`:

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

- El schema por defecto es `clabriapp` (`currentSchema`); el rol `clabriapp_app` ya tiene
  `search_path=clabriapp, pg_catalog`. La aplicación no crea ni modifica roles ni schemas.
- Dev Services están desactivados de forma global y para el datasource en todos los
  perfiles: la aplicación nunca levanta un PostgreSQL adicional.
- En el perfil `test` el datasource queda inactivo, no intenta conectarse y no contribuye
  al health check. Por eso `./mvnw test` no necesita base de datos ni contenedores.
- En el perfil real el datasource está activo y `/health/ready` incluye
  `Database connections health check`, que solo valida la conexión.

#### Variables de entorno y Secret

Las claves del Secret `clabriapp-postgresql` **no** tienen el prefijo `DB_`. El Deployment
versionado en [`k8s/app/deployment.yaml`](k8s/app/deployment.yaml) las mapea explícitamente;
`envFrom` no renombra claves. El mapeo es:

| Clave del Secret | Variable de entorno | Uso en Quarkus |
| --- | --- | --- |
| `host` | `DB_HOST` | host del JDBC URL |
| `port` | `DB_PORT` | puerto del JDBC URL |
| `database` | `DB_NAME` | base del JDBC URL |
| `username` | `DB_USERNAME` | `quarkus.datasource.username` |
| `password` | `DB_PASSWORD` | `quarkus.datasource.password` |

Ejemplo de mapeo explícito (idéntico al manifiesto versionado; **no aplicado** al clúster):

```yaml
env:
  - name: DB_HOST
    valueFrom:
      secretKeyRef:
        name: clabriapp-postgresql
        key: host
  - name: DB_PORT
    valueFrom:
      secretKeyRef:
        name: clabriapp-postgresql
        key: port
  - name: DB_NAME
    valueFrom:
      secretKeyRef:
        name: clabriapp-postgresql
        key: database
  - name: DB_USERNAME
    valueFrom:
      secretKeyRef:
        name: clabriapp-postgresql
        key: username
  - name: DB_PASSWORD
    valueFrom:
      secretKeyRef:
        name: clabriapp-postgresql
        key: password
```

En el clúster, el valor de `DB_HOST` debe ser el del Secret (`host`), que apunta al Service
`clabriapp-postgresql`. Para el smoke test local se usa `DB_HOST=127.0.0.1`, porque el
puerto `5432` del PostgreSQL del host está publicado en loopback/interfaces locales.

Límites: no hay migración de datos, el login de Heraap sigue intacto, no hay TLS y el
contenedor PostgreSQL existente publica `5432` en todas las interfaces del host (situación
previa que no se modificó). La aplicación ya tiene driver y datasource, pero no ORM,
adaptadores ni migraciones (23.9). **La aplicación no está desplegada**: los manifiestos,
la imagen y los workflows están versionados, pero no se ha registrado el runner ni aplicado
nada al clúster; el clúster usado en las pruebas de 23.5 es LocalEmu/k3s, no EKS real.

## Verificación ejecutada

Fecha de verificación: **2026-11-25**, con OpenJDK 17.0.21.1 y Apache Maven 3.9.16
(gestionado por `./mvnw`).

- `./mvnw test`: **BUILD SUCCESS**; 9 pruebas ejecutadas, 0 fallos, 0 errores, 0 omitidas.
- `./mvnw clean verify`: **BUILD SUCCESS**; compilación limpia, 9 pruebas y empaquetado
  Quarkus correctos (scaffold 23.1, antes del datasource).
- Smoke test del jar del scaffold 23.1: `GET /health` devolvió `200 OK` con
  `{"status":"UP","checks":[]}`; `GET /api/v1/service-info` devolvió `200 OK` con los
  datos de `msCalabriapp`/`ClabriApp`.

Verificación de la subtarea 23.5 (2026-11-25):

- La base `heraap` ya existía; no existían el schema `clabriapp`, el rol
  `clabriapp_app` ni el Secret. No hubo conflicto ni se sobrescribió nada.
- Se crearon el schema `clabriapp` en `heraap` y el rol `clabriapp_app` con contraseña
  aleatoria generada en memoria, sin mostrarla ni escribirla en disco. El Secret
  `clabriapp-postgresql` se creó por `stdin` y no está en Git.
- Metadatos: Service `clabriapp-postgresql` (`ClusterIP`, puerto `5432`, sin selector) y
  Endpoints `172.28.0.1:5432`; Secret `Opaque` con 5 claves (`database`, `host`,
  `password`, `port`, `username`), sin leer sus valores.
- Prueba desde pod efímero con la referencia al Secret: `heraap|clabriapp_app`; el pod
  quedó `Completed` y se eliminó. No se consultó ninguna tabla ni fila de negocio.
- Privilegios: `CONNECT` en `heraap` y `USAGE`/`CREATE` propios solo en `clabriapp`; la
  `USAGE` heredada de `PUBLIC` sobre `public` no otorga acceso a tablas; 0 privilegios sobre
  las tablas y secuencias de `public`; 0 pertenencias a otros roles; 0 objetos propios fuera
  de `clabriapp`.
- Persistencia: el contenedor PostgreSQL usa el bind mount existente
  `/DATA/AppData/postgresql/data`. No se reinició el contenedor compartido porque el
  alcance prohíbe tocar los contenedores/volúmenes existentes.
- `./mvnw test` re-ejecutado tras 23.5: **BUILD SUCCESS**, 9 pruebas, 0 fallos.

Verificación de la integración de datasource (segunda fase de 23.5, 2026-11-25):

- `./mvnw test`: **BUILD SUCCESS**; 9 pruebas, 0 fallos. El perfil `test` mantiene el
  datasource inactivo: no se conectó a PostgreSQL ni se creó ningún contenedor.
- `./mvnw package -DskipTests`: **BUILD SUCCESS**; se generó
  `target/quarkus-app/quarkus-run.jar` con `quarkus-jdbc-postgresql` 3.40.2,
  `quarkus-agroal` 3.40.2 y `org.postgresql:postgresql` 42.7.14.
- Smoke test local del jar, con `DB_HOST=127.0.0.1`, `DB_PORT=5432`, `DB_NAME=heraap` y
  usuario/contraseña leídos del Secret `clabriapp-postgresql` en memoria (sin imprimirlos ni
  escribirlos a disco): `GET /health/ready` devolvió `200` con `Database connections health
  check` en `UP` y `<default>` en `UP`.
- El health check solo abrió una conexión de validación. No se consultaron tablas ni filas,
  no se escribió en la base y no se ejecutó SQL de negocio. No se creó ningún pod,
  contenedor ni Dev Service: los mismos 28 contenedores siguieron presentes.
- El proceso Quarkus se detuvo al terminar y el puerto `18080` quedó libre. No se
  modificaron roles, schema, Secret, Service, Endpoints ni contenedores.

Verificación de la parte versionable de 23.3 (2026-11-25, sin tocar GitHub ni el clúster):

- `./mvnw -B -ntp verify`: **BUILD SUCCESS**; 9 pruebas, 0 fallos, 0 errores, 0 omitidas.
- `docker build --build-arg GIT_SHA="$(git rev-parse HEAD)" -t mscalabriapp:local .`:
  **correcto**. La imagen quedó con `User=10001:10001`, puerto `8080` y entrypoint JVM.
- `./scripts/ci/smoke-image.sh mscalabriapp:local`: **correcto**; `/health/live` `UP` y
  `/api/v1/service-info` con `msCalabriapp`/`ClabriApp`, sin PostgreSQL y sin exigir
  `/health/ready`.
- `/health/started` se comprobó con el artefacto empaquetado: SmallRye Health lo expone y
  respondió `200`.
- Parseo de los YAML de workflows y manifiestos con PyYAML: **correcto**. Comprobación
  estructural: CI solo en `ubuntu-latest`; deploy solo con `clabriapp-local-main` y
  condiciones de repositorio, evento `push` y `refs/heads/main`.
- `git status --short --branch`: rama `feature/23-3-ci-cd-localemu`, solo los archivos
  esperados de 23.3. `git diff --check`: sin errores.
- Escaneo de patrones de secretos: solo nombres de claves, variables o placeholders; ningún
  valor real. No se imprimieron coincidencias sensibles.
- No se modificó ningún archivo fuera de `msCalabriapp` por este encargo.
- `actionlint`, `kubeconform` y `kubectl` **no están disponibles** en el worker; no se
  instalaron herramientas de sistema. Por eso no se ejecutó validación con esas herramientas
  ni se aplicó nada al clúster.
- No se registró el runner, no se publicó ninguna imagen, no se cambió la visibilidad de
  GHCR, no se aplicó RBAC y no se ejecutaron los workflows en GitHub.

## Endpoints

### `GET /health`

Responde `200 OK` con el estado agregado de SmallRye Health. Con el datasource activo y la
base accesible, `/health/ready` incluye el check de Agroal:

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

También están disponibles `/health/live` y `/health/ready`. En el perfil `test` el
datasource está inactivo y el check no se registra, por lo que el estado sigue siendo `UP`
sin base de datos.

### `GET /api/v1/service-info`

Demuestra el recorrido completo `REST -> puerto de entrada -> caso de uso -> puerto de salida
-> adaptador en memoria`:

```json
{
  "name": "msCalabriapp",
  "displayName": "ClabriApp",
  "version": "0.1.0-SNAPSHOT"
}
```

Ejemplos con `curl`:

```bash
curl -i http://localhost:8080/health
curl -s http://localhost:8080/api/v1/service-info
```

## Estructura

```text
.
├── .dockerignore                  # Contexto mínimo para la imagen JVM
├── .github/
│   ├── pull_request_template.md
│   └── workflows/
│       ├── ci.yml                 # PR y push: build, pruebas e imagen (GitHub-hosted)
│       └── publish-deploy.yml     # Push a main: GHCR + runner self-hosted
├── .mvn/wrapper/                  # Configuración del Maven Wrapper
├── Dockerfile                     # Runtime JVM Java 17 no-root
├── docs/plan-trabajo.md           # Decisiones y estado de 23.3 y base 23.5
├── k8s/
│   ├── app/                       # Deployment, Service y kustomization
│   ├── bootstrap/                 # RBAC del runner (no aplicado)
│   └── postgresql-shared/         # Service, Endpoints y smoke pod (sin Secret)
├── mvnw / mvnw.cmd                # Wrapper (no requiere Maven global)
├── pom.xml
├── scripts/ci/
│   ├── preflight-ghcr.sh          # Pull anónimo antes de tocar el clúster
│   └── smoke-image.sh             # Smoke sin PostgreSQL
├── src/main/java/com/clabriapp/mscalabriapp/
│   ├── domain/model/              # Modelo puro (sin frameworks)
│   ├── application/
│   │   ├── ports/in/              # Puertos de entrada (casos de uso)
│   │   ├── ports/out/             # Puertos de salida
│   │   └── usecases/              # Implementación de casos de uso
│   └── infrastructure/
│       └── adapters/
│           ├── rest/              # Adaptador REST de entrada
│           ├── persistence/       # Adaptador de salida en memoria
│           └── config/            # Composition root (beans CDI)
├── src/main/resources/application.properties
└── src/test/java/                 # Pruebas unitarias y REST
```

### Regla de dependencias

```text
infrastructure  --->  application  --->  domain
```

- `domain` solo usa la biblioteca estándar de Java.
- `application` depende de `domain` y de sus propios puertos; **no** importa Quarkus,
  Jakarta EE ni clases de `infrastructure`.
- `infrastructure` implementa los puertos y construye los casos de uso.
- `ApplicationBeans` es el único punto de composición; por eso el caso de uso no lleva
  anotaciones CDI.

La aplicación todavía no tiene persistencia de negocio: `InMemoryServiceInfoAdapter` lee la
información de `application.properties`. La base compartida, el rol y el datasource JDBC de
23.5 ya están provisionados y cableados, pero el ORM, los adaptadores/repositorios y las
migraciones se implementan en la subtarea 23.9. Se puede sustituir el adaptador sin cambiar
dominio ni aplicación.

## Build reproducible

- Todas las versiones de Quarkus, plugins y wrapper están fijadas.
- Se verifica el SHA-256 de la distribución de Maven descargada por el wrapper.
- `project.build.outputTimestamp` está fijado para que los artefactos generados por Maven
  no dependan de la fecha de compilación.
- No se usan dependencias `SNAPSHOT` externas; solo la versión del propio proyecto es
  `SNAPSHOT`.
- El build requiere JDK 17+ y Maven 3.9+, validado con `maven-enforcer-plugin`.

La reproducibilidad byte a byte del runner de Quarkus no se garantiza; las versiones y el
timestamp sí quedan fijados para el build Maven.

## Entorno de verificación y límites

Verificado en el entorno de la subtarea:

- `java -version`: OpenJDK **17.0.21.1** disponible.
- `mvn -version`: **no disponible** (comando no encontrado); por eso se usa el wrapper.
- `./mvnw -v`: funciona y descarga Apache Maven **3.9.16** con SHA-256 verificado.
- Red: `repo.maven.apache.org` accesible; la primera ejecución descargó Maven y las
  dependencias correctamente.
- El `HOME` del entorno es de solo lectura (`/root`). Para que `./mvnw test` funcione sin
  variables extra, el script `mvnw` usa `.mvn/maven-home` como caché local cuando detecta
  que `HOME` no es escribible. En un entorno normal sigue usando `~/.m2`.
- La primera ejecución necesita red hacia `repo.maven.apache.org`; después el caché local
  permite repetir el build.
- La base PostgreSQL compartida de 23.5 está provisionada y verificada; la aplicación ya
  incluye driver JDBC y datasource, pero no ORM, adaptadores/repositorios ni migraciones
  (23.9). No se incluyen mensajería, autenticación ni AWS real. La imagen JVM, los workflows
  y los manifiestos de 23.3 están versionados, pero no se publicaron ni se aplicaron al
  clúster.
- Docker **sí está disponible** en el worker (probado con Docker 29.10.1): se construyó y
  ejecutó la imagen local. Para compilar fuera de este entorno puede hacer falta apuntar
  `DOCKER_CONFIG` a un directorio escribible si `HOME` es de solo lectura.
- `kubectl`, `actionlint` y `kubeconform` **no están instalados** en el host. Para 23.5 se
  usó el binario `kubectl` incluido en el contenedor del servidor k3d; el clúster es
  LocalEmu/k3s, no EKS real. En 23.3 no se ejecutó ningún comando contra el clúster.
- La IP del Endpoints depende del gateway de la red k3d. Si se recrea esa red, revisar y
  actualizar `k8s/postgresql-shared/endpoints.yaml`. No se reinició el contenedor
  PostgreSQL compartido; la persistencia se comprobó por el bind mount existente.
- El contenedor PostgreSQL publica `5432` en todas las interfaces del host. Es una
  condición previa que no se modificó en 23.5 y debe restringirse en una tarea de
  seguridad.
- No se ha probado compilación nativa (GraalVM/Mandrel); el alcance es JVM.

## Licencia

**Pendiente de decisión.** No se ha incluido un archivo `LICENSE` porque no hay una
licencia aprobada para el producto. No se debe asumir ninguna licencia implícita. Antes de
publicar o distribuir el código hay que decidir y aprobar la licencia, añadir el archivo
correspondiente y actualizar esta sección.

Los scripts de Maven Wrapper incluidos conservan su cabecera **Apache License 2.0**; esa
licencia aplica al wrapper, no al código del producto.

## Contribución

El flujo de trabajo completo está en [`CONTRIBUTING.md`](CONTRIBUTING.md). La rama `main`
tiene protección activa en GitHub con este estado:

- **PR obligatorio** hacia `main`; no se permite push directo.
- **0 aprobaciones requeridas** (*required approving reviews* = 0), por decisión del único
  mantenedor. GitHub no bloquea el merge por falta de aprobación, pero la **revisión manual
  del diff y de las pruebas sigue siendo obligatoria** y debe quedar documentada en el PR.
- **Conversaciones resueltas** antes del merge e **historial lineal**.
- **Force push y borrado de `main` bloqueados**, también para administradores.
- **Sin checks de estado requeridos todavía.** Los workflows de 23.3 están versionados en
  el repositorio, pero no se han ejecutado ni configurado como checks requeridos en GitHub
  en este encargo.
- El repositorio solo permite **Squash and merge**; merge commits y rebase están
  deshabilitados como métodos de integración.

La plantilla de PR está en [`.github/pull_request_template.md`](.github/pull_request_template.md).
El flujo de CI/CD versionado se describe arriba y en [`CONTRIBUTING.md`](CONTRIBUTING.md);
su activación en GitHub (runner, paquete público y checks requeridos) queda pendiente y no
se debe dar por hecha.
