# ============================================================
#  Backend RankPadel — build reproducible multi-stage
# ============================================================

# --- Stage 1: build con Maven + JDK 21 ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Cache de dependencias: primero el pom, luego el código
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests clean package

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
