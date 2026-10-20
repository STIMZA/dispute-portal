# syntax=docker/dockerfile:1

# ---- Build stage: compile, run the tests, package the jar ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Resolve dependencies first so this layer is cached until pom.xml changes.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp dependency:go-offline

COPY src ./src
# Tests gate the image by default. Use --build-arg SKIP_TESTS=true for a quicker local build.
ARG SKIP_TESTS=false
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp package -DskipTests=${SKIP_TESTS} \
    && cp target/dispute-portal-*.jar /workspace/app.jar

# ---- Runtime stage: JRE only, non-root user ----
FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/app.jar app.jar
USER app

# "prod" expects DB_URL, DB_USERNAME and DB_PASSWORD. For a throw-away run with in-memory H2 and
# demo data, start the container with -e SPRING_PROFILES_ACTIVE=dev.
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=45s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health/readiness > /dev/null || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
