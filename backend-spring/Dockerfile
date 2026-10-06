# syntax=docker/dockerfile:1.7
# =============================================================================
# Unisen SGP - Backend Spring Boot
#   docker build -t unisen/sgp-backend ./backend-spring
# =============================================================================

# ---------- Etapa 1: compilación con Maven + JDK 21 ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Las dependencias se descargan en una capa propia: solo se repite si cambia pom.xml.
# La caché de ~/.m2 (BuildKit) acelera además las reconstrucciones.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline

COPY src ./src
# Los tests se ejecutan en CI (./mvnw test); aquí solo se empaqueta.
# Se renombra a application.jar: la extracción conserva el nombre del JAR y el ENTRYPOINT lo usa.
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests \
 && cp target/sgp-backend-*.jar application.jar \
 && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# ---------- Etapa 2: ejecución con JRE 21 mínimo ----------
FROM eclipse-temurin:21-jre-alpine

# Usuario sin privilegios.
RUN addgroup -S spring && adduser -S spring -G spring
WORKDIR /app

# Capas ordenadas de menos a más cambiante: un cambio de código solo invalida la última.
COPY --from=build --chown=spring:spring /workspace/extracted/dependencies/ ./
COPY --from=build --chown=spring:spring /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=spring:spring /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=spring:spring /workspace/extracted/application/ ./

USER spring

ENV SERVER_PORT=8081 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8081

# Readiness de Actuator: "healthy" solo cuando la app acepta tráfico (BD incluida).
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=5 \
  CMD wget -q -O /dev/null "http://127.0.0.1:${SERVER_PORT}/actuator/health/readiness" || exit 1

ENTRYPOINT ["java", "-jar", "application.jar"]
