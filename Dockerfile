# ─── Stage 1: Build ──────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy dependency descriptors first so Docker caches the layer
COPY pom.xml ./
RUN mvn -q dependency:go-offline -B

# Copy source and build (skip tests; run them in CI)
COPY src ./src
RUN mvn -q package -DskipTests -B

# ─── Stage 2: Runtime ────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre

WORKDIR /app

# Create a non-root user for security
RUN groupadd --gid 1001 parknova && \
    useradd --uid 1001 --gid parknova --shell /bin/sh --no-create-home parknova

COPY --from=builder /app/target/*.jar app.jar

RUN chown parknova:parknova app.jar

USER parknova

# Port declared in application.yml (APPLICATION_PORT=8080)
EXPOSE 8080

# Use exec form so signals reach the JVM (graceful shutdown)
ENTRYPOINT ["java", "-jar", "app.jar"]
