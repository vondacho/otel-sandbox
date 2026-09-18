# Build context: repository root (see docker-compose.yml)
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml .
COPY order-service/pom.xml order-service/
COPY e2e-tests/pom.xml e2e-tests/
COPY order-service/src order-service/src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -pl order-service -am package -DskipTests

FROM eclipse-temurin:25-jre
RUN groupadd --system app && useradd --system --gid app app
WORKDIR /app
COPY --from=build /workspace/order-service/target/otel/opentelemetry-javaagent.jar opentelemetry-javaagent.jar
COPY --from=build /workspace/order-service/target/order-service.jar app.jar
USER app
# The agent installs the OpenTelemetry SDK; it is configured through OTEL_* environment variables.
ENV JAVA_TOOL_OPTIONS="-javaagent:/app/opentelemetry-javaagent.jar"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
