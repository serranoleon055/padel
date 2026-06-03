# ============================================================
#  Backend RankPadel — build reproducible multi-stage
# ============================================================

# --- Stage 1: build con Maven + JDK 21 ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Limitar memoria de Maven para no reventar el builder de Railway
ENV MAVEN_OPTS="-Xmx1024m -XX:MaxMetaspaceSize=256m"
# Cache de dependencias: primero el pom, luego el código.
# go-offline es "best-effort": si algún artefacto no resuelve en esta fase NO
# se rompe el build (|| true); el `package` de abajo descarga lo que falte.
COPY pom.xml .
RUN mvn -q -B dependency:go-offline || true
COPY src ./src
# Saltar la compilación de tests (mas rapido y menos superficie de fallo)
RUN mvn -q -B -Dmaven.test.skip=true clean package

# --- Stage 2: runtime liviano con JRE 21 ---
FROM eclipse-temurin:21-jre
WORKDIR /app
# Usuario no-root por seguridad
RUN useradd -r -u 1001 appuser
COPY --from=build /app/target/*.jar app.jar
# Carpeta de uploads (montar un volumen persistente en /data/uploads)
RUN mkdir -p /data/uploads && chown -R appuser:appuser /data
USER appuser
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
