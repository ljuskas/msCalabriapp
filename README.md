# msCalabriapp

Microservicio base de **ClabriApp**. Este repositorio contiene el bootstrap técnico de la
subtarea **23.1**: una aplicación Quarkus con arquitectura hexagonal, endpoint de salud y
pruebas base.

> **Estado:** el scaffold técnico de la subtarea **23.1** está integrado en `main` mediante
> PR. La política de ramas y pull requests de la subtarea **23.2** está documentada en
> [`CONTRIBUTING.md`](CONTRIBUTING.md), y `main` tiene protección activa en GitHub. La
> subtarea **23.5** deja preparado el acceso al PostgreSQL compartido del host mediante un
> schema y un rol aislados, un Service/Endpoint de Kubernetes y un Secret fuera de Git.
> Además, la aplicación Quarkus ya incluye el **driver JDBC PostgreSQL y el datasource
> Agroal**, configurados por variables de entorno y con el health check de readiness. No
> incluye ORM, repositorios ni migraciones: eso corresponde a la subtarea 23.9. Sigue
> siendo un esqueleto técnico, sin lógica de negocio de las tareas 23.6/23.7, sin
> autenticación, sin imagen Docker de la aplicación, sin AWS y sin CI/CD.
>
> El detalle de la decisión, las verificaciones y los pendientes está en
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
| Docker / AWS | **No requeridos** |

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

Las claves del Secret `clabriapp-postgresql` **no** tienen el prefijo `DB_`. Hay que
mapearlas explícitamente en el Deployment; `envFrom` no renombra claves. El mapeo previsto
para cuando 23.3 despliegue el contenedor es:

| Clave del Secret | Variable de entorno | Uso en Quarkus |
| --- | --- | --- |
| `host` | `DB_HOST` | host del JDBC URL |
| `port` | `DB_PORT` | puerto del JDBC URL |
| `database` | `DB_NAME` | base del JDBC URL |
| `username` | `DB_USERNAME` | `quarkus.datasource.username` |
| `password` | `DB_PASSWORD` | `quarkus.datasource.password` |

Ejemplo de mapeo explícito (no se aplica todavía; el Deployment es de 23.3):

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
adaptadores ni migraciones (23.9). **La aplicación no está desplegada en EKS**: la imagen,
el contenedor y el CI corresponden a la subtarea 23.3; el clúster usado en estas pruebas es
LocalEmu/k3s, no EKS real.

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
├── .mvn/wrapper/                  # Configuración del Maven Wrapper
├── docs/plan-trabajo.md           # Decisiones y estado de la subtarea 23.5
├── k8s/postgresql-shared/         # Service, Endpoints y smoke pod (sin Secret)
├── mvnw / mvnw.cmd                # Wrapper (no requiere Maven global)
├── pom.xml
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
  (23.9). No se incluyen mensajería, autenticación, imagen Docker de la aplicación, AWS real
  ni pipelines de CI/CD (23.3).
- `kubectl` no está instalado en el host. Para 23.5 se usó el binario incluido en el
  contenedor del servidor k3d (`docker exec ... kubectl`); el clúster es LocalEmu/k3s, no
  EKS real.
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
- **Sin checks de estado requeridos** hasta la tarea CI 23.3.
- El repositorio solo permite **Squash and merge**; merge commits y rebase están
  deshabilitados como métodos de integración.

La plantilla de PR está en [`.github/pull_request_template.md`](.github/pull_request_template.md).
Las verificaciones automatizadas obligatorias se definirán en la tarea 23.3, cuando exista
GitHub Actions; este repositorio todavía no incluye workflows de CI.
