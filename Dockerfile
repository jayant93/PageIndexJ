# ── Stage 1: Build ──────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Cache dependencies first (layer cache friendly)
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Build the JAR
COPY src ./src
RUN mvn package -DskipTests -q

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Non-root user for security
RUN addgroup -S pageindex && adduser -S pageindex -G pageindex

COPY --from=build /app/target/pageindexj-1.0.0.jar app.jar

# Results dir — ephemeral on Cloud Run (acceptable for demo)
RUN mkdir -p results && chown -R pageindex:pageindex /app

USER pageindex

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
