<!--
Título del PR: usá el formato semántico en español, por ejemplo:
  ci(deploy): añade CI en GitHub-hosted y despliegue seguro en LocalEmu
El título se usará como mensaje del commit de squash.
Rama base: main. Rama de origen: feature/<id-tarea>-<descripcion>.
Completá todas las secciones con resultados reales. Los workflows de 23.3 están
versionados, pero no se ejecutan ni se configuran como checks requeridos por el
solo hecho de abrir este PR. No inventes verificaciones ni marques casillas que no
se hayan ejecutado.
-->

## Resumen

<!-- Explicá en 2-4 líneas qué hace este PR y qué problema resuelve. -->

## Motivación

<!-- ¿Por qué es necesario el cambio? Referenciá la tarea o el issue si existe. -->

## Cambios

<!-- Listá los cambios principales, agrupados por archivo o área. -->
-
-

## Pruebas

<!-- Indicá comandos ejecutados y resultados reales. No marques pruebas que no ejecutaste. -->

- Comandos ejecutados:
  ```bash
  ./mvnw -B -ntp verify
  docker build --build-arg GIT_SHA="$(git rev-parse HEAD)" -t mscalabriapp:local .
  ./scripts/ci/smoke-image.sh mscalabriapp:local
  git diff --check
  ```
- Resultado:
  <!-- BUILD SUCCESS / fallos / pruebas omitidas y por qué -->
- Smoke de imagen:
  <!-- /health/live y /api/v1/service-info. No exigir /health/ready sin PostgreSQL. -->
- Verificaciones automatizadas: indicá si el CI de GitHub-hosted llegó a ejecutarse en este
  PR. Si los workflows todavía no están activos en GitHub, decilo explícitamente; no
  afirmes que un check pasó si no se ejecutó.

## CI/CD y despliegue

<!-- Completá solo lo que aplique. Un PR normal no despliega. -->

- [ ] El CI de este PR, si se ejecuta, usa solo runners GitHub-hosted.
- [ ] Este PR no usa `pull_request_target`, no recibe secretos y no despliega al clúster.
- [ ] Si el cambio toca workflows, revisé que el job `deploy` siga teniendo las condiciones
      de repositorio, evento `push` y ref `refs/heads/main`.
- [ ] Si el cambio toca la imagen o los manifiestos, ejecuté el build y el smoke local.
- [ ] Si aplica al primer despliegue: confirmé que el paquete GHCR es público (la
      visibilidad es independiente de la del repositorio). Si no es público, el preflight
      falla y no aplica nada; hay que cambiarlo y reintentar.
- [ ] No se registró el runner, no se publicó la imagen ni se aplicó nada al clúster como
      parte de este PR, salvo que el alcance lo indique y esté aprobado.

## Riesgos

<!-- Impacto esperado, compatibilidad, deuda pendiente, rollback si aplica. -->
-
-

## Checklist de seguridad (sin secretos)

- [ ] El diff no incluye contraseñas, tokens, claves privadas, certificados, cadenas de
      conexión con credenciales, kubeconfigs ni otros secretos.
- [ ] No se añadieron archivos `.env`, keystores, dumps, backups ni configuración local
      sensible.
- [ ] Las pruebas, ejemplos, documentación y logs no exponen credenciales ni datos
      personales; los datos de ejemplo están anonimizados.
- [ ] No se añadieron dependencias nuevas sin justificarlas, o se revisó que no tengan
      vulnerabilidades conocidas.
- [ ] Los mensajes de error y la configuración no filtran información sensible ni
      habilitan opciones inseguras por defecto.
- [ ] La publicación en GHCR usa `GITHUB_TOKEN` con `packages: write`, no un PAT.
- [ ] La imagen se publica con la etiqueta inmutable `sha-${GITHUB_SHA}`, no con `latest`.
- [ ] El Deployment referencia las cinco claves del Secret con `secretKeyRef`; no se
      versionan valores ni se crea un Secret adicional.
- [ ] El RBAC del runner no concede `get` sobre Secrets.
- [ ] Las acciones de terceros están fijadas a SHA oficiales comprobados.
- [ ] Revisé el diff completo y no detecté secretos ni cambios accidentales.

<!-- Si algún punto no aplica, indicalo explícitamente. -->
