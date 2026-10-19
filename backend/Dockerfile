FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY config ./config
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-noble AS runtime
WORKDIR /app
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=6 \
    CMD curl --fail --silent http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

# CI packages exactly the JAR already built and tested by the backend job.
FROM runtime AS ci
COPY target/sems-backend-*.jar app.jar

# Default target supports the existing local docker compose --build workflow.
FROM runtime AS local
COPY --from=build /workspace/target/sems-backend-*.jar app.jar
