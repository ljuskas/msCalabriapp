<!--
Título del PR: usá el formato semántico en español, por ejemplo:
  docs: documenta el flujo de ramas y pull requests
El título se usará como mensaje del commit de squash.
Rama base: main. Rama de origen: feature/<id-tarea>-<descripcion>.
Completá todas las secciones. No inventes verificaciones automáticas: en esta etapa
no hay workflows de CI; se configurarán en la tarea 23.3.
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
  # pegar comandos
  ```
- Resultado:
  <!-- BUILD SUCCESS / fallos / pruebas omitidas y por qué -->
- Verificaciones automatizadas: **no hay checks de CI configurados todavía**. Se
  configurarán en la tarea 23.3 cuando exista GitHub Actions. No afirmar que un check pasó
  si no existe o no se ejecutó.

## Riesgos

<!-- Impacto esperado, compatibilidad, deuda pendiente, rollback si aplica. -->
-
-

## Checklist de seguridad (sin secretos)

- [ ] El diff no incluye contraseñas, tokens, claves privadas, certificados, cadenas de
      conexión con credenciales ni otros secretos.
- [ ] No se añadieron archivos `.env`, keystores, dumps, backups ni configuración local
      sensible.
- [ ] Las pruebas, ejemplos, documentación y logs no exponen credenciales ni datos
      personales; los datos de ejemplo están anonimizados.
- [ ] No se añadieron dependencias nuevas sin justificarlas, o se revisó que no tengan
      vulnerabilidades conocidas.
- [ ] Los mensajes de error y la configuración no filtran información sensible ni
      habilitan opciones inseguras por defecto.
- [ ] Revisé el diff completo y no detecté secretos ni cambios accidentales.

<!-- Si algún punto no aplica, indicalo explícitamente. -->
