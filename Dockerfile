# syntax=docker/dockerfile:1.6

# ==================== Stage 1: Build ====================
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /src
COPY pom.xml .
COPY common/pom.xml common/
COPY common/core/pom.xml common/core/
COPY common/data/pom.xml common/data/
COPY common/web/pom.xml common/web/
COPY common/web/securityCore/pom.xml common/web/securityCore/
COPY common/security-api/pom.xml common/security-api/
COPY common/monitoring/pom.xml common/monitoring/
COPY common/integration/pom.xml common/integration/
COPY system/pom.xml system/
COPY system/api/pom.xml system/api/
COPY system/service/pom.xml system/service/
COPY auth/pom.xml auth/
COPY gateway/pom.xml gateway/
RUN mvn dependency:go-offline -B || true
COPY . .
RUN mvn clean package -DskipTests -B

# ==================== Stage 2: Runtime ====================
FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app
COPY --from=builder /src/gateway/target/*.jar app.jar
RUN chown -R appuser:appgroup /app
USER appuser
EXPOSE 8761
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
  CMD wget -qO- http://localhost:8761/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
