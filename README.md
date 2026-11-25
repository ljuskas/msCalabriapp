# msCalabriapp

Microservicio base de **ClabriApp**. Este repositorio contiene el bootstrap técnico de la
subtarea **23.1**: una aplicación Quarkus con arquitectura hexagonal, endpoint de salud y
pruebas base.

> **Estado:** el scaffold técnico de la subtarea **23.1** está integrado en `main` mediante
> PR. La política de ramas y pull requests de la subtarea **23.2** está documentada en
> [`CONTRIBUTING.md`](CONTRIBUTING.md), y `main` tiene protección activa en GitHub. Es un
> esqueleto técnico, sin lógica de negocio de las tareas 23.6/23.7, sin base de datos, sin
> autenticación, sin Docker, sin AWS y sin CI/CD.

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

## Pruebas

```bash
./mvnw test
```

Las pruebas incluyen:

- pruebas unitarias puras de dominio y del caso de uso;
- pruebas `@QuarkusTest` del endpoint `/health` (global, liveness y readiness);
- prueba REST del endpoint `/api/v1/service-info` usando REST Assured.

Para empaquetar y ejecutar el artefacto:

```bash
./mvnw package
java -jar target/quarkus-app/quarkus-run.jar
```

## Verificación ejecutada

Fecha de verificación: **2026-11-25**, con OpenJDK 17.0.21.1 y Apache Maven 3.9.16
(gestionado por `./mvnw`).

- `./mvnw test`: **BUILD SUCCESS**; 9 pruebas ejecutadas, 0 fallos, 0 errores, 0 omitidas.
- `./mvnw clean verify`: **BUILD SUCCESS**; compilación limpia, 9 pruebas y empaquetado
  Quarkus correctos.
- Smoke test del jar empaquetado: `GET /health` devolvió `200 OK` con
  `{"status":"UP","checks":[]}`; `GET /api/v1/service-info` devolvió `200 OK` con los
  datos de `msCalabriapp`/`ClabriApp`.

## Endpoints

### `GET /health`

Responde `200 OK` con el estado agregado de SmallRye Health:

```json
{"status":"UP","checks":[]}
```

También están disponibles `/health/live` y `/health/ready`.

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

No hay persistencia real todavía: `InMemoryServiceInfoAdapter` lee la información de
`application.properties`. Se puede sustituir por otro adaptador sin cambiar dominio ni
aplicación.

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
- No se incluyen base de datos, migraciones, mensajería, seguridad, Docker, Kubernetes,
  AWS ni pipelines de CI/CD.
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
