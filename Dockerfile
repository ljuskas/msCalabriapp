# Imagen JVM de msCalabriapp.
#
# Es una imagen de runtime: el artefacto Quarkus (target/quarkus-app) se genera
# antes con ./mvnw -B -ntp verify y se copia tal cual. No compila dentro de la
# imagen y no incluye Maven ni el código fuente.
FROM eclipse-temurin:17-jre-jammy

ARG UID=10001
ARG GID=10001
ARG GIT_SHA=unknown

LABEL org.opencontainers.image.title="msCalabriapp" \
      org.opencontainers.image.description="Microservicio base de ClabriApp" \
      org.opencontainers.image.source="https://github.com/ljuskas/msCalabriapp" \
      org.opencontainers.image.version="0.1.0-SNAPSHOT" \
      org.opencontainers.image.base.name="eclipse-temurin:17-jre-jammy" \
      org.opencontainers.image.revision="${GIT_SHA}"

RUN groupadd --gid "${GID}" app \
    && useradd --uid "${UID}" --gid app --no-create-home \
       --home-dir /app --shell /usr/sbin/nologin app \
    && mkdir -p /app \
    && chown app:app /app \
    && chmod 1777 /tmp

WORKDIR /app

COPY --chown=app:app target/quarkus-app/lib/ /app/lib/
COPY --chown=app:app target/quarkus-app/app/ /app/app/
COPY --chown=app:app target/quarkus-app/quarkus/ /app/quarkus/
COPY --chown=app:app target/quarkus-app/quarkus-run.jar /app/quarkus-run.jar

USER 10001:10001

EXPOSE 8080

# Quarkus escucha en 0.0.0.0 y en el puerto 8080. El logging manager es el
# requerido por Quarkus para JBoss LogManager.
ENTRYPOINT ["java", \
  "-Dquarkus.http.host=0.0.0.0", \
  "-Dquarkus.http.port=8080", \
  "-Djava.util.logging.manager=org.jboss.logmanager.LogManager", \
  "-XX:MaxRAMPercentage=60.0", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-jar", "/app/quarkus-run.jar"]
