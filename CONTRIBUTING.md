# Contribución a msCalabriapp

Este documento define el flujo de trabajo acordado para el repositorio. Su objetivo es
mantener un historial lineal en `main`, revisar los cambios antes de integrarlos y evitar
que se cuelen secretos o cambios fuera de alcance.

## Estado activo de la protección en GitHub

La rama `main` tiene **protección activa** en GitHub. La configuración vigente es:

| Ajuste | Estado |
|---|---|
| Pull request obligatorio | **Activado**. No se permite push directo a `main`. |
| Aprobaciones requeridas | **0** (*required approving reviews* = 0), por decisión del único mantenedor. GitHub no bloquea el merge por falta de aprobación. |
| Revisión manual | **Obligatoria y documentada en el PR**, aunque GitHub no exija una aprobación formal. |
| Checks de estado requeridos | **Pendiente de configurar en GitHub.** Los workflows de 23.3 están versionados, pero no se han ejecutado ni declarado como checks requeridos en este encargo. |
| Conversaciones resueltas | **Obligatorio** resolverlas antes del merge. |
| Historial lineal | **Obligatorio**. |
| Force push | **Bloqueado**. |
| Borrado de `main` | **Bloqueado**. |
| Administradores | **Incluidos**: la protección se aplica también a administradores. |

En el repositorio, el único método de merge habilitado es **Squash and merge**; los merge
commits y el rebase están deshabilitados como métodos de integración.

Este documento describe la configuración activa; no la modifica.

## Ramas

- `main` es la rama estable y está protegida en GitHub. Solo recibe cambios mediante PR
  fusionados con *squash*.
- Cada tarea o cambio coherente se desarrolla en una rama `feature/*` creada desde `main`
  actualizado.
- Formato recomendado: `feature/<id-tarea>-<descripcion-corta>`.
  - Ejemplo: `feature/23-2-branch-policy`.
  - Usar minúsculas, números y guiones; sin espacios ni caracteres especiales.
- Una rama `feature/*` se puede borrar después de que su PR se haya fusionado, tanto en
  local como en el remoto.
- No se reutiliza una rama ya fusionada para otro cambio: se crea una rama nueva desde
  `main`.
- **`main` no se borra nunca**, ni en local ni en el remoto.

## Commits semánticos en español

Los mensajes siguen el formato de *Conventional Commits* en español:

```text
tipo(alcance opcional): descripción breve en imperativo
```

Reglas:

- La descripción va en español, en imperativo, en minúscula inicial y sin punto final.
- La primera línea no debería superar los 72 caracteres.
- El cuerpo, si hace falta, explica **qué** cambia y **por qué**, no solo el cómo.
- Un commit debe agrupar un cambio lógico y dejar el repositorio en un estado razonable.
- No se incluyen secretos, credenciales, tokens ni datos personales en el mensaje ni en el
  contenido del commit.

Tipos habituales:

| Tipo | Uso |
|---|---|
| `feat` | Nueva funcionalidad |
| `fix` | Corrección de un error |
| `docs` | Documentación |
| `test` | Pruebas |
| `refactor` | Reestructuración sin cambiar comportamiento |
| `perf` | Mejora de rendimiento |
| `build` | Build, dependencias o wrapper |
| `ci` | Configuración de integración continua |
| `chore` | Mantenimiento que no encaja en los anteriores |

Ejemplos:

```text
feat(api): agrega endpoint de información del servicio
fix: corrige el estado de salud cuando falta un adaptador
docs: documenta el flujo de ramas y pull requests
test: cubre la resolución de comentarios del flujo de PR
```

El título del PR se convierte en el mensaje del commit de *squash*, así que también debe
seguir este formato.

## Flujo `feature/*` → PR → `main`

1. **Actualizar `main`** antes de empezar:

   ```bash
   git switch main
   git pull --ff-only origin main
   ```

2. **Crear la rama de trabajo** desde `main`:

   ```bash
   git switch -c feature/23-2-branch-policy
   ```

3. **Implementar el cambio** y ejecutar las pruebas que correspondan.

4. **Crear commits semánticos** en español, pequeños y coherentes:

   ```bash
   git add CONTRIBUTING.md .github/pull_request_template.md README.md
   git commit -m "docs: documenta el flujo de ramas y pull requests"
   ```

5. **Publicar la rama feature**:

   ```bash
   git push -u origin feature/23-2-branch-policy
   ```

6. **Abrir un PR obligatorio dirigido a `main`**. La protección de `main` exige el PR. El
   PR debe describir el cambio con la plantilla del repositorio. No se abre un PR de una
   rama `feature/*` hacia otra rama `feature/*` para integrar trabajo en la rama estable.

7. **Revisar manualmente el diff completo y las pruebas** (ver secciones siguientes) y
   dejar constancia de esa revisión en el PR.

8. **Resolver todas las conversaciones** de revisión antes de fusionar; la protección de
   `main` lo exige.

9. **Fusionar con *squash***, único método de merge habilitado, cuando la revisión y las
   pruebas estén conformes.

10. **Borrar la rama feature** una vez fusionada y sincronizar `main`:

    ```bash
    git switch main
    git pull --ff-only origin main
    git branch -d feature/23-2-branch-policy
    git push origin --delete feature/23-2-branch-policy
    git fetch --prune
    ```

Si el PR ya borró la rama remota al fusionarse, el `git push origin --delete` no es
necesario. El borrado de la rama feature es una limpieza posterior al merge; no afecta a
`main`.

### Conflictos y actualización de la rama

Si `main` avanzó mientras la rama feature estaba abierta, se actualiza la rama feature sin
reescribir historia publicada:

```bash
git fetch origin
git switch feature/23-2-branch-policy
git merge origin/main
# resolver conflictos, probar y commitear si corresponde
git push
```

El historial de `main` seguirá siendo lineal porque la integración final se hace con
*squash*. No se fuerza el push para ocultar conflictos ni para reescribir commits ya
publicados.

## Revisión manual obligatoria

La protección de `main` exige un PR, pero **no exige aprobaciones** (*required approving
reviews* = 0), por lo que GitHub no bloqueará el merge por falta de aprobación. Esa
circunstancia no elimina la revisión manual: sigue siendo obligatoria, debe quedar
registrada en el PR y es la que sostiene la calidad del cambio.

Antes de fusionar, el autor y, cuando exista, el revisor deben comprobar:

- El diff completo, no solo los archivos principales.
- Que el cambio se limita al alcance de la tarea y no incluye modificaciones accidentales.
- Que se respeta la regla de dependencias del proyecto:
  `infrastructure → application → domain`.
- Que las pruebas cubren el comportamiento nuevo o corregido y que pasan.
- Que la documentación afectada está actualizada.
- Que no hay secretos, credenciales, tokens, claves, datos personales ni archivos de
  entorno versionados.
- Que los mensajes de error y los logs no exponen información sensible.

Pruebas de referencia del proyecto:

```bash
./mvnw -B -ntp verify
docker build --build-arg GIT_SHA="$(git rev-parse HEAD)" -t mscalabriapp:local .
./scripts/ci/smoke-image.sh mscalabriapp:local
git diff --check      # espacios en blanco y conflictos sin resolver
```

El smoke de la imagen comprueba solo `/health/live` y `/api/v1/service-info`; no exige
`/health/ready`, porque sin PostgreSQL el datasource está `DOWN`. Los comandos y resultados
reales deben quedar registrados en el PR. No se debe afirmar que una prueba pasó si no se
ejecutó.

## Pull request

- Es **obligatorio** para integrar cualquier cambio en `main`; la protección de `main` lo
  exige y no se permite push directo a `main`.
- La rama base es siempre `main`.
- La protección exige **0 aprobaciones** (*required approving reviews* = 0) por decisión del
  único mantenedor. No existe un requisito de aprobación formal en GitHub; la revisión
  manual documentada en el PR sigue siendo obligatoria.
- El mantenedor no aprueba formalmente su propio PR y no lo necesita para fusionar; eso no
  sustituye la revisión manual ni las pruebas.
- El título sigue el formato semántico en español.
- La descripción usa la plantilla de
  [`.github/pull_request_template.md`](.github/pull_request_template.md) e incluye resumen,
  motivación, cambios, pruebas, riesgos y checklist de seguridad.
- Un PR debe poder revisarse de forma independiente y tener un alcance claro. Los cambios
  grandes se dividen en PR más pequeños cuando sea posible.
- Un PR en borrador puede abrirse para revisión temprana, pero no se fusiona hasta que esté
  listo y revisado.

## Comentarios de revisión

- Cada comentario se responde o se corrige antes de marcarlo como resuelto.
- Un comentario bloqueante no se marca como resuelto sin una corrección o una explicación
  aceptada por quien lo planteó.
- Los comentarios no bloqueantes pueden resolverse indicando el motivo, pero deben quedar
  registrados.
- No se fusiona un PR con conversaciones sin resolver. La protección de `main` exige que
  todas las conversaciones estén resueltas antes del merge.

## Merge: solo *squash*

El repositorio tiene habilitado **Squash and merge** y tiene deshabilitados **merge commit**
y **rebase and merge**. La protección de `main` exige además **historial lineal**.

- Un PR produce **un único commit** en `main`.
- El título del PR se usa como mensaje del commit final y debe ser semántico en español.
- La descripción del PR puede conservarse como cuerpo del commit si aporta contexto.
- No se crean commits de merge en `main`.
- No se usa *rebase and merge* ni *merge commit* para integrar.

Después del merge se borra la rama feature. `main` no se borra y no se reescribe.

## Prohibiciones explícitas

- **Push directo a `main`:** bloqueado por la protección. Todo cambio entra por PR.
- **Force-push:** bloqueado por la protección, también para administradores. No se reescribe
  historia ya publicada y nunca se aplica sobre `main`. Si una rama feature necesita una
  corrección, se añade un commit y se vuelve a publicar.
- **Borrado de `main`:** bloqueado por la protección, tanto en local como en el remoto.
- **Merge commit y rebase:** deshabilitados como métodos de merge; la integración en `main`
  es solo con *squash*.

Estas prohibiciones están activas en GitHub y se aplican también a los administradores. La
revisión manual y las pruebas siguen siendo obligatorias aunque GitHub no exija
aprobaciones.

## Procedimiento práctico con un solo mantenedor

Mientras el repositorio tenga un único mantenedor, el PR sigue siendo obligatorio y se usa
como registro de la revisión y de las pruebas. El procedimiento es:

1. Crear la rama `feature/*` desde `main` actualizado.
2. Implementar y commitear con mensajes semánticos en español.
3. Publicar la rama y abrir el PR hacia `main`.
4. Revisar el propio diff completo en la pestaña **Files changed** de GitHub.
5. Ejecutar las pruebas y anotar en el PR los comandos y sus resultados reales.
6. Dejar constancia en el PR de la revisión manual realizada. Si hay dudas, abrir
   comentarios propios y resolverlos antes de fusionar.
7. No fusionar si hay pruebas fallidas, conversaciones sin resolver o secretos en el diff.
8. Fusionar con **Squash and merge** y borrar la rama feature.
9. Sincronizar `main` en local con `git pull --ff-only origin main`.

Con un solo mantenedor no es posible aprobar formalmente el propio PR en GitHub, y la
protección está configurada con **0 aprobaciones requeridas** por esa razón. GitHub no
bloqueará el merge por falta de aprobación; la revisión manual documentada en el PR sigue
siendo obligatoria. No se declara un requisito de aprobaciones mayor que cero. Si en el
futuro se incorpora un segundo revisor, se le podrá solicitar revisión voluntaria; cambiar
el número exigido sería una modificación de la configuración de GitHub, ajena a este
documento.

## CI/CD y despliegue (23.3)

Los workflows están **versionados en el repositorio**, pero en este encargo no se han
ejecutado en GitHub, no se han configurado como checks requeridos, el runner no está
registrado y el paquete GHCR no se ha publicado. No se deben inventar nombres de checks ni
marcar casillas de CI como superadas.

### Pull request hacia `main`

- Workflow: [`.github/workflows/ci.yml`](.github/workflows/ci.yml).
- Se dispara con `pull_request` hacia `main` y con `push` a `main`.
- Todos los jobs usan `ubuntu-latest` (**GitHub-hosted**). No hay ningún job self-hosted,
  no se usa `pull_request_target` y no se reciben secretos.
- Pasos: `./mvnw -B -ntp verify`, construcción de la imagen JVM y smoke sin PostgreSQL
  contra `/health/live` y `/api/v1/service-info`.
- Un PR de fork se queda en build/test sobre runner GitHub-hosted. **Un PR no despliega y
  no accede al clúster.**

### Push/merge a `main`

- Workflow: [`.github/workflows/publish-deploy.yml`](.github/workflows/publish-deploy.yml).
- Solo se dispara con `push` a `main`; no tiene `workflow_dispatch`.
- `publish` corre en GitHub-hosted, verifica, construye, prueba y publica en GHCR con el
  `GITHUB_TOKEN` automático y la etiqueta inmutable `sha-${GITHUB_SHA}`. **No se usa ningún
  PAT.**
- `deploy` corre en el runner self-hosted con la etiqueta exacta `clabriapp-local-main`,
  usa `kubectl` y el kubeconfig preinstalado en
  `/etc/mscalabriapp-runner/kubeconfig`, y **no usa Docker ni el socket de Docker**.
- El job `deploy` exige explícitamente repositorio `ljuskas/msCalabriapp`, evento `push` y
  ref `refs/heads/main`.
- Antes de tocar el clúster se hace un **preflight anónimo de GHCR**. Si el paquete es
  privado o la imagen no está, el job falla con un mensaje que indica cambiar la
  visibilidad y reintentar, sin aplicar nada.

### Prerrequisitos operativos

- Runner registrado con la etiqueta exacta `clabriapp-local-main`, `kubectl` y `curl`.
- Kubeconfig fuera de Git en `/etc/mscalabriapp-runner/kubeconfig`, legible por el runner.
- Bootstrap RBAC de [`k8s/bootstrap/`](k8s/bootstrap/) aplicado por el operador. Su Role no
  concede `get` sobre Secrets; el job de despliegue comprueba con
  `kubectl auth can-i get secrets -n clabriapp` que el kubeconfig tampoco pueda leerlos.
- Paquete GHCR público. La visibilidad del paquete es independiente de la del repositorio;
  la primera publicación puede crearlo privado. Cambiarlo en
  `GitHub > Packages > mscalabriapp > Package settings > Change visibility > Public` y
  volver a ejecutar el workflow.
- Sin Docker ni socket de Docker en el runner de despliegue.

### Permisos y acciones

- Workflows con `permissions: contents: read`; solo el job de publicación añade
  `packages: write`. La política de Actions del repositorio debe permitir ese permiso.
- No se usan PAT ni secretos de GitHub para publicar.
- No se crean Secrets de Kubernetes ni se versionan valores del Secret
  `clabriapp-postgresql`.
- Las acciones de terceros están fijadas a SHA oficiales comprobados:
  `actions/checkout` v4.4.0 y `actions/setup-java` v4.9.1.

### Reemplazo del texto anterior de `proyHerapp_v2/status.md`

El texto antiguo que proponía previews por PR y despliegue desde el PR queda **reemplazado**:
PR solo ejecuta CI en GitHub-hosted; el despliegue ocurre solo después del push/merge a
`main`. No hay previews por PR ni namespaces efímeros. No se editó el otro repositorio.

## Seguridad y secretos

- No se versionan contraseñas, tokens, claves privadas, certificados, cadenas de conexión
  con credenciales ni archivos `.env`.
- No se versionan kubeconfigs, tokens de runner ni valores del Secret
  `clabriapp-postgresql`. La publicación en GHCR usa solo el `GITHUB_TOKEN` automático, no
  un PAT.
- La configuración sensible se pasa por variables de entorno o mecanismos locales no
  versionados.
- No se incluyen secretos en el código, las pruebas, la documentación, los mensajes de
  commit, la descripción del PR ni los logs.
- No se ejecutan workflows de PR en runners self-hosted ni se exponen secretos a forks.
- Si se detecta un secreto expuesto, no basta con borrar el archivo: hay que revocar o
  rotar la credencial, avisar al mantenedor y eliminar el secreto del historial de forma
  coordinada.
- El checklist de seguridad de la plantilla de PR debe completarse antes de fusionar. Si un
  punto no aplica, se indica explícitamente.

## Referencias

- [README.md](README.md): propósito, arranque, pruebas, imagen y CI/CD.
- [.github/pull_request_template.md](.github/pull_request_template.md): plantilla de PR.
- [.github/workflows/ci.yml](.github/workflows/ci.yml): CI de PR y push.
- [.github/workflows/publish-deploy.yml](.github/workflows/publish-deploy.yml): publicación
  y despliegue desde `main`.
- [k8s/app/](k8s/app/): Deployment y Service.
- [k8s/bootstrap/](k8s/bootstrap/): RBAC del runner, no aplicado.
