# Build stage: compiles the boot jar with the project's own Gradle wrapper, so the image does not depend on a jar
# built outside Docker.
FROM eclipse-temurin:21.0.13.1_2-jdk-alpine AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,id=triage-service-gradle,target=/root/.gradle sh ./gradlew :app-service:bootJar --no-daemon

# Runtime stage: JRE only, non-root user.
FROM eclipse-temurin:21.0.13.1_2-jre-alpine
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/applications/app-service/build/libs/triage-service.jar app.jar
USER app
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=5 \
    CMD wget -qO- "http://localhost:${SERVER_PORT}/health/readiness" || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
